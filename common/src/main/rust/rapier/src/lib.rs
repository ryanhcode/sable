#![allow(static_mut_refs)]

pub mod algo;
pub mod boxes;
mod buoyancy;
mod collider;
mod config;
mod contraptions;
mod dispatcher;
mod event_handler;
mod groups;
mod hooks;
mod joints;
pub mod rope;
mod scene;
mod voxel_collider;

use jni::objects::{JClass, JDoubleArray, JIntArray};
use jni::sys::{jboolean, jdouble, jint};
use jni::{JNIEnv, JavaVM};
use rapier3d::glamx::Quat;
use rapier3d::math::Vector;
use std::collections::HashMap;

use fern::colors::{Color, ColoredLevelConfig};
use log::info;

use crate::buoyancy::compute_buoyancy;
use crate::collider::LevelCollider;
use crate::dispatcher::SableDispatcher;
use crate::event_handler::SableEventHandler;
use crate::groups::LEVEL_GROUP;
use crate::joints::SableJointSet;
use crate::rope::RopeMap;
use crate::scene::{ChunkAccess, ChunkMap, SableManifoldInfoMap, pack_section_pos};
use crate::voxel_collider::VoxelColliderMap;
use hooks::SablePhysicsHooks;
use marten::Real;
use marten::level::VoxelPhysicsState::Interior;
use marten::level::{
    ALL_VOXEL_PHYSICS_STATES, BlockState, CHUNK_SHIFT, ChunkSection, OCTREE_CHUNK_SHIFT,
    OCTREE_CHUNK_SIZE, OctreeChunkSection, VoxelPhysicsState,
};
use marten::octree::SubLevelOctree;
use rapier3d::na::{Matrix3, Vector3 as NaVector3};
use rapier3d::parry::query::{DefaultQueryDispatcher, QueryDispatcher};
use rapier3d::prelude::*;
use scene::{LevelColliderID, PhysicsScene};

#[derive(Debug)]
pub struct ActiveLevelColliderInfo {
    pub collider: ColliderHandle,
    pub static_mount: Option<RigidBodyHandle>,
    pub fake_velocities: Option<RigidBodyVelocity<Real>>,
    pub local_bounds_min: Option<NaVector3<i32>>,
    pub local_bounds_max: Option<NaVector3<i32>>,
    pub center_of_mass: Option<NaVector3<f64>>,
    pub octree: Option<SubLevelOctree>,
    pub chunk_map: Option<ChunkMap>,
    pub scene_id: jint,
}

impl ChunkAccess for ActiveLevelColliderInfo {
    fn get_chunk_mut(&mut self, x: i32, y: i32, z: i32) -> Option<&mut ChunkSection> {
        self.chunk_map
            .as_mut()
            .unwrap()
            .get_mut(&pack_section_pos(x, y, z))
    }

    fn get_chunk(&self, x: i32, y: i32, z: i32) -> Option<&ChunkSection> {
        self.chunk_map
            .as_ref()
            .unwrap()
            .get(&pack_section_pos(x, y, z))
    }
}

pub fn get_scene<'a>(scene_id: jint) -> &'a PhysicsScene {
    let physics_state = unsafe { &mut PHYSICS_STATE };
    let scene = if physics_state.is_none() {
        None
    } else {
        physics_state.as_ref().unwrap().scenes.get(&scene_id)
    };

    scene.unwrap()
}

pub fn get_scene_mut<'a>(scene_id: jint) -> &'a mut PhysicsScene {
    let physics_state = unsafe { &mut PHYSICS_STATE };
    let scene = if physics_state.is_none() {
        None
    } else {
        physics_state.as_mut().unwrap().scenes.get_mut(&scene_id)
    };

    scene.unwrap()
}

impl ActiveLevelColliderInfo {
    /// Creates a new handle for a sable object with rigidbody and collider handles
    #[must_use]
    pub fn new(collider: ColliderHandle, scene_id: i32) -> Self {
        Self {
            collider,
            static_mount: None,
            fake_velocities: None,
            chunk_map: None,
            local_bounds_min: None,
            local_bounds_max: None,
            center_of_mass: None,
            octree: None,
            scene_id,
        }
    }

    pub fn has_own_chunks(&self) -> bool {
        self.chunk_map.is_some()
    }

    /// Sets the local bounds for the object
    pub fn set_local_bounds(&mut self, min: NaVector3<i32>, max: NaVector3<i32>, scene_id: jint) {
        if Some(min) != self.local_bounds_min || Some(max) != self.local_bounds_max {
            self.local_bounds_min = Some(min);
            self.local_bounds_max = Some(max);

            let max_axis = (max - min).max() as u32 + 1;
            let smallest_pow_2_above = max_axis.next_power_of_two();

            let chunk_min = NaVector3::new(
                min.x >> CHUNK_SHIFT,
                min.y >> CHUNK_SHIFT,
                min.z >> CHUNK_SHIFT,
            );
            let chunk_max = NaVector3::new(
                max.x >> CHUNK_SHIFT,
                max.y >> CHUNK_SHIFT,
                max.z >> CHUNK_SHIFT,
            );

            self.octree = Some(SubLevelOctree::new(
                smallest_pow_2_above.trailing_zeros() as i32
            ));

            let Some(physics_state) = (unsafe { &PHYSICS_STATE }) else {
                panic!("No physics state!");
            };
            let Some(scene) = physics_state.scenes.get(&scene_id) else {
                panic!("No scene with given ID!");
            };

            let has_own_chunks = self.has_own_chunks();

            for cx in chunk_min.x..=chunk_max.x {
                for cy in chunk_min.y..=chunk_max.y {
                    for cz in chunk_min.z..=chunk_max.z {
                        let chunk = if has_own_chunks {
                            self.chunk_map
                                .as_ref()
                                .unwrap()
                                .get(&pack_section_pos(cx, cy, cz))
                        } else {
                            scene.get_chunk(cx, cy, cz)
                        };

                        if let Some(chunk_section) = chunk {
                            for x in 0..16 {
                                for y in 0..16 {
                                    for z in 0..16 {
                                        let block_owned = chunk_section.get_block(x, y, z);
                                        if block_owned.1 == VoxelPhysicsState::Empty {
                                            continue;
                                        }

                                        insert_block_octree(
                                            self.octree.as_mut().unwrap(),
                                            &block_owned,
                                            false,
                                            (x + (cx << CHUNK_SHIFT)) - min.x,
                                            (y + (cy << CHUNK_SHIFT)) - min.y,
                                            (z + (cz << CHUNK_SHIFT)) - min.z,
                                        );
                                    }
                                }
                            }
                        }
                        // let chunk = scene.main_level_chunks.get(&pack_section_pos(cx, cy, cz));

                        // if let Some(chunk) = chunk {
                        //     self.insert_chunk(chunk, cx, cy, cz);
                        // }
                    }
                }
            }
        }
        self.local_bounds_min = Some(min);
        self.local_bounds_max = Some(max);
    }

    fn insert_chunk(&mut self, chunk_section: &ChunkSection, cx: i32, cy: i32, cz: i32) {
        for x in 0..16 {
            for y in 0..16 {
                for z in 0..16 {
                    self.insert_block(
                        x + (cx << CHUNK_SHIFT),
                        y + (cy << CHUNK_SHIFT),
                        z + (cz << CHUNK_SHIFT),
                        &chunk_section.get_block(x, y, z),
                        false,
                    );
                }
            }
        }
    }

    fn insert_block(&mut self, x: i32, y: i32, z: i32, state: &BlockState, remove: bool) {
        let local_min = self.local_bounds_min.unwrap();
        let x = x - local_min.x;
        let y = y - local_min.y;
        let z = z - local_min.z;

        let Some(octree) = &mut self.octree else {
            panic!("No octree!");
        };
        insert_block_octree(octree, state, remove, x, y, z);
    }

    fn contains(&self, x: i32, y: i32, z: i32) -> bool {
        if self.local_bounds_min.is_none() || self.local_bounds_max.is_none() {
            return false;
        }

        let local_min = self.local_bounds_min.unwrap();
        let local_max = self.local_bounds_max.unwrap();

        x >= local_min.x
            && x <= local_max.x
            && y >= local_min.y
            && y <= local_max.y
            && z >= local_min.z
            && z <= local_max.z
    }
}

/// The current physics engine state, holding all scenes
pub struct PhysicsState {
    /// The integration parameters, updated every time-step
    integration_parameters: IntegrationParameters,

    /// An array of i32 IDs -> block collider entries
    voxel_collider_map: VoxelColliderMap,

    /// A map of dimension ID -> scene
    scenes: HashMap<i32, PhysicsScene>,
}

/// A collision to report to the Java side.
#[derive(Debug, Clone)]
pub struct ReportedCollision {
    body_a: Option<LevelColliderID>,
    body_b: Option<LevelColliderID>,
    local_point_a: NaVector3<f64>,
    local_point_b: NaVector3<f64>,
    local_normal_a: NaVector3<f64>,
    local_normal_b: NaVector3<f64>,
    force_amount: f64,
}

/// The current physics engine state, set during initialization.
pub static mut PHYSICS_STATE: Option<PhysicsState> = None;
//TODO: safer static state

#[inline(always)]
pub unsafe fn get_physics_state_mut() -> &'static mut PhysicsState {
    unsafe { PHYSICS_STATE.as_mut().expect("No physics state!") }
}

#[inline(always)]
pub unsafe fn get_physics_state() -> &'static PhysicsState {
    unsafe { PHYSICS_STATE.as_ref().expect("No physics state!") }
}

#[inline(always)]
pub fn get_scene_mut_ref(scene_id: jint) -> &'static mut PhysicsScene {
    unsafe {
        get_physics_state_mut()
            .scenes
            .get_mut(&scene_id)
            .expect("No scene with given ID!")
    }
}

#[inline(always)]
pub fn get_scene_ref(scene_id: jint) -> &'static PhysicsScene {
    unsafe {
        get_physics_state()
            .scenes
            .get(&scene_id)
            .expect("No scene with given ID!")
    }
}

#[inline(always)]
pub fn get_rigid_body_mut(scene: &mut PhysicsScene, id: LevelColliderID) -> &mut RigidBody {
    let handle = scene.rigid_bodies.get(&id).expect("No rigid body for id");
    &mut scene.rigid_body_set[*handle]
}

#[inline(always)]
pub fn get_rigid_body(scene: &PhysicsScene, id: LevelColliderID) -> &RigidBody {
    let handle = scene.rigid_bodies.get(&id).expect("No rigid body for id");
    &scene.rigid_body_set[*handle]
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_initialize<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    x: jdouble,
    y: jdouble,
    z: jdouble,
    universal_drag: jdouble,
) {
    if unsafe { &PHYSICS_STATE }.is_none() {
        let colors = ColoredLevelConfig::new()
            .info(Color::Green)
            .error(Color::Red)
            .debug(Color::Blue);

        let _ = fern::Dispatch::new()
            .format(move |out, message, record| {
                out.finish(format_args!(
                    "[{}] [{}] ({}) {}",
                    humantime::format_rfc3339(std::time::SystemTime::now()),
                    colors.color(record.level()),
                    record.target(),
                    message
                ))
            })
            .level(log::LevelFilter::Info)
            .level_for("jni", log::LevelFilter::Error)
            .chain(std::io::stdout())
            .apply();

        unsafe {
            PHYSICS_STATE = Some(PhysicsState {
                integration_parameters: IntegrationParameters {
                    dt: 1.0 / 20.0,

                    max_ccd_substeps: 3,
                    normalized_prediction_distance: 0.005,

                    contact_softness: SpringCoefficients {
                        natural_frequency: 30.0,
                        damping_ratio: 5.0,
                    },
                    // joint_softness: SpringCoefficients {
                    //     natural_frequency: 1.0e2,
                    //     damping_ratio: 1.0,
                    // },
                    normalized_max_corrective_velocity: 50.0,
                    normalized_allowed_linear_error: 0.0025,

                    ..IntegrationParameters::default()
                },
                voxel_collider_map: VoxelColliderMap::new(),
                scenes: HashMap::new(),
            });
        }
    }

    unsafe {
        let ground = RigidBodyBuilder::fixed();

        if let Some(state) = &mut PHYSICS_STATE {
            let collider =
                ColliderBuilder::new(SharedShape::new(LevelCollider::new(None, true, scene_id)))
                    .collision_groups(LEVEL_GROUP)
                    .build();

            let mut scene = PhysicsScene {
                scene_id,
                pipeline: PhysicsPipeline::new(),
                rigid_body_set: RigidBodySet::new(),
                collider_set: ColliderSet::new(),
                island_manager: IslandManager::new(),
                broad_phase: DefaultBroadPhase::new(),
                narrow_phase: NarrowPhase::with_query_dispatcher(
                    SableDispatcher.chain(DefaultQueryDispatcher),
                ),
                impulse_joint_set: ImpulseJointSet::new(),
                multibody_joint_set: MultibodyJointSet::new(),
                ccd_solver: CCDSolver::new(),
                physics_hooks: SablePhysicsHooks,
                event_handler: SableEventHandler { scene_id },
                main_level_chunks: HashMap::<i64, ChunkSection>::new(),
                octree_chunks: HashMap::<i64, OctreeChunkSection>::new(),
                reported_collisions: Vec::with_capacity(16),
                joint_set: SableJointSet::new(),
                ground_handle: None,
                rope_map: RopeMap::default(),
                level_colliders: HashMap::<LevelColliderID, ActiveLevelColliderInfo>::new(),
                rigid_bodies: HashMap::<LevelColliderID, RigidBodyHandle>::new(),
                current_step_vm: None,
                gravity: Vector::new(x as Real, y as Real, z as Real),
                universal_drag: universal_drag as Real,
                manifold_info_map: SableManifoldInfoMap::default(),
            };

            scene.collider_set.insert(collider);
            scene.ground_handle = Some(scene.rigid_body_set.insert(ground));
            scene.current_step_vm =
                Some(JavaVM::from_raw(env.get_java_vm().unwrap().get_java_vm_pointer()).unwrap());
            state.scenes.insert(scene_id, scene);
        }
    }

    info!("Rapier initialized scene {}", scene_id);
}

/// Computes buoyancy
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_tick<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    _time_step: jdouble,
) {
    unsafe {
        if let Some(state) = &mut PHYSICS_STATE {
            rope::tick(scene_id);
            joints::tick(scene_id);

            let Some(scene) = state.scenes.get_mut(&scene_id) else {
                panic!("No scene with given ID!");
            };

            compute_buoyancy(scene);
        }
    }
}

/// Steps physics
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_step<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    time_step: jdouble,
) {
    unsafe {
        if let Some(state) = &mut PHYSICS_STATE {
            rope::tick(scene_id);
            joints::tick(scene_id);

            state.integration_parameters.dt = time_step as f32;

            let Some(scene) = state.scenes.get_mut(&scene_id) else {
                panic!("No scene with given ID!");
            };

            scene.manifold_info_map = SableManifoldInfoMap::default();

            scene.pipeline.step(
                scene.gravity,
                &state.integration_parameters,
                &mut scene.island_manager,
                &mut scene.broad_phase,
                &mut scene.narrow_phase,
                &mut scene.rigid_body_set,
                &mut scene.collider_set,
                &mut scene.impulse_joint_set,
                &mut scene.multibody_joint_set,
                &mut scene.ccd_solver,
                &scene.physics_hooks,
                &scene.event_handler,
            );
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_getPose<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id: jint,
    store: JDoubleArray<'local>,
) {
    unsafe {
        let Some(scene) = get_physics_state().scenes.get(&scene_id) else {
            panic!("No scene with given ID!");
        };

        let rb: &RigidBody = &scene.rigid_body_set[scene.rigid_bodies[&(id as LevelColliderID)]];

        let arr: [jdouble; 7] = [
            rb.translation().x as jdouble,
            rb.translation().y as jdouble,
            rb.translation().z as jdouble,
            rb.rotation().x as jdouble,
            rb.rotation().y as jdouble,
            rb.rotation().z as jdouble,
            rb.rotation().w as jdouble,
        ];

        env.set_double_array_region(&store, 0, &arr).unwrap();
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_setCenterOfMass<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id: jint,
    x: jdouble,
    y: jdouble,
    z: jdouble,
) {
    unsafe {
        if let Some(state) = &mut PHYSICS_STATE {
            let Some(scene) = state.scenes.get_mut(&scene_id) else {
                panic!("No scene with given ID!");
            };

            scene
                .level_colliders
                .get_mut(&(id as LevelColliderID))
                .unwrap()
                .center_of_mass = Some(NaVector3::new(x, y, z));
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_setLocalBounds<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id: jint,
    min_x: jint,
    min_y: jint,
    min_z: jint,
    max_x: jint,
    max_y: jint,
    max_z: jint,
) {
    unsafe {
        if let Some(state) = &mut PHYSICS_STATE {
            let Some(scene) = state.scenes.get_mut(&scene_id) else {
                panic!("No scene with given ID!");
            };

            scene
                .level_colliders
                .get_mut(&(id as LevelColliderID))
                .unwrap()
                .set_local_bounds(
                    NaVector3::new(min_x, min_y, min_z),
                    NaVector3::new(max_x, max_y, max_z),
                    scene_id,
                );
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_createSubLevel<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id: jint,
    pose: JDoubleArray<'local>,
) {
    let mut pose_arr: [jdouble; 7] = [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0];
    env.get_double_array_region(pose, 0, &mut pose_arr).unwrap();

    let quat = Quat::from_xyzw(
        pose_arr[3] as Real,
        pose_arr[4] as Real,
        pose_arr[5] as Real,
        pose_arr[6] as Real,
    );

    let mut rigid_body = RigidBodyBuilder::dynamic()
        .ccd_enabled(true)
        .translation(Vector::new(
            pose_arr[0] as Real,
            pose_arr[1] as Real,
            pose_arr[2] as Real,
        ))
        .build();
    rigid_body.set_rotation(quat, false);
    let activation_params = rigid_body.activation_mut();
    activation_params.angular_threshold = 0.15;
    activation_params.normalized_linear_threshold = 0.15;

    unsafe {
        if let Some(state) = &mut PHYSICS_STATE {
            let Some(scene) = state.scenes.get_mut(&scene_id) else {
                panic!("No scene with given ID!");
            };

            rigid_body.set_linear_damping(scene.universal_drag);
            rigid_body.set_angular_damping(scene.universal_drag);

            let handle = scene.rigid_body_set.insert(rigid_body);

            // make a level collider
            let collider = ColliderBuilder::new(SharedShape::new(LevelCollider::new(
                Some(id as LevelColliderID),
                false,
                scene_id,
            )))
            .friction(0.525)
            .active_events(ActiveEvents::CONTACT_FORCE_EVENTS)
            .active_hooks(ActiveHooks::MODIFY_SOLVER_CONTACTS)
            .density(0.0)
            .collision_groups(LEVEL_GROUP)
            .build();

            let collider_handle =
                scene
                    .collider_set
                    .insert_with_parent(collider, handle, &mut scene.rigid_body_set);

            scene.level_colliders.insert(
                id as LevelColliderID,
                ActiveLevelColliderInfo::new(collider_handle, scene_id),
            );

            scene.rigid_bodies.insert(id as LevelColliderID, handle);
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_removeSubLevel<
    'local,
>(
    mut _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id: jint,
) {
    unsafe {
        if let Some(state) = &mut PHYSICS_STATE {
            let Some(scene) = state.scenes.get_mut(&scene_id) else {
                panic!("No scene with given ID!");
            };

            let handle = scene.rigid_bodies[&(id as LevelColliderID)];
            scene.rigid_body_set.remove(
                handle,
                &mut scene.island_manager,
                &mut scene.collider_set,
                &mut scene.impulse_joint_set,
                &mut scene.multibody_joint_set,
                true,
            );

            scene.level_colliders.remove(&(id as LevelColliderID));
            scene.rigid_bodies.remove(&(id as LevelColliderID));
        }
    }
}

pub fn insert_block_octree(
    octree: &mut SubLevelOctree,
    state: &BlockState,
    remove: bool,
    x: i32,
    y: i32,
    z: i32,
) {
    let block_collider_id = state.0;
    let block_collider = if block_collider_id > 0 {
        let phys_state = unsafe { get_physics_state() };
        Some(
            phys_state
                .voxel_collider_map
                .voxel_colliders
                .get(block_collider_id as usize - 1)
                .unwrap(),
        )
    } else {
        None
    };
    let voxel_state = state.1;

    let solid = voxel_state != Interior
        && voxel_state != VoxelPhysicsState::Empty
        && (block_collider_id > 0
            && !block_collider
                .unwrap()
                .as_ref()
                .unwrap()
                .collision_boxes
                .is_empty());

    if remove && !solid {
        octree.insert(x, y, z, -1);
    }

    if solid {
        octree.insert(x, y, z, block_collider_id as i32);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_addChunk<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    x: jint,
    y: jint,
    z: jint,
    data: JIntArray<'local>,
    global: jboolean,
    object_id: jint,
) {
    let mut ints: [jint; 4096] = [0; 4096];
    env.get_int_array_region(data, 0, &mut ints).unwrap();

    let mut blocks = Vec::with_capacity(ints.len());

    for block in ints {
        // split it in half
        let block_collider_id = (block >> 16) as u16;
        let voxel_state_id = (block & 0xFFFF) as u16;

        blocks.push((
            block_collider_id as u32,
            ALL_VOXEL_PHYSICS_STATES[voxel_state_id as usize],
        ));
    }

    let chunk = ChunkSection::new(blocks);

    unsafe {
        if let Some(state) = &mut PHYSICS_STATE {
            let Some(scene) = state.scenes.get_mut(&scene_id) else {
                panic!("No scene with given ID!");
            };

            scene
                .main_level_chunks
                .insert(pack_section_pos(x, y, z), chunk);

            let chunk = scene
                .main_level_chunks
                .get(&pack_section_pos(x, y, z))
                .unwrap();
            if global == 0 {
                // println!("receving non global physics chunk");
                // println!("object id {:?}", object_id);
                if object_id != -1 {
                    let body = scene
                        .level_colliders
                        .get_mut(&(object_id as LevelColliderID))
                        .unwrap();

                    body.insert_chunk(chunk, x, y, z);
                    // println!("inserting blocks to octree");
                    // println!("post octree {:?}", body.octree);
                    // println!("post min {:?}", body.local_bounds_min);
                    // println!("post max {:?}", body.local_bounds_max);
                    // println!("post com {:?}", body.center_of_mass);
                }
            } else {
                for bx in 0..16 {
                    for by in 0..16 {
                        for bz in 0..16 {
                            let block = chunk.get_block(bx, by, bz);
                            let x = bx + (x << CHUNK_SHIFT);
                            let y = by + (y << CHUNK_SHIFT);
                            let z = bz + (z << CHUNK_SHIFT);

                            // insert into level octree
                            let ox = x >> OCTREE_CHUNK_SHIFT;
                            let oy = y >> OCTREE_CHUNK_SHIFT;
                            let oz = z >> OCTREE_CHUNK_SHIFT;

                            let mut octree_chunk =
                                scene.octree_chunks.get_mut(&pack_section_pos(ox, oy, oz));

                            if octree_chunk.is_none() {
                                scene.octree_chunks.insert(
                                    pack_section_pos(ox, oy, oz),
                                    OctreeChunkSection::new(),
                                );
                                octree_chunk =
                                    scene.octree_chunks.get_mut(&pack_section_pos(ox, oy, oz));
                            }

                            let Some(octree_chunk) = octree_chunk else {
                                panic!("No octree chunk!")
                            };

                            if block.0 == 0 {
                                insert_block_octree(
                                    &mut octree_chunk.liquid_octree,
                                    &block,
                                    false,
                                    x & (OCTREE_CHUNK_SIZE - 1),
                                    y & (OCTREE_CHUNK_SIZE - 1),
                                    z & (OCTREE_CHUNK_SIZE - 1),
                                );
                                insert_block_octree(
                                    &mut octree_chunk.octree,
                                    &block,
                                    false,
                                    x & (OCTREE_CHUNK_SIZE - 1),
                                    y & (OCTREE_CHUNK_SIZE - 1),
                                    z & (OCTREE_CHUNK_SIZE - 1),
                                );
                            } else {
                                if state.voxel_collider_map.voxel_colliders[(block.0 - 1) as usize]
                                    .as_ref()
                                    .unwrap()
                                    .is_fluid
                                {
                                    insert_block_octree(
                                        &mut octree_chunk.liquid_octree,
                                        &block,
                                        false,
                                        x & (OCTREE_CHUNK_SIZE - 1),
                                        y & (OCTREE_CHUNK_SIZE - 1),
                                        z & (OCTREE_CHUNK_SIZE - 1),
                                    );
                                } else {
                                    insert_block_octree(
                                        &mut octree_chunk.octree,
                                        &block,
                                        false,
                                        x & (OCTREE_CHUNK_SIZE - 1),
                                        y & (OCTREE_CHUNK_SIZE - 1),
                                        z & (OCTREE_CHUNK_SIZE - 1),
                                    );
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_removeChunk<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    x: jint,
    y: jint,
    z: jint,
    global: jboolean,
) {
    unsafe {
        if let Some(state) = &mut PHYSICS_STATE {
            let Some(scene) = state.scenes.get_mut(&scene_id) else {
                panic!("No scene with given ID!");
            };

            scene.main_level_chunks.remove(&pack_section_pos(x, y, z));

            if global > 0 {
                let octree_chunk = scene.octree_chunks.get_mut(&pack_section_pos(
                    (x << CHUNK_SHIFT) >> OCTREE_CHUNK_SHIFT,
                    (y << CHUNK_SHIFT) >> OCTREE_CHUNK_SHIFT,
                    (z << CHUNK_SHIFT) >> OCTREE_CHUNK_SHIFT,
                ));

                if let Some(octree_chunk) = octree_chunk {
                    for bx in 0..16 {
                        for by in 0..16 {
                            for bz in 0..16 {
                                let x = bx + (x << CHUNK_SHIFT);
                                let y = by + (y << CHUNK_SHIFT);
                                let z = bz + (z << CHUNK_SHIFT);

                                insert_block_octree(
                                    &mut octree_chunk.octree,
                                    &(0, VoxelPhysicsState::Empty),
                                    true,
                                    x & (OCTREE_CHUNK_SIZE - 1),
                                    y & (OCTREE_CHUNK_SIZE - 1),
                                    z & (OCTREE_CHUNK_SIZE - 1),
                                );
                                insert_block_octree(
                                    &mut octree_chunk.liquid_octree,
                                    &(0, VoxelPhysicsState::Empty),
                                    true,
                                    x & (OCTREE_CHUNK_SIZE - 1),
                                    y & (OCTREE_CHUNK_SIZE - 1),
                                    z & (OCTREE_CHUNK_SIZE - 1),
                                );
                            }
                        }
                    }

                    if octree_chunk.octree.buffer[0] == 0
                        && octree_chunk.liquid_octree.buffer[0] == 0
                    {
                        scene.octree_chunks.remove(&pack_section_pos(
                            (x << CHUNK_SHIFT) >> OCTREE_CHUNK_SHIFT,
                            (y << CHUNK_SHIFT) >> OCTREE_CHUNK_SHIFT,
                            (z << CHUNK_SHIFT) >> OCTREE_CHUNK_SHIFT,
                        ));
                    }
                }
            }
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_changeBlock<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    x: jint,
    y: jint,
    z: jint,
    block: jint,
) {
    let block_collider_id = (block >> 16) as u16;
    let voxel_state_id = (block & 0xFFFF) as u16;

    unsafe {
        if let Some(state) = &mut PHYSICS_STATE {
            let Some(scene) = state.scenes.get_mut(&scene_id) else {
                panic!("No scene with given ID!");
            };

            let chunk = scene
                .main_level_chunks
                .get_mut(&pack_section_pos(x >> 4, y >> 4, z >> 4));
            if let Some(chunk) = chunk {
                let block_state = (
                    block_collider_id as u32,
                    ALL_VOXEL_PHYSICS_STATES[voxel_state_id as usize],
                );

                chunk.set_block(x & 15, y & 15, z & 15, block_state);

                let mut any = false;
                for (_, sable_body) in scene.level_colliders.iter_mut() {
                    if sable_body.contains(x, y, z) {
                        sable_body.insert_block(x, y, z, &block_state, true);
                        any = true;
                        break;
                    }
                }

                if !any {
                    // insert into level octree
                    let ox = x >> OCTREE_CHUNK_SHIFT;
                    let oy = y >> OCTREE_CHUNK_SHIFT;
                    let oz = z >> OCTREE_CHUNK_SHIFT;

                    let mut octree_chunk =
                        scene.octree_chunks.get_mut(&pack_section_pos(ox, oy, oz));

                    if octree_chunk.is_none() {
                        scene
                            .octree_chunks
                            .insert(pack_section_pos(ox, oy, oz), OctreeChunkSection::new());
                        octree_chunk = scene.octree_chunks.get_mut(&pack_section_pos(ox, oy, oz));
                    }

                    let Some(octree_chunk) = octree_chunk else {
                        panic!("No octree chunk!")
                    };

                    if block_collider_id == 0 {
                        insert_block_octree(
                            &mut octree_chunk.octree,
                            &block_state,
                            true,
                            x & (OCTREE_CHUNK_SIZE - 1),
                            y & (OCTREE_CHUNK_SIZE - 1),
                            z & (OCTREE_CHUNK_SIZE - 1),
                        );
                        insert_block_octree(
                            &mut octree_chunk.liquid_octree,
                            &block_state,
                            true,
                            x & (OCTREE_CHUNK_SIZE - 1),
                            y & (OCTREE_CHUNK_SIZE - 1),
                            z & (OCTREE_CHUNK_SIZE - 1),
                        );
                    } else {
                        if state
                            .voxel_collider_map
                            .voxel_colliders
                            .get(block_collider_id as usize - 1)
                            .unwrap()
                            .as_ref()
                            .unwrap()
                            .is_fluid
                        {
                            insert_block_octree(
                                &mut octree_chunk.liquid_octree,
                                &block_state,
                                false,
                                x & (OCTREE_CHUNK_SIZE - 1),
                                y & (OCTREE_CHUNK_SIZE - 1),
                                z & (OCTREE_CHUNK_SIZE - 1),
                            );
                        } else {
                            insert_block_octree(
                                &mut octree_chunk.octree,
                                &block_state,
                                false,
                                x & (OCTREE_CHUNK_SIZE - 1),
                                y & (OCTREE_CHUNK_SIZE - 1),
                                z & (OCTREE_CHUNK_SIZE - 1),
                            );
                        }
                    }
                }
            }
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_setMassProperties<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id: jint,
    mass: jdouble,
    center_of_mass: JDoubleArray<'local>,
    inertia: JDoubleArray<'local>,
) {
    let mut com: [jdouble; 3] = [0.0, 0.0, 0.0];
    env.get_double_array_region(center_of_mass, 0, &mut com)
        .unwrap();

    let mut inertia_arr: [jdouble; 9] = [0.0; 9];
    env.get_double_array_region(inertia, 0, &mut inertia_arr)
        .unwrap();

    let inertia_tensor = Matrix3::new(
        inertia_arr[0] as Real,
        inertia_arr[1] as Real,
        inertia_arr[2] as Real,
        inertia_arr[3] as Real,
        inertia_arr[4] as Real,
        inertia_arr[5] as Real,
        inertia_arr[6] as Real,
        inertia_arr[7] as Real,
        inertia_arr[8] as Real,
    );

    let scene = get_scene_mut_ref(scene_id);

    let rb = &mut scene.rigid_body_set[scene.rigid_bodies[&(id as LevelColliderID)]];

    rb.set_additional_mass_properties(
        MassProperties::with_inertia_matrix(Vector::ZERO, mass as Real, inertia_tensor.into()),
        true,
    );
}

/// Teleports the object to the given position.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_teleportObject<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id: jint,
    x: jdouble,
    y: jdouble,
    z: jdouble,
    i: jdouble,
    j: jdouble,
    k: jdouble,
    r: jdouble,
) {
    let scene = get_scene_mut_ref(scene_id);
    let rb = &mut scene.rigid_body_set[scene.rigid_bodies[&(id as LevelColliderID)]];

    let mut pose = *rb.position();
    pose.translation = Vector::new(x as Real, y as Real, z as Real);
    pose.rotation = Quat::from_xyzw(i as Real, j as Real, k as Real, r as Real);
    rb.set_position(pose, true);
}

/// Wakes up an object.
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_wakeUpObject<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id: jint,
) {
    let scene = get_scene_mut_ref(scene_id);
    let rb = &mut scene.rigid_body_set[scene.rigid_bodies[&(id as LevelColliderID)]];
    rb.wake_up(true);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_addLinearAngularVelocities<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id: jint,
    linear_x: jdouble,
    linear_y: jdouble,
    linear_z: jdouble,
    angular_x: jdouble,
    angular_y: jdouble,
    angular_z: jdouble,
    wake_up: jboolean,
) {
    let scene = get_scene_mut_ref(scene_id);
    let rb = get_rigid_body_mut(scene, id as LevelColliderID);

    if wake_up == 0 && rb.is_sleeping() {
        return;
    }

    rb.set_linvel(
        rb.linvel() + Vector::new(linear_x as Real, linear_y as Real, linear_z as Real),
        wake_up > 0,
    );
    rb.set_angvel(
        rb.angvel() + Vector::new(angular_x as Real, angular_y as Real, angular_z as Real),
        wake_up > 0,
    );
}

/// Clears & queries all collisions
///
/// TODO: Do not pass body IDs as doubles, stupid as hell lmao
///
/// A collision is formatted as follows:
/// [body_a, body_b, force_amount, local_normal_a, local_normal_b, local_point_a, local_point_b]
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_clearCollisions<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
) -> JDoubleArray<'local> {
    let scene = get_scene_mut_ref(scene_id);

    let max_collisions = 100;

    scene.reported_collisions.truncate(max_collisions);
    let mut arr: Vec<jdouble> = Vec::with_capacity(scene.reported_collisions.len() * 15);

    for collision in scene.reported_collisions.iter() {
        let body_a = if let Some(id) = collision.body_a {
            id as jdouble
        } else {
            -1.0
        };

        let body_b = if let Some(id) = collision.body_b {
            id as jdouble
        } else {
            -1.0
        };

        arr.push(body_a);
        arr.push(body_b);
        arr.push(collision.force_amount as jdouble);
        arr.push(collision.local_normal_a.x as jdouble);
        arr.push(collision.local_normal_a.y as jdouble);
        arr.push(collision.local_normal_a.z as jdouble);
        arr.push(collision.local_normal_b.x as jdouble);
        arr.push(collision.local_normal_b.y as jdouble);
        arr.push(collision.local_normal_b.z as jdouble);
        arr.push(collision.local_point_a.x as jdouble);
        arr.push(collision.local_point_a.y as jdouble);
        arr.push(collision.local_point_a.z as jdouble);
        arr.push(collision.local_point_b.x as jdouble);
        arr.push(collision.local_point_b.y as jdouble);
        arr.push(collision.local_point_b.z as jdouble);
    }

    let double_array = _env.new_double_array(arr.len() as jint).unwrap();
    _env.set_double_array_region(&double_array, 0, &arr)
        .unwrap();

    scene.reported_collisions.clear();

    double_array
}

/// Applies a force to a body
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_applyForce<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id: jint,
    x: jdouble,
    y: jdouble,
    z: jdouble,
    fx: jdouble,
    fy: jdouble,
    fz: jdouble,
    wake_up: jboolean,
) {
    unsafe {
        let Some(state) = &mut PHYSICS_STATE else {
            panic!("No physics state!");
        };

        let Some(scene) = state.scenes.get_mut(&scene_id) else {
            panic!("No scene with given ID!");
        };

        let body = scene.rigid_bodies.get(&(id as LevelColliderID)).unwrap();
        let rb = &mut scene.rigid_body_set[*body];

        if wake_up == 0 && rb.is_sleeping() {
            return;
        }

        let force: Vector = rb
            .rotation()
            .mul_vec3(Vector::new(fx as Real, fy as Real, fz as Real));
        let force_pos = rb
            .position()
            .transform_point(Vector::new(x as Real, y as Real, z as Real));

        rb.apply_impulse(force, wake_up > 0);

        let torque_impulse = (force_pos - rb.position().translation).cross(force);
        rb.apply_torque_impulse(torque_impulse, wake_up > 0);
    }
}

/// Applies a force and torque
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_applyForceAndTorque<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id: jint,
    fx: jdouble,
    fy: jdouble,
    fz: jdouble,
    tx: jdouble,
    ty: jdouble,
    tz: jdouble,
    wake_up: jboolean,
) {
    unsafe {
        let Some(state) = &mut PHYSICS_STATE else {
            panic!("No physics state!");
        };

        let Some(scene) = state.scenes.get_mut(&scene_id) else {
            panic!("No scene with given ID!");
        };

        let body = scene.rigid_bodies.get(&(id as LevelColliderID)).unwrap();
        let rb = &mut scene.rigid_body_set[*body];

        if wake_up == 0 && rb.is_sleeping() {
            return;
        }

        let force: Vector = rb
            .rotation()
            .mul_vec3(Vector::new(fx as Real, fy as Real, fz as Real));
        rb.apply_impulse(force, wake_up > 0);

        let torque: Vector = rb
            .rotation()
            .mul_vec3(Vector::new(tx as Real, ty as Real, tz as Real));
        rb.apply_torque_impulse(torque, wake_up > 0);
    }
}

/// Gets the linear velocity of a body
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_getLinearVelocity<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id: jint,
    store: JDoubleArray<'local>,
) {
    unsafe {
        let Some(state) = &mut PHYSICS_STATE else {
            panic!("No physics state!");
        };

        let Some(scene) = state.scenes.get_mut(&scene_id) else {
            panic!("No scene with given ID!");
        };

        let body = scene.rigid_bodies.get(&(id as LevelColliderID)).unwrap();
        let rb = &scene.rigid_body_set[*body];

        let vel = rb.linvel();

        _env.set_double_array_region(
            &store,
            0,
            &[vel.x as jdouble, vel.y as jdouble, vel.z as jdouble],
        )
        .unwrap();
    }
}

/// Gets the angular velocity of a body
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_getAngularVelocity<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id: jint,
    store: JDoubleArray<'local>,
) {
    unsafe {
        let Some(state) = &mut PHYSICS_STATE else {
            panic!("No physics state!");
        };

        let Some(scene) = state.scenes.get_mut(&scene_id) else {
            panic!("No scene with given ID!");
        };

        let body = scene.rigid_bodies.get(&(id as LevelColliderID)).unwrap();
        let rb = &scene.rigid_body_set[*body];

        let vel = rb.angvel();

        _env.set_double_array_region(
            &store,
            0,
            &[vel.x as jdouble, vel.y as jdouble, vel.z as jdouble],
        )
        .unwrap();
    }
}

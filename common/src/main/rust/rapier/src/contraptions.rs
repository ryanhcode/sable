use std::collections::HashMap;

use jni::JNIEnv;
use jni::objects::{JClass, JDoubleArray, JIntArray};
use jni::sys::{jdouble, jint};
use rapier3d::dynamics::RigidBodyBuilder;
use rapier3d::geometry::{ColliderBuilder, SharedShape};
use rapier3d::glamx::{DVec3, IVec3, Pose3};
use rapier3d::pipeline::{ActiveEvents, ActiveHooks};
use rapier3d::prelude::{RigidBodyHandle, RigidBodyVelocity};

use crate::collider::LevelCollider;
use crate::groups::LEVEL_GROUP;
use crate::scene::LevelColliderID;
use crate::{ActiveLevelColliderInfo, PoseExt, get_scene_mut_ref};

macro_rules! extract_jdouble_array {
    ($env:expr, $jarr:expr, $len:expr) => {{
        let mut arr = [0.0 as jdouble; $len];
        $env.get_double_array_region($jarr, 0, &mut arr).unwrap();
        arr
    }};
}

macro_rules! extract_jint_array {
    ($env:expr, $jarr:expr, $len:expr) => {{
        let mut arr = [0 as jint; $len];
        $env.get_int_array_region($jarr, 0, &mut arr).unwrap();
        arr
    }};
}

// Helper for getting a mutable kinematic sub-level collider info
fn get_kinematic_collider_info(
    scene: &mut crate::scene::PhysicsScene,
    id: jint,
) -> &mut ActiveLevelColliderInfo {
    scene
        .level_colliders
        .get_mut(&(id as LevelColliderID))
        .expect("No kinematic contraption with given ID!")
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_createKinematicContraption<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    mount_id: jint,
    id: jint,
    _pose: JDoubleArray<'local>,
) {
    let scene = get_scene_mut_ref(scene_id);

    let should_be_static = mount_id == -1;
    let mount_rigid_body = if should_be_static {
        let new_body = scene
            .rigid_body_set
            .insert(RigidBodyBuilder::kinematic_position_based());
        Some(new_body)
    } else {
        Some(
            *scene
                .rigid_bodies
                .get(&(mount_id as LevelColliderID))
                .unwrap(),
        )
    };

    let mount_rigid_body: RigidBodyHandle = if let Some(body) = mount_rigid_body {
        body
    } else {
        panic!("woops!")
    };

    let level_collider = LevelCollider::new(Some(id as LevelColliderID), false, scene_id);

    let collider = ColliderBuilder::new(SharedShape::new(level_collider))
        .friction(0.45)
        .active_events(ActiveEvents::CONTACT_FORCE_EVENTS)
        .active_hooks(ActiveHooks::MODIFY_SOLVER_CONTACTS)
        .density(0.0)
        .collision_groups(LEVEL_GROUP)
        .build();

    let collider_handle = scene.collider_set.insert_with_parent(
        collider,
        mount_rigid_body,
        &mut scene.rigid_body_set,
    );

    let mut info = ActiveLevelColliderInfo::new(collider_handle, scene_id);
    if should_be_static {
        info.static_mount = Some(mount_rigid_body);
    }

    info.chunk_map = Some(HashMap::new()); // Use a dedicated chunk map as it doesn't have a plot java-side
    scene.level_colliders.insert(id as LevelColliderID, info);
}

/// Set the transform (position/orientation) of a kinematic sub-level's center of mass relative to its parent
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_setKinematicContraptionTransform<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id: jint,
    center_of_mass: JDoubleArray<'local>,
    pose: JDoubleArray<'local>,
    velocities: JDoubleArray<'local>,
) {
    let center_of_mass_arr = extract_jdouble_array!(env, center_of_mass, 3);
    let pose_arr = extract_jdouble_array!(env, pose, 7);
    let velocities_arr = extract_jdouble_array!(env, velocities, 6);

    let isometry = Pose3::from_jdouble_array(pose_arr);

    let scene = get_scene_mut_ref(scene_id);
    let info = get_kinematic_collider_info(scene, id);
    let collider_handle = info.collider;

    let scene = get_scene_mut_ref(scene_id);
    let collider = scene.collider_set.get_mut(collider_handle);

    if collider.is_none() {
        return;
    }

    // if (info.static_mount.is_some()) {
    //     let body = scene.rigid_body_set.get_mut(info.static_mount.unwrap()).unwrap();
    //
    //     if (info.fake_velocities.is_none()) {
    //         body.set_position(isometry, true);
    //     }
    //
    //     // body.set_next_kinematic_position(isometry);
    //     scene.impulse_joint_set.remove_joints_attached_to_rigid_body(info.static_mount.unwrap());
    // } else {
    let collider = collider.unwrap();

    collider.set_position_wrt_parent(isometry);
    // }

    info.center_of_mass = Some(DVec3::from_array(center_of_mass_arr));

    info.fake_velocities = Some(RigidBodyVelocity::new(
        DVec3::from_array(*velocities_arr.first_chunk().expect("const inbounds")).as_vec3(),
        DVec3::from_array(*velocities_arr[3..].first_chunk().expect("const inbounds")).as_vec3(),
    ));
}

/// Add a chunk to a kinematic sub-level (4096 blocks, each as packed int)
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_addKinematicContraptionChunkSection<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id: jint,
    x: jint,
    y: jint,
    z: jint,
    data: JIntArray<'local>,
) {
    let ints = extract_jint_array!(env, data, 4096);
    let mut blocks = Vec::with_capacity(ints.len());
    for block in ints {
        let block_collider_id = (block >> 16) as u16;
        let voxel_state_id = (block & 0xFFFF) as u16;
        blocks.push((
            block_collider_id as u32,
            crate::ALL_VOXEL_PHYSICS_STATES[voxel_state_id as usize],
        ));
    }
    let chunk = marten::level::ChunkSection::new(blocks);

    let scene = get_scene_mut_ref(scene_id);

    let info = get_kinematic_collider_info(scene, id);
    if let Some(chunk_map) = &mut info.chunk_map {
        chunk_map.insert(crate::scene::pack_section_pos(IVec3::new(x, y, z)), chunk);
    }
}

/// Remove a kinematic sub-level from a scene
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_removeKinematicContraption<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id: jint,
) {
    let scene = get_scene_mut_ref(scene_id);
    let info = scene.level_colliders.remove(&(id as LevelColliderID));
    let info = info.unwrap();

    scene.collider_set.remove(
        info.collider,
        &mut scene.island_manager,
        &mut scene.rigid_body_set,
        true,
    );

    if let Some(mount_handle) = info.static_mount {
        scene.rigid_body_set.remove(
            mount_handle,
            &mut scene.island_manager,
            &mut scene.collider_set,
            &mut scene.impulse_joint_set,
            &mut scene.multibody_joint_set,
            true,
        );
    }
}

use std::collections::HashMap;

use jni::JNIEnv;
use jni::objects::{JClass, JDoubleArray, JIntArray};
use jni::sys::{jdouble, jint, jlong};
use marten::Real;
use rapier3d::dynamics::RigidBodyBuilder;
use rapier3d::geometry::{ColliderBuilder, SharedShape};
use rapier3d::glamx::{DVec3, Pose3, Quat};
use rapier3d::math::Vec3;
use rapier3d::pipeline::{ActiveEvents, ActiveHooks};
use rapier3d::prelude::{RigidBodyHandle, RigidBodyVelocity};

use crate::collider::LevelCollider;
use crate::groups::LEVEL_GROUP;
use crate::scene::{LevelColliderID, SableSceneData};
use crate::{ActiveLevelColliderInfo, with_handle};

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
    sable: &mut SableSceneData,
    id: jint,
) -> Option<&mut ActiveLevelColliderInfo> {
    sable.level_colliders.get_mut(&(id as LevelColliderID))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_createKinematicContraption<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    mount_id: jint,
    id: jint,
    _pose: JDoubleArray<'local>,
) {
    with_handle(handle, |scene| {
        let mut sim_data = scene.sim_data.write().unwrap();
        let sim_data = &mut *sim_data;
        let mut sable_data = scene.sable_data.write().unwrap();

        let should_be_static = mount_id == -1;
        let mount_rigid_body = if should_be_static {
            let new_body = sim_data
                .rigid_body_set
                .insert(RigidBodyBuilder::kinematic_position_based());
            Some(new_body)
        } else {
            sable_data
                .rigid_bodies
                .get(&(mount_id as LevelColliderID))
                .copied()
        };

        let mount_rigid_body: RigidBodyHandle = if let Some(body) = mount_rigid_body {
            body
        } else {
            // The mount body was unloaded before this call reached us; nothing to attach to.
            return;
        };

        let level_collider = LevelCollider::new(Some(id as LevelColliderID), false);

        let collider = ColliderBuilder::new(SharedShape::new(level_collider))
            .friction(0.45)
            .active_events(ActiveEvents::CONTACT_FORCE_EVENTS)
            .active_hooks(ActiveHooks::MODIFY_SOLVER_CONTACTS)
            .density(0.0)
            .collision_groups(LEVEL_GROUP)
            .build();

        let collider_handle = sim_data.collider_set.insert_with_parent(
            collider,
            mount_rigid_body,
            &mut sim_data.rigid_body_set,
        );

        let mut info = ActiveLevelColliderInfo::new(collider_handle);
        if should_be_static {
            info.static_mount = Some(mount_rigid_body);
        }

        info.chunk_map = Some(HashMap::new()); // Use a dedicated chunk map as it doesn't have a plot java-side
        sable_data
            .level_colliders
            .insert(id as LevelColliderID, info);
    })
}

/// Set the transform (position/orientation) of a kinematic sub-level's center of mass relative to its parent
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_setKinematicContraptionTransform<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    id: jint,
    center_of_mass: JDoubleArray<'local>,
    pose: JDoubleArray<'local>,
    velocities: JDoubleArray<'local>,
) {
    let center_of_mass_arr = extract_jdouble_array!(env, center_of_mass, 3);
    let pose_arr = extract_jdouble_array!(env, pose, 7);
    let velocities_arr = extract_jdouble_array!(env, velocities, 6);
    let translation = Vec3::new(
        pose_arr[0] as Real,
        pose_arr[1] as Real,
        pose_arr[2] as Real,
    );
    let quat = Quat::from_xyzw(
        pose_arr[3] as Real,
        pose_arr[4] as Real,
        pose_arr[5] as Real,
        pose_arr[6] as Real,
    );

    with_handle(handle, |scene| {
        let mut sim_data = scene.sim_data.write().unwrap();
        let mut sable_data = scene.sable_data.write().unwrap();

        let Some(info) = get_kinematic_collider_info(&mut sable_data, id) else {
            return;
        };
        let collider_handle = info.collider;

        let collider = sim_data.collider_set.get_mut(collider_handle);

        if collider.is_none() {
            return;
        }

        let isometry = Pose3 {
            rotation: quat,
            translation,
        };

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

        info.center_of_mass = Some(DVec3::new(
            center_of_mass_arr[0],
            center_of_mass_arr[1],
            center_of_mass_arr[2],
        ));

        info.fake_velocities = Some(RigidBodyVelocity::new(
            Vec3::new(
                velocities_arr[0] as Real,
                velocities_arr[1] as Real,
                velocities_arr[2] as Real,
            ),
            Vec3::new(
                velocities_arr[3] as Real,
                velocities_arr[4] as Real,
                velocities_arr[5] as Real,
            ),
        ));
    })
}

/// Add a chunk to a kinematic sub-level (4096 blocks, each as packed int)
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_addKinematicContraptionChunkSection<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
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

    with_handle(handle, |scene| {
        let mut sable_data = scene.sable_data.write().unwrap();
        let Some(info) = get_kinematic_collider_info(&mut sable_data, id) else {
            return;
        };
        if let Some(chunk_map) = &mut info.chunk_map {
            chunk_map.insert(crate::scene::pack_section_pos(x, y, z), chunk);
        }
    })
}

/// Remove a kinematic sub-level from a scene
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_removeKinematicContraption<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    id: jint,
) {
    with_handle(handle, |scene| {
        let mut sim_data = scene.sim_data.write().unwrap();
        let sim_data = &mut *sim_data;
        let mut sable_data = scene.sable_data.write().unwrap();

        // Already removed (e.g. a duplicate/late removal after an unload) - nothing to do.
        let Some(info) = sable_data.level_colliders.remove(&(id as LevelColliderID)) else {
            return;
        };

        sim_data.collider_set.remove(
            info.collider,
            &mut sim_data.island_manager,
            &mut sim_data.rigid_body_set,
            true,
        );

        if let Some(mount_handle) = info.static_mount {
            sim_data.rigid_body_set.remove(
                mount_handle,
                &mut sim_data.island_manager,
                &mut sim_data.collider_set,
                &mut sim_data.impulse_joint_set,
                &mut sim_data.multibody_joint_set,
                true,
            );
        }
    })
}

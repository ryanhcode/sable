use std::collections::HashMap;

use jni::sys::{jdouble, jint};
use marten::Real;
use rapier3d::dynamics::RigidBodyBuilder;
use rapier3d::geometry::{ColliderBuilder, SharedShape};
use rapier3d::glamx::{Pose3, Quat};
use rapier3d::math::Vector;
use rapier3d::na::Vector3;
use rapier3d::pipeline::{ActiveEvents, ActiveHooks};
use rapier3d::prelude::{RigidBodyHandle, RigidBodyVelocity};

use crate::collider::LevelCollider;
use crate::groups::LEVEL_GROUP;
use crate::scene::LevelColliderID;
use crate::{ActiveLevelColliderInfo, get_scene_mut_ref};

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

pub fn create_kinematic_contraption(
    scene_id: jint,
    mount_id: jint,
    id: jint,
    // pose_arr: [jdouble; 7]
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
#[inline]
pub fn set_kinematic_contraption_transform(
    scene_id: jint,
    id: jint,
    center_of_mass_arr: [jdouble; 3],
    pose_arr: [jdouble; 7],
    velocities_arr: [jdouble; 6],
) {
    let translation = Vector3::new(
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

    let scene = get_scene_mut_ref(scene_id);
    let info = get_kinematic_collider_info(scene, id);
    let collider_handle = info.collider;

    let scene = get_scene_mut_ref(scene_id);
    let collider = scene.collider_set.get_mut(collider_handle);

    if collider.is_none() {
        return;
    }

    let isometry = Pose3 {
        rotation: quat,
        translation: Vector::new(translation.x, translation.y, translation.z),
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

    info.center_of_mass = Some(Vector3::new(
        center_of_mass_arr[0],
        center_of_mass_arr[1],
        center_of_mass_arr[2],
    ));

    info.fake_velocities = Some(RigidBodyVelocity::new(
        Vector::new(
            velocities_arr[0] as Real,
            velocities_arr[1] as Real,
            velocities_arr[2] as Real,
        ),
        Vector::new(
            velocities_arr[3] as Real,
            velocities_arr[4] as Real,
            velocities_arr[5] as Real,
        ),
    ));
}

/// Add a chunk to a kinematic sub-level (4096 blocks, each as packed int)
pub fn add_kinematic_contraption_chunk_section(
    scene_id: jint,
    id: jint,
    x: jint,
    y: jint,
    z: jint,
    ints: &[i32; 4096],
) {
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
        chunk_map.insert(crate::scene::pack_section_pos(x, y, z), chunk);
    }
}

/// Remove a kinematic sub-level from a scene
pub fn remove_kinematic_contraption(scene_id: jint, id: jint) {
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

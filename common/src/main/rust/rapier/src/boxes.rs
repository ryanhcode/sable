use jni::JNIEnv;
use jni::objects::{JClass, JDoubleArray};
use jni::sys::{jdouble, jint};
use marten::Real;
use rapier3d::dynamics::RigidBodyBuilder;
use rapier3d::geometry::{ColliderBuilder, SharedShape};
use rapier3d::glamx::Quat;
use rapier3d::math::Vector;

use crate::get_scene_mut;
use crate::scene::LevelColliderID;

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_createBox<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id: jint,
    mass: jdouble,
    half_extent_x: jdouble,
    half_extent_y: jdouble,
    half_extent_z: jdouble,
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

    let scene = get_scene_mut(scene_id);

    let handle = scene.rigid_body_set.insert(rigid_body);

    // make a level collider
    let collider = ColliderBuilder::new(SharedShape::cuboid(
        half_extent_x as Real,
        half_extent_y as Real,
        half_extent_z as Real,
    ))
    .mass(mass as Real)
    .friction(0.45)
    .build();

    scene
        .collider_set
        .insert_with_parent(collider, handle, &mut scene.rigid_body_set);

    scene.rigid_bodies.insert(id as LevelColliderID, handle);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_removeBox<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id: jint,
) {
    let scene = get_scene_mut(scene_id);
    let handle = scene.rigid_bodies[&(id as LevelColliderID)];
    scene.rigid_body_set.remove(
        handle,
        &mut scene.island_manager,
        &mut scene.collider_set,
        &mut scene.impulse_joint_set,
        &mut scene.multibody_joint_set,
        true,
    );

    scene.rigid_bodies.remove(&(id as LevelColliderID));
}

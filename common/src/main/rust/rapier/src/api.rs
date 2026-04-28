use core::slice;

use fisher::tracked_call;
use jni::{
    JNIEnv, JavaVM,
    objects::{JClass, JDoubleArray, JIntArray, JObject},
    sys::{jboolean, jdouble, jint, jlong, jsize},
};
use marten::level::SableMethodID;

use crate::{
    add_chunk, add_linear_velocities, apply_force, apply_force_and_torque,
    boxes::{create_box, remove_box},
    change_block, clear_collisions,
    config::{config_frequency_and_damping, config_min_island_size, config_solver_iterations},
    contraptions::{
        add_kinematic_contraption_chunk_section, create_kinematic_contraption,
        remove_kinematic_contraption, set_kinematic_contraption_transform,
    },
    create_sub_level, get_angular_velocity, get_linear_velocity, get_pose, initialize,
    joints::{
        add_fixed_constraint, add_free_constraint, add_generic_constraint, add_rotary_constraint,
        get_constraint_impulses, is_constraint_valid, remove_constraint,
        set_constraint_contacts_enabled, set_constraint_frame, set_constraint_motor,
    },
    remove_chunk, remove_sub_level,
    rope::{
        add_rope_point_at_start, create_rope, query_rope, remove_rope, remove_rope_point_at_start,
        set_rope_attachment, set_rope_first_segment_length, wake_up_rope,
    },
    set_center_of_mass, set_local_bounds, set_mass_properties, step, teleport_object, tick,
    voxel_collider::{add_voxel_collider_box, clear_voxel_collider_boxes, new_voxel_collider},
    wake_up_object,
};
macro_rules! extract_jdouble_array {
    ($env:expr, $jarr:expr, $len:expr) => {{
        let mut arr = [0.0 as jdouble; $len];
        $env.get_double_array_region($jarr, 0, &mut arr).unwrap();
        arr
    }};
}

fisher::decl_tracked_api! {
    pub mod recording {
        add_chunk, add_linear_velocities, apply_force, apply_force_and_torque, create_box, remove_box, change_block, clear_collisions,
        config_frequency_and_damping, config_min_island_size, config_solver_iterations,
        add_kinematic_contraption_chunk_section, create_kinematic_contraption,
        remove_kinematic_contraption, set_kinematic_contraption_transform,
        create_sub_level, get_angular_velocity, get_linear_velocity, get_pose, initialize,
        add_fixed_constraint, add_free_constraint, add_generic_constraint, add_rotary_constraint,
        get_constraint_impulses, is_constraint_valid, remove_constraint,
        set_constraint_contacts_enabled, set_constraint_frame, set_constraint_motor,
        remove_chunk, remove_sub_level,
        add_rope_point_at_start, create_rope, query_rope, remove_rope, remove_rope_point_at_start,
        set_rope_attachment, set_rope_first_segment_length, wake_up_rope,
        set_center_of_mass, set_local_bounds, set_mass_properties, step, teleport_object, tick,
        add_voxel_collider_box, clear_voxel_collider_boxes, new_voxel_collider,
        wake_up_object,
    }
}

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
    let pose_arr: [jdouble; 7] = extract_jdouble_array!(env, pose, 7);

    tracked_call!(create_box(
        scene_id,
        id,
        mass,
        half_extent_x,
        half_extent_y,
        half_extent_z,
        pose_arr,
    ));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_removeBox<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id: jint,
) {
    tracked_call!(remove_box(scene_id, id));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_configFrequencyAndDamping<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    collision_natural_frequency: jdouble,
    collision_damping_ratio: jdouble,
) {
    tracked_call!(config_frequency_and_damping(
        collision_natural_frequency,
        collision_damping_ratio
    ));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_configSolverIterations<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    num_solver_iterations: jint,
    num_internal_pgs_iterations: jint,
    num_internal_stabilization_iterations: jint,
) {
    tracked_call!(config_solver_iterations(
        num_solver_iterations,
        num_internal_pgs_iterations,
        num_internal_stabilization_iterations,
    ));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_configMinIslandSize<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    island_size: jint,
) {
    tracked_call!(config_min_island_size(island_size));
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
    tracked_call!(create_kinematic_contraption(scene_id, mount_id, id));
}

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
    tracked_call!(set_kinematic_contraption_transform(
        scene_id,
        id,
        center_of_mass_arr,
        pose_arr,
        velocities_arr
    ));
}

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
    let mut buf = Box::<[i32; 4096]>::new_uninit();
    env.get_int_array_region(data, 0, unsafe {
        slice::from_raw_parts_mut((*buf).as_mut_ptr().cast(), 4096)
    })
    .expect("to copy int array");
    let buf = unsafe { buf.assume_init() };
    tracked_call!(add_kinematic_contraption_chunk_section(
        scene_id, id, x, y, z, buf
    ));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_removeKinematicContraption<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id: jint,
) {
    tracked_call!(remove_kinematic_contraption(scene_id, id));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_setConstraintMotor<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    joint_id: jlong,
    axis: jint,
    target_pos: jdouble,
    stiffness: jdouble,
    damping: jdouble,
    has_max_force: jboolean,
    max_force: jdouble,
) {
    tracked_call!(set_constraint_motor(
        scene_id,
        joint_id,
        axis,
        target_pos,
        stiffness,
        damping,
        has_max_force,
        max_force,
    ));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_isConstraintValid<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    joint_id: jlong,
) -> jboolean {
    tracked_call!(is_constraint_valid(scene_id, joint_id))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_getConstraintImpulses<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    joint_id: jlong,
    store: JDoubleArray<'local>,
) {
    let arr = tracked_call!(get_constraint_impulses(scene_id, joint_id));
    env.set_double_array_region(&store, 0, &arr).unwrap();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_setConstraintContactsEnabled<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    joint_id: jlong,
    enabled: jboolean,
) {
    tracked_call!(set_constraint_contacts_enabled(scene_id, joint_id, enabled));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_removeConstraint<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    joint_id: jlong,
) {
    tracked_call!(remove_constraint(scene_id, joint_id));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_addRotaryConstraint<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id_a: jint,
    id_b: jint,
    local_x_a: jdouble,
    local_y_a: jdouble,
    local_z_a: jdouble,
    local_x_b: jdouble,
    local_y_b: jdouble,
    local_z_b: jdouble,
    axis_x_a: jdouble,
    axis_y_a: jdouble,
    axis_z_a: jdouble,
    axis_x_b: jdouble,
    axis_y_b: jdouble,
    axis_z_b: jdouble,
) -> jlong {
    tracked_call!(add_rotary_constraint(
        scene_id, id_a, id_b, local_x_a, local_y_a, local_z_a, local_x_b, local_y_b, local_z_b,
        axis_x_a, axis_y_a, axis_z_a, axis_x_b, axis_y_b, axis_z_b,
    ))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_addFixedConstraint<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id_a: jint,
    id_b: jint,
    local_x_a: jdouble,
    local_y_a: jdouble,
    local_z_a: jdouble,
    local_x_b: jdouble,
    local_y_b: jdouble,
    local_z_b: jdouble,
    local_q_x: jdouble,
    local_q_y: jdouble,
    local_q_z: jdouble,
    local_q_w: jdouble,
) -> jlong {
    tracked_call!(add_fixed_constraint(
        scene_id, id_a, id_b, local_x_a, local_y_a, local_z_a, local_x_b, local_y_b, local_z_b,
        local_q_x, local_q_y, local_q_z, local_q_w,
    ))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_addFreeConstraint<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id_a: jint,
    id_b: jint,
    local_x_a: jdouble,
    local_y_a: jdouble,
    local_z_a: jdouble,
    local_x_b: jdouble,
    local_y_b: jdouble,
    local_z_b: jdouble,
    local_q_x: jdouble,
    local_q_y: jdouble,
    local_q_z: jdouble,
    local_q_w: jdouble,
) -> jlong {
    tracked_call!(add_free_constraint(
        scene_id, id_a, id_b, local_x_a, local_y_a, local_z_a, local_x_b, local_y_b, local_z_b,
        local_q_x, local_q_y, local_q_z, local_q_w,
    ))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_addGenericConstraint<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id_a: jint,
    id_b: jint,
    local_x_a: jdouble,
    local_y_a: jdouble,
    local_z_a: jdouble,
    local_q_x_a: jdouble,
    local_q_y_a: jdouble,
    local_q_z_a: jdouble,
    local_q_w_a: jdouble,
    local_x_b: jdouble,
    local_y_b: jdouble,
    local_z_b: jdouble,
    local_q_x_b: jdouble,
    local_q_y_b: jdouble,
    local_q_z_b: jdouble,
    local_q_w_b: jdouble,
    locked_axes_mask: jint,
) -> jlong {
    tracked_call!(add_generic_constraint(
        scene_id,
        id_a,
        id_b,
        local_x_a,
        local_y_a,
        local_z_a,
        local_q_x_a,
        local_q_y_a,
        local_q_z_a,
        local_q_w_a,
        local_x_b,
        local_y_b,
        local_z_b,
        local_q_x_b,
        local_q_y_b,
        local_q_z_b,
        local_q_w_b,
        locked_axes_mask,
    ))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_setConstraintFrame<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    joint_id: jlong,
    side: jint,
    local_x: jdouble,
    local_y: jdouble,
    local_z: jdouble,
    local_q_x: jdouble,
    local_q_y: jdouble,
    local_q_z: jdouble,
    local_q_w: jdouble,
) {
    tracked_call!(set_constraint_frame(
        scene_id, joint_id, side, local_x, local_y, local_z, local_q_x, local_q_y, local_q_z,
        local_q_w,
    ));
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
    let vm = unsafe { JavaVM::from_raw(env.get_java_vm().unwrap().get_java_vm_pointer()).unwrap() };
    tracked_call!(initialize(Some(vm), scene_id, x, y, z, universal_drag));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_tick<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    _time_step: jdouble,
) {
    tracked_call!(tick(scene_id));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_step<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    time_step: jdouble,
) {
    tracked_call!(step(scene_id, time_step));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_getPose<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id: jint,
    store: JDoubleArray<'local>,
) {
    let arr: [jdouble; 7] = tracked_call!(get_pose(scene_id, id));
    env.set_double_array_region(&store, 0, &arr).unwrap();
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
    tracked_call!(set_center_of_mass(scene_id, id, x, y, z));
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
    tracked_call!(set_local_bounds(
        scene_id, id, min_x, min_y, min_z, max_x, max_y, max_z
    ));
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
    let pose_arr = extract_jdouble_array!(env, pose, 7);
    tracked_call!(create_sub_level(scene_id, id, pose_arr));
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
    tracked_call!(remove_sub_level(scene_id, id));
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
    let mut buf = Box::<[i32; 4096]>::new_uninit();
    env.get_int_array_region(data, 0, unsafe {
        slice::from_raw_parts_mut((*buf).as_mut_ptr().cast(), 4096)
    })
    .expect("to copy int array");
    let buf = unsafe { buf.assume_init() };
    tracked_call!(add_chunk(scene_id, x, y, z, buf, global, object_id));
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
    tracked_call!(remove_chunk(scene_id, x, y, z, global));
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
    tracked_call!(change_block(scene_id, x, y, z, block));
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
    let com = extract_jdouble_array!(env, center_of_mass, 3);
    let inertia_arr = extract_jdouble_array!(env, inertia, 9);

    tracked_call!(set_mass_properties(scene_id, id, mass, com, inertia_arr));
}

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
    tracked_call!(teleport_object(scene_id, id, x, y, z, i, j, k, r));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_wakeUpObject<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id: jint,
) {
    tracked_call!(wake_up_object(scene_id, id));
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
    tracked_call!(add_linear_velocities(
        scene_id, id, linear_x, linear_y, linear_z, angular_x, angular_y, angular_z, wake_up,
    ));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_clearCollisions<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
) -> JDoubleArray<'local> {
    let arr = tracked_call!(clear_collisions(scene_id));
    let double_array = _env.new_double_array(arr.len() as jint).unwrap();
    _env.set_double_array_region(&double_array, 0, &arr)
        .unwrap();

    double_array
}

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
    tracked_call!(apply_force(scene_id, id, x, y, z, fx, fy, fz, wake_up));
}

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
    tracked_call!(apply_force_and_torque(
        scene_id, id, fx, fy, fz, tx, ty, tz, wake_up
    ));
}

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
    _env.set_double_array_region(&store, 0, &tracked_call!(get_linear_velocity(scene_id, id)))
        .unwrap();
}

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
    _env.set_double_array_region(
        &store,
        0,
        &tracked_call!(get_angular_velocity(scene_id, id)),
    )
    .unwrap();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_createRope<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    point_radius: jdouble,
    first_joint_length: jdouble,
    points: JDoubleArray<'local>,
    num_points: jint,
) -> jlong {
    let mut coordinates = vec![0.0; (num_points * 3) as usize];
    env.get_double_array_region(points, 0, &mut coordinates)
        .unwrap();
    tracked_call!(create_rope(
        scene_id,
        point_radius,
        first_joint_length,
        coordinates,
        num_points,
    ))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_queryRope<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id: jlong,
) -> JDoubleArray<'local> {
    let flattened = tracked_call!(query_rope(scene_id, id));
    let double_array = env.new_double_array((flattened.len()) as jsize).unwrap();
    env.set_double_array_region(&double_array, 0, &flattened)
        .unwrap();
    double_array
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_removeRope<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id: jlong,
) {
    tracked_call!(remove_rope(scene_id, id));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_setRopeFirstSegmentLength<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id: jlong,
    length: jdouble,
) {
    tracked_call!(set_rope_first_segment_length(scene_id, id, length));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_removeRopePointAtStart<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id: jlong,
) {
    tracked_call!(remove_rope_point_at_start(scene_id, id));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_addRopePointAtStart<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    id: jlong,
    x: jdouble,
    y: jdouble,
    z: jdouble,
) {
    tracked_call!(add_rope_point_at_start(scene_id, id, x, y, z));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_wakeUpRope<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    rope_id: jlong,
) {
    tracked_call!(wake_up_rope(scene_id, rope_id));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_setRopeAttachment<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    rope_id: jlong,
    sub_level_id: jint,
    x: jdouble,
    y: jdouble,
    z: jdouble,
    end: jboolean,
) {
    tracked_call!(set_rope_attachment(
        scene_id,
        rope_id,
        sub_level_id,
        x,
        y,
        z,
        end
    ));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_newVoxelCollider<
    'local,
>(
    mut env: JNIEnv<'static>,
    _class: JClass<'local>,
    friction: jdouble,
    volume: jdouble,
    restitution: jdouble,
    is_fluid: jboolean,
    contact_events: JObject,
    dynamic: jboolean,
) -> jint {
    let global_ref = if contact_events.is_null() {
        None
    } else {
        Some(env.new_global_ref(contact_events).unwrap())
    };

    let global_method = if let Some(global_ref_value) = &global_ref {
        let class = env.get_object_class(global_ref_value).unwrap();

        let id = SableMethodID(
            env.get_method_id(
                class,
                String::from("onCollision"),
                String::from("(IIIDDDD)[D"),
            )
            .unwrap(),
        );
        Some(id)
    } else {
        None
    };
    tracked_call!(new_voxel_collider(
        friction,
        volume,
        restitution,
        is_fluid,
        dynamic,
        global_ref,
        global_method,
    ))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_addVoxelColliderBox<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    index: jint,
    box_bounds: JDoubleArray<'local>,
) {
    let bounds = extract_jdouble_array!(env, box_bounds, 6);
    tracked_call!(add_voxel_collider_box(index, bounds));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_clearVoxelColliderBoxes<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    index: jint,
) {
    tracked_call!(clear_voxel_collider_boxes(index));
}
#[cfg(feature = "recording")]
#[ctor::ctor]
pub fn ctor() {
    fisher::setup_trace();
}
#[cfg(feature = "recording")]
#[ctor::dtor]
pub fn dtor() {
    fisher::finish_trace();
}

use crate::config::{JOINT_SPRING_DAMPING_RATIO, JOINT_SPRING_FREQUENCY};
use crate::scene::LevelColliderID;
use crate::{get_scene_mut_ref, get_scene_ref};
use jni::JNIEnv;
use jni::objects::{JClass, JDoubleArray};
use jni::sys::{jboolean, jdouble, jint, jlong};
use marten::Real;
use rapier3d::dynamics::{
    GenericJointBuilder, JointAxesMask, JointAxis, RevoluteJointBuilder, SpringCoefficients,
};
use rapier3d::glamx::{DQuat, DVec3, Quat};
use rapier3d::math::Vec3;
use rapier3d::prelude::{FixedJointBuilder, ImpulseJointHandle};
use std::collections::HashMap;

type SableJointHandle = jlong;
type RapierJointHandle = ImpulseJointHandle;

struct SubLevelJoint {
    id_a: Option<LevelColliderID>,
    id_b: Option<LevelColliderID>,

    pos_a: DVec3,
    pos_b: DVec3,
    normal_a: DVec3,
    normal_b: DVec3,

    rotation_a: Option<Quat>,
    rotation_b: Option<Quat>,

    handle: RapierJointHandle,

    fixed: bool,
    contacts_enabled: bool,
}
#[derive(Default)]
pub struct SableJointSet {
    joints: HashMap<SableJointHandle, SubLevelJoint>,
}

pub fn tick(scene_id: jint) {
    let scene = get_scene_mut_ref(scene_id);
    // filter the joints
    scene
        .joint_set
        .joints
        .retain(|_handle, joint| scene.impulse_joint_set.contains(joint.handle));
    // update every joint
    for (_handle, joint) in scene.joint_set.joints.iter_mut() {
        let impulse_joint = scene
            .impulse_joint_set
            .get_mut(joint.handle, false)
            .unwrap();
        impulse_joint.data.contacts_enabled = joint.contacts_enabled;
        if !joint.fixed && joint.rotation_a.is_none() {
            impulse_joint.data.set_local_axis1(joint.normal_a.as_vec3());
        }
        let local_anchor_1 = joint.pos_a
            - joint
                .id_a
                .map(|v| scene.level_colliders[&v].center_of_mass.unwrap())
                .unwrap_or_default();

        impulse_joint
            .data
            .set_local_anchor1(local_anchor_1.as_vec3());

        if !joint.fixed && joint.rotation_b.is_none() {
            impulse_joint.data.set_local_axis2(joint.normal_b.as_vec3());
        }
        let local_anchor_2 = joint.pos_b
            - joint
                .id_b
                .map(|v| scene.level_colliders[&v].center_of_mass.unwrap())
                .unwrap_or_default();

        impulse_joint
            .data
            .set_local_anchor2(local_anchor_2.as_vec3());

        if let Some(rotation_a) = joint.rotation_a {
            impulse_joint.data.local_frame1.rotation = rotation_a;
        }
        if let Some(rotation_b) = joint.rotation_b {
            impulse_joint.data.local_frame2.rotation = rotation_b;
        }
    }
}

const AXES: [JointAxis; 6] = [
    JointAxis::LinX,
    JointAxis::LinY,
    JointAxis::LinZ,
    JointAxis::AngX,
    JointAxis::AngY,
    JointAxis::AngZ,
];

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
    let scene = get_scene_mut_ref(scene_id);
    let Some(joint) = scene.joint_set.joints.get(&joint_id) else {
        return;
    };

    let data = &mut scene
        .impulse_joint_set
        .get_mut(joint.handle, false)
        .unwrap()
        .data;
    data.set_motor_position(
        AXES[axis as usize],
        target_pos as Real,
        stiffness as Real,
        damping as Real,
    );

    if has_max_force > 0 {
        data.motors[axis as usize].max_force = max_force as Real
    }
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
    let scene = get_scene_ref(scene_id);
    if scene.joint_set.joints.contains_key(&joint_id) {
        1
    } else {
        0
    }
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
    let scene = get_scene_ref(scene_id);
    let joint = scene.joint_set.joints.get(&joint_id).unwrap();
    let impulse_joint = scene.impulse_joint_set.get(joint.handle).unwrap();
    let impulses = impulse_joint.impulses;

    let arr: [jdouble; 6] = [
        impulses[0] as jdouble,
        impulses[1] as jdouble,
        impulses[2] as jdouble,
        impulses[3] as jdouble,
        impulses[4] as jdouble,
        impulses[5] as jdouble,
    ];

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
    let scene = get_scene_mut_ref(scene_id);
    let Some(joint) = scene.joint_set.joints.get_mut(&joint_id) else {
        return;
    };

    joint.contacts_enabled = enabled > 0;
}

// removes a constraint
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_removeConstraint<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    scene_id: jint,
    joint_id: jlong,
) {
    let scene = get_scene_mut_ref(scene_id);
    if let Some(joint) = scene.joint_set.joints.remove(&joint_id) {
        scene.impulse_joint_set.remove(joint.handle, true);
    }
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
) -> SableJointHandle {
    let scene = get_scene_mut_ref(scene_id);

    let rb_a = if id_a == -1 {
        scene.ground_handle.unwrap()
    } else {
        scene.rigid_bodies[&(id_a as LevelColliderID)]
    };

    let rb_b = if id_b == -1 {
        scene.ground_handle.unwrap()
    } else {
        scene.rigid_bodies[&(id_b as LevelColliderID)]
    };

    let revolute = RevoluteJointBuilder::new(
        DVec3::new(axis_x_a, axis_y_a, axis_z_a)
            .as_vec3()
            .normalize(),
    )
    .local_anchor1(Vec3::ZERO)
    .local_anchor2(Vec3::ZERO)
    .softness(SpringCoefficients::new(
        JOINT_SPRING_FREQUENCY,
        JOINT_SPRING_DAMPING_RATIO,
    ));

    let handle = scene
        .impulse_joint_set
        .insert(rb_a, rb_b, revolute.build(), true);

    let (index, generation) = handle.0.into_raw_parts();
    let handle_long: SableJointHandle = index as jlong | (generation as jlong) << 32;

    scene.joint_set.joints.insert(
        handle_long,
        SubLevelJoint {
            id_a: if id_a == -1 {
                None
            } else {
                Some(id_a as LevelColliderID)
            },
            id_b: if id_b == -1 {
                None
            } else {
                Some(id_b as LevelColliderID)
            },

            pos_a: DVec3::new(local_x_a, local_y_a, local_z_a),
            pos_b: DVec3::new(local_x_b, local_y_b, local_z_b),

            normal_a: DVec3::new(axis_x_a, axis_y_a, axis_z_a),
            normal_b: DVec3::new(axis_x_b, axis_y_b, axis_z_b),

            rotation_a: None,
            rotation_b: None,

            handle,

            fixed: false,
            contacts_enabled: true,
        },
    );

    handle_long
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
) -> SableJointHandle {
    let scene = get_scene_mut_ref(scene_id);

    let rb_a = if id_a == -1 {
        scene.ground_handle.unwrap()
    } else {
        scene.rigid_bodies[&(id_a as LevelColliderID)]
    };

    let rb_b = if id_b == -1 {
        scene.ground_handle.unwrap()
    } else {
        scene.rigid_bodies[&(id_b as LevelColliderID)]
    };

    let quat = DQuat::from_xyzw(local_q_x, local_q_y, local_q_z, local_q_w).as_quat();
    let mut revolute = FixedJointBuilder::new()
        .local_anchor1(Vec3::ZERO)
        .local_anchor2(Vec3::ZERO)
        .softness(SpringCoefficients::new(
            JOINT_SPRING_FREQUENCY,
            JOINT_SPRING_DAMPING_RATIO,
        ));
    revolute.0.data.local_frame1.rotation = quat;

    let handle = scene
        .impulse_joint_set
        .insert(rb_a, rb_b, revolute.build(), true);

    let (index, generation) = handle.0.into_raw_parts();
    let handle_long: SableJointHandle = index as jlong | (generation as jlong) << 32;

    scene.joint_set.joints.insert(
        handle_long,
        SubLevelJoint {
            id_a: if id_a == -1 {
                None
            } else {
                Some(id_a as LevelColliderID)
            },
            id_b: if id_b == -1 {
                None
            } else {
                Some(id_b as LevelColliderID)
            },

            pos_a: DVec3::new(local_x_a, local_y_a, local_z_a),
            pos_b: DVec3::new(local_x_b, local_y_b, local_z_b),

            normal_a: DVec3::ZERO,
            normal_b: DVec3::ZERO,

            rotation_a: None,
            rotation_b: None,

            handle,

            fixed: true,
            contacts_enabled: false,
        },
    );

    handle_long
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
) -> SableJointHandle {
    let scene = get_scene_mut_ref(scene_id);

    let rb_a = if id_a == -1 {
        scene.ground_handle.unwrap()
    } else {
        scene.rigid_bodies[&(id_a as LevelColliderID)]
    };

    let rb_b = if id_b == -1 {
        scene.ground_handle.unwrap()
    } else {
        scene.rigid_bodies[&(id_b as LevelColliderID)]
    };

    let mut joint = GenericJointBuilder::new(JointAxesMask::empty()).softness(
        SpringCoefficients::new(JOINT_SPRING_FREQUENCY, JOINT_SPRING_DAMPING_RATIO),
    );

    joint.0.local_frame1.rotation =
        DQuat::from_xyzw(local_q_x, local_q_y, local_q_z, local_q_w).as_quat();

    let handle = scene
        .impulse_joint_set
        .insert(rb_a, rb_b, joint.build(), true);

    let (index, generation) = handle.0.into_raw_parts();
    let handle_long: SableJointHandle = index as jlong | (generation as jlong) << 32;

    scene.joint_set.joints.insert(
        handle_long,
        SubLevelJoint {
            id_a: if id_a == -1 {
                None
            } else {
                Some(id_a as LevelColliderID)
            },
            id_b: if id_b == -1 {
                None
            } else {
                Some(id_b as LevelColliderID)
            },

            pos_a: DVec3::new(local_x_a, local_y_a, local_z_a),
            pos_b: DVec3::new(local_x_b, local_y_b, local_z_b),

            normal_a: DVec3::ZERO,
            normal_b: DVec3::ZERO,

            rotation_a: None,
            rotation_b: None,

            handle,

            fixed: true,
            contacts_enabled: true,
        },
    );

    handle_long
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
) -> SableJointHandle {
    let scene = get_scene_mut_ref(scene_id);

    let rb_a = if id_a == -1 {
        scene.ground_handle.unwrap()
    } else {
        scene.rigid_bodies[&(id_a as LevelColliderID)]
    };

    let rb_b = if id_b == -1 {
        scene.ground_handle.unwrap()
    } else {
        scene.rigid_bodies[&(id_b as LevelColliderID)]
    };

    let locked_axes = JointAxesMask::from_bits_truncate(locked_axes_mask as u8);

    let rotation_a = DQuat::from_xyzw(local_q_x_a, local_q_y_a, local_q_z_a, local_q_w_a).as_quat();
    let rotation_b = DQuat::from_xyzw(local_q_x_b, local_q_y_b, local_q_z_b, local_q_w_b).as_quat();

    let mut joint = GenericJointBuilder::new(locked_axes).softness(SpringCoefficients::new(
        JOINT_SPRING_FREQUENCY,
        JOINT_SPRING_DAMPING_RATIO,
    ));
    joint.0.local_frame1.rotation = rotation_a;
    joint.0.local_frame2.rotation = rotation_b;

    let handle = scene
        .impulse_joint_set
        .insert(rb_a, rb_b, joint.build(), true);

    let (index, generation) = handle.0.into_raw_parts();
    let handle_long: SableJointHandle = index as jlong | (generation as jlong) << 32;

    scene.joint_set.joints.insert(
        handle_long,
        SubLevelJoint {
            id_a: if id_a == -1 {
                None
            } else {
                Some(id_a as LevelColliderID)
            },
            id_b: if id_b == -1 {
                None
            } else {
                Some(id_b as LevelColliderID)
            },

            pos_a: DVec3::new(local_x_a, local_y_a, local_z_a),
            pos_b: DVec3::new(local_x_b, local_y_b, local_z_b),

            normal_a: DVec3::ZERO,
            normal_b: DVec3::ZERO,

            rotation_a: Some(rotation_a),
            rotation_b: Some(rotation_b),

            handle,

            fixed: true,
            contacts_enabled: true,
        },
    );

    handle_long
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
    let scene = get_scene_mut_ref(scene_id);
    let Some(joint) = scene.joint_set.joints.get_mut(&joint_id) else {
        return;
    };

    let position = DVec3::new(local_x, local_y, local_z);
    let rotation = DQuat::from_xyzw(local_q_x, local_q_y, local_q_z, local_q_w).as_quat();

    match side {
        0 => {
            joint.pos_a = position;
            joint.rotation_a = Some(rotation);
        }
        1 => {
            joint.pos_b = position;
            joint.rotation_b = Some(rotation);
        }
        _ => panic!("Invalid constraint frame side: {}", side),
    }
}

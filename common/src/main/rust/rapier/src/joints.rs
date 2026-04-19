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
use rapier3d::glamx::Quat;
use rapier3d::math::Vector;
use rapier3d::na::Vector3;
use rapier3d::prelude::{FixedJointBuilder, ImpulseJointHandle};
use std::collections::HashMap;

type SableJointHandle = jlong;
type RapierJointHandle = ImpulseJointHandle;

struct SubLevelJoint {
    id_a: Option<LevelColliderID>,
    id_b: Option<LevelColliderID>,

    pos_a: Vector3<f64>,
    pos_b: Vector3<f64>,
    normal_a: Vector3<f64>,
    normal_b: Vector3<f64>,

    handle: RapierJointHandle,

    fixed: bool,
    contacts_enabled: bool,
}

pub struct SableJointSet {
    joints: HashMap<SableJointHandle, SubLevelJoint>,
}

impl SableJointSet {
    #[must_use]
    pub fn new() -> Self {
        Self {
            joints: HashMap::new(),
        }
    }
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
        if !joint.fixed {
            impulse_joint.data.set_local_axis1(Vector::new(
                joint.normal_a.x as Real,
                joint.normal_a.y as Real,
                joint.normal_a.z as Real,
            ));
        }
        let local_anchor_1 = joint.pos_a
            - if let Some(id_a) = joint.id_a {
                let rb_a = &scene.level_colliders[&id_a];
                rb_a.center_of_mass.unwrap()
            } else {
                Vector3::new(0.0, 0.0, 0.0)
            };
        impulse_joint.data.set_local_anchor1(Vector::new(
            local_anchor_1.x as Real,
            local_anchor_1.y as Real,
            local_anchor_1.z as Real,
        ));
        if !joint.fixed {
            impulse_joint.data.set_local_axis2(Vector::new(
                joint.normal_b.x as Real,
                joint.normal_b.y as Real,
                joint.normal_b.z as Real,
            ));
        }
        let local_anchor_2 = joint.pos_b
            - if let Some(id_b) = joint.id_b {
                let rb_b = &scene.level_colliders[&id_b];
                rb_b.center_of_mass.unwrap()
            } else {
                Vector3::new(0.0, 0.0, 0.0)
            };
        impulse_joint.data.set_local_anchor2(Vector::new(
            local_anchor_2.x as Real,
            local_anchor_2.y as Real,
            local_anchor_2.z as Real,
        ));
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
        Vector::new(axis_x_a as Real, axis_y_a as Real, axis_z_a as Real).normalize(),
    )
    .local_anchor1(Vector::ZERO)
    .local_anchor2(Vector::ZERO)
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

            pos_a: Vector3::new(local_x_a, local_y_a, local_z_a),
            pos_b: Vector3::new(local_x_b, local_y_b, local_z_b),

            normal_a: Vector3::new(axis_x_a, axis_y_a, axis_z_a),
            normal_b: Vector3::new(axis_x_b, axis_y_b, axis_z_b),

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

    let quat = Quat::from_xyzw(
        local_q_x as Real,
        local_q_y as Real,
        local_q_z as Real,
        local_q_w as Real,
    );
    let mut revolute = FixedJointBuilder::new()
        .local_anchor1(Vector::ZERO)
        .local_anchor2(Vector::ZERO)
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

            pos_a: Vector3::new(local_x_a, local_y_a, local_z_a),
            pos_b: Vector3::new(local_x_b, local_y_b, local_z_b),

            normal_a: Vector3::new(0.0, 0.0, 0.0),
            normal_b: Vector3::new(0.0, 0.0, 0.0),

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

    let quat = Quat::from_xyzw(
        local_q_x as Real,
        local_q_y as Real,
        local_q_z as Real,
        local_q_w as Real,
    );
    joint.0.local_frame1.rotation = quat;

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

            pos_a: Vector3::new(local_x_a, local_y_a, local_z_a),
            pos_b: Vector3::new(local_x_b, local_y_b, local_z_b),

            normal_a: Vector3::new(0.0, 0.0, 0.0),
            normal_b: Vector3::new(0.0, 0.0, 0.0),

            handle,

            fixed: true,
            contacts_enabled: true,
        },
    );

    handle_long
}

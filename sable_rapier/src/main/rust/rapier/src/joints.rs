use crate::config::{JOINT_SPRING_DAMPING_RATIO, JOINT_SPRING_FREQUENCY};
use crate::scene::{LevelColliderID, PhysicsScene, SableSceneData};
use crate::with_handle;
use jni::JNIEnv;
use jni::objects::{JClass, JDoubleArray};
use jni::sys::{jboolean, jbyte, jdouble, jint, jlong};
use marten::Real;
use rapier3d::dynamics::{
    GenericJointBuilder, JointAxesMask, JointAxis, RevoluteJointBuilder, SpringCoefficients,
};
use rapier3d::glamx::{DVec3, Quat};
use rapier3d::math::Vec3;
use rapier3d::prelude::{FixedJointBuilder, ImpulseJointHandle, RigidBodyHandle};
use std::collections::HashMap;

type SableJointHandle = jlong;
type RapierJointHandle = ImpulseJointHandle;

fn get_rb(
    sable_data: &SableSceneData,
    ground_handle: RigidBodyHandle,
    id: jint,
) -> Option<RigidBodyHandle> {
    if id == -1 {
        Some(ground_handle)
    } else {
        sable_data.rigid_bodies.get(&(id as LevelColliderID)).copied()
    }
}


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

pub fn tick(scene: &PhysicsScene) {
    let mut sable_data = scene.sable_data.write().unwrap();
    let mut sim = scene.sim_data.write().unwrap();

    // filter the joints
    sable_data
        .joint_set
        .joints
        .retain(|_handle, joint| sim.impulse_joint_set.contains(joint.handle));

    // update every joint
    for (_handle, joint) in sable_data.joint_set.joints.iter() {
        let impulse_joint = sim.impulse_joint_set.get_mut(joint.handle, false).unwrap();
        impulse_joint.data.contacts_enabled = joint.contacts_enabled;
        if !joint.fixed && joint.rotation_a.is_none() {
            impulse_joint.data.set_local_axis1(joint.normal_a.as_vec3());
        }

        let center_of_mass_1 = if let Some(id_a) = joint.id_a
            && let Some(rb_a) = sable_data.level_colliders.get(&id_a)
        {
            rb_a.center_of_mass.unwrap()
        } else {
            DVec3::ZERO
        };

        let local_anchor_1 = joint.pos_a - center_of_mass_1;
        impulse_joint
            .data
            .set_local_anchor1(local_anchor_1.as_vec3());
        if !joint.fixed && joint.rotation_b.is_none() {
            impulse_joint.data.set_local_axis2(joint.normal_b.as_vec3());
        }

        let center_of_mass_2 = if let Some(id_b) = joint.id_b
            && let Some(rb_b) = sable_data.level_colliders.get(&id_b)
        {
            rb_b.center_of_mass.unwrap()
        } else {
            DVec3::ZERO
        };

        let local_anchor_2 = joint.pos_b - center_of_mass_2;
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
    handle: jlong,
    joint_id: jlong,
    axis: jint,
    target_pos: jdouble,
    stiffness: jdouble,
    damping: jdouble,
    has_max_force: jboolean,
    max_force: jdouble,
) {
    with_handle(handle, |scene| {
        let sable_data = scene.sable_data.read().unwrap();
        let mut sim_data = scene.sim_data.write().unwrap();

        let Some(joint) = sable_data.joint_set.joints.get(&joint_id) else {
            return;
        };

        let data = &mut sim_data
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
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_setConstraintLimit<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    joint_id: jlong,
    axis: jint,
    min: jdouble,
    max: jdouble,
) {
    with_handle(handle, |scene| {
        let sable_data = scene.sable_data.read().unwrap();
        let mut sim_data = scene.sim_data.write().unwrap();

        let Some(joint) = sable_data.joint_set.joints.get(&joint_id) else {
            return;
        };

        let data = &mut sim_data
            .impulse_joint_set
            .get_mut(joint.handle, false)
            .unwrap()
            .data;

        data.set_limits(AXES[axis as usize], [min as Real, max as Real]);
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_lockConstraintAxes<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    joint_id: jlong,
    mask: jbyte,
) {
    with_handle(handle, |scene| {
        let sable_data = scene.sable_data.read().unwrap();
        let mut sim_data = scene.sim_data.write().unwrap();

        let Some(joint) = sable_data.joint_set.joints.get(&joint_id) else {
            return;
        };

        let data = &mut sim_data
            .impulse_joint_set
            .get_mut(joint.handle, false)
            .unwrap()
            .data;

        data.lock_axes(JointAxesMask::from_bits(mask as u8).expect("Invalid mask!"));
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_isConstraintValid<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    joint_id: jlong,
) -> jboolean {
    with_handle(handle, |scene| {
        let sable_data = scene.sable_data.read().unwrap();
        if sable_data.joint_set.joints.contains_key(&joint_id) {
            1
        } else {
            0
        }
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_getConstraintImpulses<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    joint_id: jlong,
    store: JDoubleArray<'local>,
) {
    with_handle(handle, |scene| {
        let sable_data = scene.sable_data.read().unwrap();
        let sim_data = scene.sim_data.read().unwrap();

        let joint = sable_data.joint_set.joints.get(&joint_id).unwrap();
        let impulse_joint = sim_data.impulse_joint_set.get(joint.handle).unwrap();
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
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_setConstraintContactsEnabled<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    joint_id: jlong,
    enabled: jboolean,
) {
    with_handle(handle, |scene| {
        let mut sable_data = scene.sable_data.write().unwrap();
        let Some(joint) = sable_data.joint_set.joints.get_mut(&joint_id) else {
            return;
        };

        joint.contacts_enabled = enabled > 0;
    })
}

// removes a constraint
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_removeConstraint<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    joint_id: jlong,
) {
    with_handle(handle, |scene| {
        let mut sable_data = scene.sable_data.write().unwrap();
        let mut sim_data = scene.sim_data.write().unwrap();
        if let Some(joint) = sable_data.joint_set.joints.remove(&joint_id) {
            sim_data.impulse_joint_set.remove(joint.handle, true);
        }
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_addRotaryConstraint<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
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
    with_handle(handle, |scene| {
        let mut sable_data = scene.sable_data.write().unwrap();
        let mut sim_data = scene.sim_data.write().unwrap();

        let rb_a = match get_rb(&sable_data, scene.ground_handle.unwrap(), id_a) {
            Some(rb) => rb,
            None => return -1,
        };

        let rb_b = match get_rb(&sable_data, scene.ground_handle.unwrap(), id_b) {
            Some(rb) => rb,
            None => return -1,
        };

        let revolute = RevoluteJointBuilder::new(
            Vec3::new(axis_x_a as Real, axis_y_a as Real, axis_z_a as Real).normalize(),
        )
        .local_anchor1(Vec3::ZERO)
        .local_anchor2(Vec3::ZERO)
        .softness(SpringCoefficients::new(
            JOINT_SPRING_FREQUENCY,
            JOINT_SPRING_DAMPING_RATIO,
        ));

        let handle = sim_data
            .impulse_joint_set
            .insert(rb_a, rb_b, revolute.build(), true);

        let (index, generation) = handle.0.into_raw_parts();
        let handle_long: SableJointHandle = index as jlong | (generation as jlong) << 32;

        sable_data.joint_set.joints.insert(
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
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_addFixedConstraint<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
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
    with_handle(handle, |scene| {
        let mut sable_data = scene.sable_data.write().unwrap();
        let mut sim_data = scene.sim_data.write().unwrap();

        let rb_a = match get_rb(&sable_data, scene.ground_handle.unwrap(), id_a) {
            Some(rb) => rb,
            None => return -1,
        };

        let rb_b = match get_rb(&sable_data, scene.ground_handle.unwrap(), id_b) {
            Some(rb) => rb,
            None => return -1,
        };

        let quat = Quat::from_xyzw(
            local_q_x as Real,
            local_q_y as Real,
            local_q_z as Real,
            local_q_w as Real,
        );
        let mut revolute = FixedJointBuilder::new()
            .local_anchor1(Vec3::ZERO)
            .local_anchor2(Vec3::ZERO)
            .softness(SpringCoefficients::new(
                JOINT_SPRING_FREQUENCY,
                JOINT_SPRING_DAMPING_RATIO,
            ));
        revolute.0.data.local_frame1.rotation = quat;

        let handle = sim_data
            .impulse_joint_set
            .insert(rb_a, rb_b, revolute.build(), true);

        let (index, generation) = handle.0.into_raw_parts();
        let handle_long: SableJointHandle = index as jlong | (generation as jlong) << 32;

        sable_data.joint_set.joints.insert(
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

                normal_a: DVec3::new(0.0, 0.0, 0.0),
                normal_b: DVec3::new(0.0, 0.0, 0.0),

                rotation_a: None,
                rotation_b: None,

                handle,

                fixed: true,
                contacts_enabled: false,
            },
        );

        handle_long
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_addFreeConstraint<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
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
    with_handle(handle, |scene| {
        let mut sable_data = scene.sable_data.write().unwrap();
        let mut sim_data = scene.sim_data.write().unwrap();

        let rb_a = match get_rb(&sable_data, scene.ground_handle.unwrap(), id_a) {
            Some(rb) => rb,
            None => return -1,
        };

        let rb_b = match get_rb(&sable_data, scene.ground_handle.unwrap(), id_b) {
            Some(rb) => rb,
            None => return -1,
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

        let handle = sim_data
            .impulse_joint_set
            .insert(rb_a, rb_b, joint.build(), true);

        let (index, generation) = handle.0.into_raw_parts();
        let handle_long: SableJointHandle = index as jlong | (generation as jlong) << 32;

        sable_data.joint_set.joints.insert(
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

                normal_a: DVec3::new(0.0, 0.0, 0.0),
                normal_b: DVec3::new(0.0, 0.0, 0.0),

                rotation_a: None,
                rotation_b: None,

                handle,

                fixed: true,
                contacts_enabled: true,
            },
        );

        handle_long
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_addGenericConstraint<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
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
    with_handle(handle, |scene| {
        let mut sable_data = scene.sable_data.write().unwrap();
        let mut sim_data = scene.sim_data.write().unwrap();

        let rb_a = match get_rb(&sable_data, scene.ground_handle.unwrap(), id_a) {
            Some(rb) => rb,
            None => return -1,
        };

        let rb_b = match get_rb(&sable_data, scene.ground_handle.unwrap(), id_b) {
            Some(rb) => rb,
            None => return -1,
        };

        let locked_axes = JointAxesMask::from_bits_truncate(locked_axes_mask as u8);

        let rotation_a = Quat::from_xyzw(
            local_q_x_a as Real,
            local_q_y_a as Real,
            local_q_z_a as Real,
            local_q_w_a as Real,
        );
        let rotation_b = Quat::from_xyzw(
            local_q_x_b as Real,
            local_q_y_b as Real,
            local_q_z_b as Real,
            local_q_w_b as Real,
        );

        let mut joint = GenericJointBuilder::new(locked_axes).softness(SpringCoefficients::new(
            JOINT_SPRING_FREQUENCY,
            JOINT_SPRING_DAMPING_RATIO,
        ));
        joint.0.local_frame1.rotation = rotation_a;
        joint.0.local_frame2.rotation = rotation_b;

        let handle = sim_data
            .impulse_joint_set
            .insert(rb_a, rb_b, joint.build(), true);

        let (index, generation) = handle.0.into_raw_parts();
        let handle_long: SableJointHandle = index as jlong | (generation as jlong) << 32;

        sable_data.joint_set.joints.insert(
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

                pos_a: DVec3::new(local_x_a as f64, local_y_a as f64, local_z_a as f64),
                pos_b: DVec3::new(local_x_b as f64, local_y_b as f64, local_z_b as f64),

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
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_setConstraintFrame<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
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
    with_handle(handle, |scene| {
        let mut sable_data = scene.sable_data.write().unwrap();
        let Some(joint) = sable_data.joint_set.joints.get_mut(&joint_id) else {
            return;
        };

        let position = DVec3::new(local_x as f64, local_y as f64, local_z as f64);
        let rotation = Quat::from_xyzw(
            local_q_x as Real,
            local_q_y as Real,
            local_q_z as Real,
            local_q_w as Real,
        );

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
    })
}

use std::collections::HashMap;

use jni::JNIEnv;
use jni::objects::{JClass, JDoubleArray};
use jni::sys::{jboolean, jdouble, jint, jlong, jsize};
use marten::Real;
use rapier3d::dynamics::{GenericJointBuilder, JointAxis, RigidBodyBuilder, SpringCoefficients};
use rapier3d::geometry::{ColliderBuilder, SharedShape};
use rapier3d::glamx::DVec3;
use rapier3d::math::Vec3;
use rapier3d::prelude::{
    ImpulseJointHandle, ImpulseJointSet, JointAxesMask, RigidBodyHandle, RopeJointBuilder,
};

use crate::config::{JOINT_SPRING_DAMPING_RATIO, JOINT_SPRING_FREQUENCY};
use crate::groups::ROPE_GROUP;
use crate::scene::{LevelColliderID, PhysicsScene, SableSceneData, SimulationSceneData};
use crate::with_handle;

const MIN_BOUND_STIFFNESS: Real = 150.0;
const MIN_BOUND_DAMPING: Real = 10.0;

struct RopeAttachment {
    joint: ImpulseJointHandle,
    sub_level_id: Option<LevelColliderID>,
    location: DVec3,
}

struct RopeStrand {
    points: Vec<RigidBodyHandle>,
    joints: Vec<(ImpulseJointHandle, ImpulseJointHandle)>,

    point_radius: Real,
    first_joint_length: Real,

    start_attachment: Option<RopeAttachment>,
    end_attachment: Option<RopeAttachment>,
}
#[derive(Default)]
pub struct RopeMap {
    counting_id: usize,
    ropes: HashMap<usize, RopeStrand>,
}

pub fn tick(scene: &PhysicsScene) {
    let mut sable_data = scene.sable_data.write().unwrap();
    let mut sim = scene.sim_data.write().unwrap();

    let mut dead_start_attachments = Vec::new();
    let mut dead_end_attachments = Vec::new();

    for (id, rope) in sable_data.rope_map.ropes.iter() {
        if let Some(attachment) = &rope.start_attachment {
            let endpoint_gone = attachment
                .sub_level_id
                .is_some_and(|id_b| !sable_data.level_colliders.contains_key(&id_b));
            if !sim.impulse_joint_set.contains(attachment.joint) || endpoint_gone {
                dead_start_attachments.push(*id);
            } else {
                let local_anchor = attachment.location
                    - if let Some(id_b) = attachment.sub_level_id {
                        let rb_b = &sable_data.level_colliders[&id_b];
                        rb_b.center_of_mass.unwrap()
                    } else {
                        DVec3::ZERO
                    };

                let impulse_joint = sim
                    .impulse_joint_set
                    .get_mut(attachment.joint, false)
                    .unwrap();
                impulse_joint.data.set_local_anchor1(local_anchor.as_vec3());
            }
        }

        if let Some(attachment) = &rope.end_attachment {
            let endpoint_gone = attachment
                .sub_level_id
                .is_some_and(|id_b| !sable_data.level_colliders.contains_key(&id_b));
            if !sim.impulse_joint_set.contains(attachment.joint) || endpoint_gone {
                dead_end_attachments.push(*id);
            } else {
                let local_anchor = attachment.location
                    - if let Some(id_b) = attachment.sub_level_id {
                        let rb_b = &sable_data.level_colliders[&id_b];
                        rb_b.center_of_mass.unwrap()
                    } else {
                        DVec3::ZERO
                    };

                let impulse_joint = sim
                    .impulse_joint_set
                    .get_mut(attachment.joint, false)
                    .unwrap();
                impulse_joint.data.set_local_anchor1(local_anchor.as_vec3());
            }
        }
    }

    for id in dead_start_attachments {
        if let Some(rope) = sable_data.rope_map.ropes.get_mut(&id) {
            rope.start_attachment = None;
        }
    }

    for id in dead_end_attachments {
        if let Some(rope) = sable_data.rope_map.ropes.get_mut(&id) {
            rope.end_attachment = None;
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_createRope<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    point_radius: jdouble,
    first_joint_length: jdouble,
    points: JDoubleArray<'local>,
    num_points: jint,
) -> jlong {
    let mut coordinates = vec![0.0; (num_points * 3) as usize];
    env.get_double_array_region(points, 0, &mut coordinates)
        .unwrap();

    with_handle(handle, |scene| {
        let mut sim_data = scene.sim_data.write().unwrap();
        let mut sable_data = scene.sable_data.write().unwrap();
        let universal_drag = scene.universal_drag;

        let mut vec = Vec::with_capacity(num_points as usize);
        for i in 0..(num_points as usize) {
            let coordinate = Vec3::new(
                coordinates[i * 3] as Real,
                coordinates[i * 3 + 1] as Real,
                coordinates[i * 3 + 2] as Real,
            );

            let handle = create_rope_body(
                &mut sim_data,
                universal_drag,
                coordinate,
                point_radius as Real,
            );

            vec.push(handle);
        }

        let mut joints: Vec<(ImpulseJointHandle, ImpulseJointHandle)> =
            Vec::with_capacity(vec.len() - 1);
        for i in 0..vec.len() - 1 {
            let point_handle_0 = &vec[i];
            let point_handle_1 = &vec[i + 1];

            let length = if i == 0 {
                first_joint_length as Real
            } else {
                1.0
            };
            joints.push(add_rope_joint(
                &mut sim_data.impulse_joint_set,
                point_handle_0,
                point_handle_1,
                length,
            ));
        }

        let strand = RopeStrand {
            points: vec,
            point_radius: point_radius as Real,
            first_joint_length: first_joint_length as Real,
            start_attachment: None,
            end_attachment: None,
            joints,
        };

        sable_data.rope_map.counting_id += 1;
        let id = sable_data.rope_map.counting_id;

        sable_data.rope_map.ropes.insert(id, strand);

        id as jlong
    })
}

fn add_rope_joint(
    impulse_joint_set: &mut ImpulseJointSet,
    point_handle_0: &RigidBodyHandle,
    point_handle_1: &RigidBodyHandle,
    length: Real,
) -> (ImpulseJointHandle, ImpulseJointHandle) {
    let mut joint = RopeJointBuilder::new(length)
        .local_anchor1(Vec3::ZERO)
        .local_anchor2(Vec3::ZERO)
        .softness(SpringCoefficients::new(
            JOINT_SPRING_FREQUENCY,
            JOINT_SPRING_DAMPING_RATIO,
        ));
    joint.0.data.set_limits(JointAxis::LinX, [0.0, length]);
    joint.0.data.set_motor_position(
        JointAxis::LinX,
        length,
        MIN_BOUND_STIFFNESS,
        MIN_BOUND_DAMPING,
    );
    let handle = impulse_joint_set.insert(*point_handle_0, *point_handle_1, joint.build(), true);
    let damp_handle = impulse_joint_set.insert(
        *point_handle_0,
        *point_handle_1,
        GenericJointBuilder::new(JointAxesMask::empty())
            .softness(SpringCoefficients::new(
                JOINT_SPRING_FREQUENCY,
                JOINT_SPRING_DAMPING_RATIO,
            ))
            .build(),
        true,
    );

    let damp_joint = &mut impulse_joint_set.get_mut(damp_handle, false).unwrap().data;

    let damping_strength = 18.0;
    damp_joint.set_motor_velocity(JointAxis::LinX, 0.0, damping_strength);
    damp_joint.set_motor_velocity(JointAxis::LinY, 0.0, damping_strength);
    damp_joint.set_motor_velocity(JointAxis::LinZ, 0.0, damping_strength);

    (handle, damp_handle)
}

fn create_rope_body(
    sim_data: &mut SimulationSceneData,
    universal_drag: Real,
    coordinate: Vec3,
    point_radius: Real,
) -> RigidBodyHandle {
    let mut rigid_body = RigidBodyBuilder::dynamic()
        .translation(coordinate)
        .lock_rotations()
        .build();

    rigid_body.set_linear_damping(universal_drag);
    rigid_body.set_angular_damping(universal_drag);

    let handle = sim_data.rigid_body_set.insert(rigid_body);
    let collider = ColliderBuilder::new(SharedShape::cuboid(
        point_radius as Real,
        point_radius as Real,
        point_radius as Real,
    ))
    .friction(0.15)
    .mass(0.35)
    .collision_groups(ROPE_GROUP)
    .build();

    sim_data
        .collider_set
        .insert_with_parent(collider, handle, &mut sim_data.rigid_body_set);

    handle
}

/// Removes a rope
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_queryRope<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    id: jlong,
) -> JDoubleArray<'local> {
    with_handle(handle, |scene| {
        let sable_data = scene.sable_data.read().unwrap();
        let sim_data = scene.sim_data.read().unwrap();

        let Some(strand) = sable_data.rope_map.ropes.get(&(id as usize)) else {
            return env.new_double_array(0).unwrap();
        };

        let flattened: Vec<jdouble> = strand
            .points
            .iter()
            .flat_map(|x| {
                let pos = sim_data
                    .rigid_body_set
                    .get(*x)
                    .unwrap()
                    .position()
                    .translation;
                vec![pos.x as f64, pos.y as f64, pos.z as f64]
            })
            .collect();

        let double_array = env
            .new_double_array((strand.points.len() * 3) as jsize)
            .unwrap();
        env.set_double_array_region(&double_array, 0, &flattened)
            .unwrap();
        double_array
    })
}

/// Removes a rope
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_removeRope<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    id: jlong,
) {
    with_handle(handle, |scene| {
        let mut sable_data = scene.sable_data.write().unwrap();
        let mut sim_data = scene.sim_data.write().unwrap();
        let sim_data = &mut *sim_data;

        let Some(strand) = sable_data.rope_map.ropes.remove(&(id as usize)) else {
            return;
        };
        for handle in strand.points {
            sim_data.rigid_body_set.remove(
                handle,
                &mut sim_data.island_manager,
                &mut sim_data.collider_set,
                &mut sim_data.impulse_joint_set,
                &mut sim_data.multibody_joint_set,
                true,
            );
        }
    })
}

/// Sets the joint
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_setRopeFirstSegmentLength<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    id: jlong,
    length: jdouble,
) {
    with_handle(handle, |scene| {
        let mut sable_data = scene.sable_data.write().unwrap();
        let mut sim_data = scene.sim_data.write().unwrap();

        let Some(strand) = sable_data.rope_map.ropes.get_mut(&(id as usize)) else {
            return;
        };

        strand.first_joint_length = length as Real;
        let first_joint = &mut sim_data
            .impulse_joint_set
            .get_mut(strand.joints.first().unwrap().0, true)
            .unwrap()
            .data;
        first_joint.set_limits(JointAxis::LinX, [0.0, length as Real]);
        first_joint.set_motor_position(
            JointAxis::LinX,
            length as Real,
            MIN_BOUND_STIFFNESS,
            MIN_BOUND_DAMPING,
        );
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_removeRopePointAtStart<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    id: jlong,
) {
    with_handle(handle, |scene| {
        let mut sable_data = scene.sable_data.write().unwrap();
        let mut sim_data = scene.sim_data.write().unwrap();
        let sim_data = &mut *sim_data;

        let Some(strand) = sable_data.rope_map.ropes.get_mut(&(id as usize)) else {
            return;
        };
        let point = strand.points.remove(0);
        strand.joints.remove(0);
        sim_data.rigid_body_set.remove(
            point,
            &mut sim_data.island_manager,
            &mut sim_data.collider_set,
            &mut sim_data.impulse_joint_set,
            &mut sim_data.multibody_joint_set,
            true,
        );

        let new_first_joint = &mut sim_data
            .impulse_joint_set
            .get_mut(strand.joints.first().unwrap().0, false)
            .unwrap()
            .data;
        new_first_joint.set_limits(JointAxis::LinX, [0.0, strand.first_joint_length]);
        new_first_joint.set_motor_position(
            JointAxis::LinX,
            strand.first_joint_length,
            MIN_BOUND_STIFFNESS,
            MIN_BOUND_DAMPING,
        );

        if strand.start_attachment.is_some() {
            sim_data
                .impulse_joint_set
                .remove(strand.start_attachment.as_ref().unwrap().joint, true);
            strand.start_attachment = None;
        }
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_addRopePointAtStart<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    id: jlong,
    x: jdouble,
    y: jdouble,
    z: jdouble,
) {
    with_handle(handle, |scene| {
        let mut sim_data = scene.sim_data.write().unwrap();
        let mut sable_data = scene.sable_data.write().unwrap();
        let universal_drag = scene.universal_drag;

        let Some(strand) = sable_data.rope_map.ropes.get_mut(&(id as usize)) else {
            return;
        };
        let point_radius = strand.point_radius;

        // set joint that will no longer be the first
        let old_joint = &mut sim_data
            .impulse_joint_set
            .get_mut(strand.joints.first().unwrap().0, false)
            .unwrap()
            .data;
        old_joint.set_limits(JointAxis::LinX, [0.0, 1.0]);
        old_joint.set_motor_position(JointAxis::LinX, 1.0, MIN_BOUND_STIFFNESS, MIN_BOUND_DAMPING);

        let handle = create_rope_body(
            &mut sim_data,
            universal_drag,
            Vec3::new(x as Real, y as Real, z as Real),
            point_radius,
        );
        strand.joints.insert(
            0,
            add_rope_joint(
                &mut sim_data.impulse_joint_set,
                &handle,
                strand.points.first().unwrap(),
                strand.first_joint_length,
            ),
        );
        strand.points.insert(0, handle);

        if strand.start_attachment.is_some() {
            sim_data
                .impulse_joint_set
                .remove(strand.start_attachment.as_ref().unwrap().joint, true);
            strand.start_attachment = None;
        }
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_wakeUpRope<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    rope_id: jlong,
) {
    with_handle(handle, |scene| {
        let sable_data = scene.sable_data.read().unwrap();
        let mut sim_data = scene.sim_data.write().unwrap();

        let Some(strand) = sable_data.rope_map.ropes.get(&(rope_id as usize)) else {
            return;
        };

        for point in &strand.points {
            sim_data
                .rigid_body_set
                .get_mut(*point)
                .unwrap()
                .wake_up(true);
        }
    })
}

/// Sets the attachment at a given end
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_setRopeAttachment<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    rope_id: jlong,
    sub_level_id: jint,
    x: jdouble,
    y: jdouble,
    z: jdouble,
    end: jboolean,
) {
    with_handle(handle, |scene| {
        let ground_handle = scene.ground_handle.unwrap();
        let mut sim_data = scene.sim_data.write().unwrap();
        let mut sable_data = scene.sable_data.write().unwrap();
        let SableSceneData {
            rope_map,
            rigid_bodies,
            ..
        } = &mut *sable_data;

        let Some(strand) = rope_map.ropes.get_mut(&(rope_id as usize)) else {
            return;
        };

        let rope_body = if end > 0 {
            strand.points.last()
        } else {
            strand.points.first()
        }
        .unwrap();
        let sub_level_body = if sub_level_id == -1 {
            ground_handle
        } else {
            let Some(rb) = rigid_bodies.get(&(sub_level_id as LevelColliderID)) else {
                return;
            };
            *rb
        };

        let joint = RopeJointBuilder::new(0.0)
            .local_anchor1(Vec3::ZERO)
            .local_anchor2(Vec3::ZERO)
            .softness(SpringCoefficients::new(
                JOINT_SPRING_FREQUENCY,
                JOINT_SPRING_DAMPING_RATIO,
            ));
        let joint =
            sim_data
                .impulse_joint_set
                .insert(sub_level_body, *rope_body, joint.build(), true);

        if if end > 0 {
            &strand.end_attachment
        } else {
            &strand.start_attachment
        }
        .is_some()
        {
            let attachment = if end > 0 {
                &strand.end_attachment
            } else {
                &strand.start_attachment
            }
            .as_ref()
            .unwrap();
            sim_data.impulse_joint_set.remove(attachment.joint, true);
        }
        let attachment = RopeAttachment {
            sub_level_id: if sub_level_id == -1 {
                None
            } else {
                Some(sub_level_id as LevelColliderID)
            },
            joint,
            location: DVec3::new(x, y, z),
        };

        if end > 0 {
            strand.end_attachment = Some(attachment);
        } else {
            strand.start_attachment = Some(attachment);
        }
    })
}

use jni::objects::{JDoubleArray, JObject, JValue};
use jni::signature::ReturnType;
use jni::sys::{jdouble, jint, jvalue};
use marten::Real;
use marten::level::VoxelColliderData;
use rapier3d::geometry::{Collider, SolverContact};
use rapier3d::math::{Pose, Vec3, Vector};
use rapier3d::na::Vector3;
use rapier3d::pipeline::{ContactModificationContext, PhysicsHooks};

use crate::collider::LevelCollider;
use crate::scene::LevelColliderID;
use crate::{get_physics_state, get_scene_mut, get_scene_ref};

pub struct SablePhysicsHooks;

impl PhysicsHooks for SablePhysicsHooks {
    fn modify_solver_contacts(&self, context: &mut ContactModificationContext) {
        if !VoxelColliderData::needs_hooks(*context.user_data) {
            return;
        }

        let mut remove = false;
        for contact in context.solver_contacts.iter_mut() {
            let Some(collider_a) = context.colliders.get(context.collider1) else {
                panic!("No collider A!");
            };

            let Some(collider_b) = context.colliders.get(context.collider2) else {
                panic!("No collider B!");
            };

            let level_collider_a = collider_a.shape().as_shape::<LevelCollider>();
            let level_collider_b = collider_b.shape().as_shape::<LevelCollider>();

            if level_collider_a.is_none() && level_collider_b.is_none() {
                continue;
            }

            let mut tangent_velo: Vector = Vector::ZERO;

            let mut velocity = 0.0;
            let mut friction_multiplier = 1.0;

            if let Some(handle) = context.rigid_body1 {
                let mut velo_1 = context
                    .bodies
                    .get(handle)
                    .unwrap()
                    .velocity_at_point(contact.point);
                velo_1 += Self::get_fake_velocity(contact, collider_a, level_collider_a);
                velocity += velo_1.dot(*context.normal);
            }
            if let Some(handle) = context.rigid_body2 {
                let mut velo_2 = context
                    .bodies
                    .get(handle)
                    .unwrap()
                    .velocity_at_point(contact.point);
                velo_2 += Self::get_fake_velocity(contact, collider_a, level_collider_a);
                velocity -= velo_2.dot(*context.normal);
            }

            velocity = velocity.abs();

            let mut restitution: Real = 0.0;

            let manifold_index = (*context.user_data >> 1) as usize;

            if let Some(level_collider_a) = level_collider_a {
                let (add_velo, remove_a, friction_mult, block_restitution) = handle_block_params(
                    collider_a.position(),
                    collider_a,
                    Some(level_collider_a),
                    &contact.point,
                    velocity,
                    manifold_index,
                    true,
                );
                tangent_velo += add_velo;
                remove |= remove_a;
                friction_multiplier *= friction_mult;
                restitution = restitution.max(block_restitution);
            }

            if let Some(level_collider_b) = level_collider_b {
                let (add_velo, remove_b, friction_mult, block_restitution) = handle_block_params(
                    collider_b.position(),
                    collider_b,
                    Some(level_collider_b),
                    &contact.point,
                    velocity,
                    manifold_index,
                    false,
                );
                tangent_velo -= add_velo;
                remove |= remove_b;
                friction_multiplier *= friction_mult;
                restitution = restitution.max(block_restitution);
            }

            tangent_velo -= *context.normal * tangent_velo.dot(*context.normal);

            contact.tangent_velocity = tangent_velo;
            contact.friction *= friction_multiplier;
            contact.restitution = contact.restitution.max(restitution);
        }

        if remove {
            context.solver_contacts.clear()
        }
    }
}

impl SablePhysicsHooks {
    fn get_fake_velocity(
        contact: &SolverContact,
        collider_a: &Collider,
        level_collider_a: Option<&LevelCollider>,
    ) -> Vector {
        if let Some(level_collider_a) = level_collider_a
            && level_collider_a.id.is_some()
        {
            let scene_id = level_collider_a.scene_id;
            let scene = get_scene_ref(scene_id);

            let collider_info =
                &scene.level_colliders[&(level_collider_a.id.unwrap() as LevelColliderID)];

            if let Some(fake_velo) = collider_info.fake_velocities {
                let transform = collider_a.position();
                return transform.transform_vector(fake_velo.velocity_at_point(
                    transform.inverse_transform_point(contact.point),
                    Vector::ZERO,
                ));
            };
        }
        Vector::new(0.0, 0.0, 0.0)
    }
}

fn handle_block_params(
    isometry: &Pose,
    _collider: &Collider,
    level_collider: Option<&LevelCollider>,
    global_point: &Vector,
    velocity: Real,
    manifold_index: usize,
    body_a: bool,
) -> (Vector, bool, Real, Real) {
    let state = unsafe { get_physics_state() };
    let scene = get_scene_mut(level_collider.unwrap().scene_id);

    let collider_info = level_collider.and_then(|lc| lc.id.map(|id| &scene.level_colliders[&(id)]));

    let mut tangent_velo: Vector = Vector::ZERO;

    if collider_info.is_some()
        && let Some(fake_velo) = collider_info.unwrap().fake_velocities
    {
        tangent_velo += isometry.transform_vector(fake_velo.velocity_at_point(
            isometry.inverse_transform_point(*global_point),
            Vector::ZERO,
        ));
    };

    // Get manifold info from the map
    let Some(manifold_info) = scene.manifold_info_map.list.get(&manifold_index) else {
        return (tangent_velo, false, 1.0, 0.0);
    };

    let block_coord = if body_a {
        manifold_info.pos_a
    } else {
        manifold_info.pos_b
    };

    let block_id = if body_a {
        manifold_info.col_a as u32
    } else {
        manifold_info.col_b as u32
    };

    let center_of_mass = collider_info.map_or(Vector3::zeros(), |b| b.center_of_mass.unwrap());
    let local = isometry.inverse_transform_point(*global_point);
    let block_coord_d: Vector3<f64> =
        Vector3::new(local.x as f64, local.y as f64, local.z as f64) + center_of_mass;

    if block_id == 0 {
        return (tangent_velo, false, 1.0, 0.0);
    }

    let voxel_collider_data = &state
        .voxel_collider_map
        .voxel_colliders
        .get((block_id - 1) as usize);
    let mut friction_multiplier = 1.0;
    let mut restitution = 0.0;

    if voxel_collider_data.is_none() {
        return (tangent_velo, false, friction_multiplier, restitution);
    }

    let voxel_collider_data = voxel_collider_data.unwrap().as_ref().unwrap();
    friction_multiplier *= voxel_collider_data.friction;
    restitution = voxel_collider_data.restitution;

    let Some(contact_events) = voxel_collider_data.contact_events.as_ref() else {
        return (tangent_velo, false, friction_multiplier, restitution);
    };

    if collider_info.is_some() && collider_info.unwrap().has_own_chunks() {
        return (tangent_velo, false, friction_multiplier, restitution);
    }

    let Some(current_step_vm) = &mut scene.current_step_vm else {
        panic!("No current step env!");
    };

    let Some(method) = &voxel_collider_data.contact_method else {
        panic!("No contact method!");
    };

    let args = &[
        JValue::Int(block_coord.x as jint),
        JValue::Int(block_coord.y as jint),
        JValue::Int(block_coord.z as jint),
        JValue::Double(block_coord_d.x),
        JValue::Double(block_coord_d.y),
        JValue::Double(block_coord_d.z),
        JValue::Double(velocity as jdouble),
    ];

    let args: Vec<jvalue> = args.iter().map(|v| v.as_jni()).collect();

    let mut guard = current_step_vm.attach_current_thread().unwrap();

    let result =
        unsafe { guard.call_method_unchecked(contact_events, method, ReturnType::Array, &args) }
            .unwrap();
    let arr = JDoubleArray::from(JObject::try_from(result).unwrap());

    let mut velo_arr: [jdouble; 4] = [0.0, 0.0, 0.0, 0.0];
    guard
        .get_double_array_region(arr, 0, &mut velo_arr)
        .unwrap();

    let velo = Vec3::new(
        velo_arr[0] as Real,
        velo_arr[1] as Real,
        velo_arr[2] as Real,
    );

    (
        tangent_velo + isometry.transform_vector(velo),
        velo_arr[3] > 0.0,
        friction_multiplier,
        restitution,
    )
}

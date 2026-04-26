use crate::scene::ChunkAccess;
use crate::{
    algo::{DEFAULT_COLLISION_PARALLEL_CUTOFF, find_collision_pairs},
    get_physics_state_mut,
    scene::PhysicsScene,
};
use marten::Real;
use rapier3d::dynamics::RigidBody;
use rapier3d::geometry::Aabb;
use rapier3d::glamx::IVec3;
use rapier3d::math::Vec3;
use rapier3d::prelude::RigidBodyVelocity;

pub fn compute_buoyancy(scene: &mut PhysicsScene) {
    let state = unsafe { get_physics_state_mut() };
    for (id, body_handle) in scene.rigid_bodies.iter() {
        let info = scene.level_colliders.get(id);

        if info.is_none() {
            continue;
        }
        let info = info.unwrap();
        let Some(body) = scene.rigid_body_set.get_mut(*body_handle) else {
            panic!("No body with given handle!");
        };

        let Some(center_of_mass) = info.center_of_mass else {
            panic!("No center of mass for body!");
        };

        let Some(local_bounds_min) = info.local_bounds_min else {
            panic!("No local bounds for body!");
        };
        let Some(local_bounds_max) = info.local_bounds_max else {
            panic!("No local bounds for body!");
        };
        body.reset_forces(false);
        body.reset_torques(false);
        let pairs = find_collision_pairs(
            info,
            None,
            body.position(),
            0.0,
            DEFAULT_COLLISION_PARALLEL_CUTOFF,
            true,
        );
        let vels: RigidBodyVelocity<Real> = *body.vels();

        let complex = (local_bounds_max - local_bounds_min).element_sum() < 10;
        for (static_pos, dynamic_pos) in pairs.iter() {
            let mut local_pos = ((dynamic_pos.as_dvec3() + 0.5) - center_of_mass).as_vec3();
            //TODO reduce duplication
            if complex {
                for i in 0..8 {
                    let x = (i & 1) * 2 - 1;
                    let y = ((i >> 1) & 1) * 2 - 1;
                    let z = ((i >> 2) & 1) * 2 - 1;

                    local_pos += IVec3::new(x, y, z).as_vec3() * 0.25;

                    do_drag(body, &vels, static_pos, &local_pos, 0.25, 1.0);
                }
            } else {
                do_drag(body, &vels, static_pos, &local_pos, 0.5, 1.0);
            }
        }
        let scene = state.scenes.get_mut(&scene.scene_id).unwrap();
        for (static_pos, dynamic_pos) in pairs.iter() {
            let chunk = scene.get_chunk(dynamic_pos >> 4);

            if chunk.is_none() {
                continue;
            }

            let (block_id, _voxel_collider_state) = chunk.unwrap().get_block(dynamic_pos & 15);

            // block id's are unsigned, and offset by 1 to allow for a single "empty" at 0
            if block_id == 0 {
                continue;
            }

            let voxel_collider_data = &state
                .voxel_collider_map
                .get((block_id - 1) as usize, *dynamic_pos);

            let Some(voxel_collider_data) = &voxel_collider_data else {
                continue;
            };
            let mut local_pos = ((dynamic_pos.as_dvec3() + 0.5) - center_of_mass).as_vec3();

            let complex = (local_bounds_max - local_bounds_min).element_sum() < 10;
            if complex {
                for i in 0..8 {
                    let x = (i & 1) * 2 - 1;
                    let y = ((i >> 1) & 1) * 2 - 1;
                    let z = ((i >> 2) & 1) * 2 - 1;
                    local_pos += IVec3::new(x, y, z).as_vec3() * 0.25;
                    do_float(
                        body,
                        static_pos,
                        &local_pos,
                        0.25,
                        voxel_collider_data.volume,
                    );
                }
            } else {
                do_float(
                    body,
                    static_pos,
                    &local_pos,
                    0.5,
                    voxel_collider_data.volume,
                );
            }
        }
    }
}

//TODO reduce duplication
fn do_drag(
    body: &mut RigidBody,
    vels: &RigidBodyVelocity<Real>,
    static_pos: &IVec3,
    point: &Vec3,
    size: Real,
    strength: Real,
) {
    let point = body.position().transform_point(*point);
    let static_pos = static_pos.as_vec3();
    let overlap = Aabb::new(point - Vec3::splat(size), point + Vec3::splat(size))
        .intersection(&Aabb::new(static_pos, static_pos + 1.0));

    if overlap.is_none() {
        return;
    }

    let volume = overlap.unwrap().volume();
    let velo = vels.velocity_at_point(point, body.mass_properties().world_com);

    body.add_force_at_point(-velo * 1.7 * volume * strength, point, false);
}

fn do_float(body: &mut RigidBody, static_pos: &IVec3, point: &Vec3, size: Real, strength: Real) {
    let point = body.position().transform_point(*point);
    let static_pos = static_pos.as_vec3();
    let overlap = Aabb::new(point - Vec3::splat(size), point + Vec3::splat(size))
        .intersection(&Aabb::new(static_pos, static_pos + 1.0));

    if overlap.is_none() {
        return;
    }

    let volume = overlap.unwrap().volume();

    body.add_force_at_point(Vec3::new(0.0, 10.5 * volume * strength, 0.0), point, false);
}

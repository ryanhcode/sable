use crate::collider::LevelCollider;
use log::info;
use rapier3d::geometry::{ContactManifoldData, Shape};
use rapier3d::glamx::Pose3;
use rapier3d::math::Vector;
use rapier3d::na::Vector3;
use rapier3d::parry::query::details::{NormalConstraints, contact_manifold_cuboid_cuboid_shapes};
use rapier3d::parry::query::{
    ClosestPoints, Contact, ContactManifold, ContactManifoldsWorkspace, DefaultQueryDispatcher,
    NonlinearRigidMotion, PersistentQueryDispatcher, QueryDispatcher, ShapeCastHit,
    ShapeCastOptions, Unsupported,
};
use rapier3d::prelude::ShapeType::Custom;
use rapier3d::prelude::{Aabb, Real};

use crate::algo::find_collision_pairs;
use crate::scene::{ChunkAccess, LevelColliderID, SableManifoldInfo};
use crate::{ActiveLevelColliderInfo, PhysicsState, get_physics_state, get_scene_ref};
use marten::level::VoxelPhysicsState::{Edge, Face, Interior};
use marten::level::{NEEDS_HOOKS_USER_DATA, VoxelPhysicsState};
use std::sync::atomic::Ordering;

/// The distance we scale collision points local to the box collider for before we check interior collisions
/// This helps avoid a missed interior collision when points are slightly outside of their voxel on an axis
/// not aligned with their normal. Example: A horizontal interior collision with a point 0.5001 above the
/// block center.
const INTERIOR_COLLISION_SCALE_FACTOR: Real = 0.99;

/// The distance at which we offset the normal of a collision point to check if it is inside a voxel collider
/// to rule it as an interior collision
const INTERIOR_COLLISION_CHECK_DISTANCE: f64 = 0.015;

type IVec3 = Vector3<i32>;

pub struct SableDispatcher;

impl SableDispatcher {
    /// Computes the local, inclusive block bounds of a global aabb with inflation
    #[allow(clippy::cast_possible_truncation)]
    #[allow(unused)]
    fn get_local_block_bounds(mut local_aabb: Aabb, inflation: Real) -> (IVec3, IVec3) {
        // Inflate the aabb by the prediction distance
        local_aabb.maxs += Vector::splat(inflation);
        local_aabb.mins -= Vector::splat(inflation);

        let local_min = IVec3::new(
            local_aabb.mins.x.floor() as i32,
            local_aabb.mins.y.floor() as i32,
            local_aabb.mins.z.floor() as i32,
        );

        let local_max = IVec3::new(
            local_aabb.maxs.x.floor() as i32,
            local_aabb.maxs.y.floor() as i32,
            local_aabb.maxs.z.floor() as i32,
        );

        (local_min, local_max)
    }
}

impl QueryDispatcher for SableDispatcher {
    fn intersection_test(
        &self,
        _pos12: &Pose3,
        g1: &dyn Shape,
        g2: &dyn Shape,
    ) -> Result<bool, Unsupported> {
        info!("intersect {:?} <-> {:?}", g1.shape_type(), g2.shape_type());
        Err(Unsupported)
    }

    fn distance(
        &self,
        _pos12: &Pose3,
        g1: &dyn Shape,
        g2: &dyn Shape,
    ) -> Result<Real, Unsupported> {
        info!("distance {:?} <-> {:?}", g1.shape_type(), g2.shape_type());
        Err(Unsupported)
    }

    fn contact(
        &self,
        _pos12: &Pose3,
        g1: &dyn Shape,
        g2: &dyn Shape,
        _prediction: Real,
    ) -> Result<Option<Contact>, Unsupported> {
        info!("contact {:?} <-> {:?}", g1.shape_type(), g2.shape_type());
        Err(Unsupported)
    }

    fn closest_points(
        &self,
        _pos12: &Pose3,
        g1: &dyn Shape,
        g2: &dyn Shape,
        _max_dist: Real,
    ) -> Result<ClosestPoints, Unsupported> {
        info!(
            "closest points {:?} <-> {:?}",
            g1.shape_type(),
            g2.shape_type()
        );
        Err(Unsupported)
    }

    fn cast_shapes(
        &self,
        _pos12: &Pose3,
        _local_vel12: Vector,
        _g1: &dyn Shape,
        _g2: &dyn Shape,
        _options: ShapeCastOptions,
    ) -> Result<Option<ShapeCastHit>, Unsupported> {
        Err(Unsupported)
    }

    fn cast_shapes_nonlinear(
        &self,
        _motion1: &NonlinearRigidMotion,
        _g1: &dyn Shape,
        _motion2: &NonlinearRigidMotion,
        _g2: &dyn Shape,
        _start_time: Real,
        _end_time: Real,
        _stop_at_penetration: bool,
    ) -> Result<Option<ShapeCastHit>, Unsupported> {
        Err(Unsupported)
    }
}

impl<ContactData> PersistentQueryDispatcher<ContactManifoldData, ContactData> for SableDispatcher
where
    ContactData: Default + Copy,
{
    fn contact_manifolds(
        &self,
        pos12: &Pose3,
        g1: &dyn Shape,
        g2: &dyn Shape,
        prediction: Real,
        manifolds: &mut Vec<ContactManifold<ContactManifoldData, ContactData>>,
        _workspace: &mut Option<ContactManifoldsWorkspace>,
    ) -> Result<(), Unsupported> {
        if g1.shape_type() != Custom && g2.shape_type() != Custom {
            return Err(Unsupported);
        }

        if g1.shape_type() == Custom && g2.shape_type() != Custom {
            Self::static_world_vs_collider::<ContactData>(
                pos12,
                g1.as_shape::<LevelCollider>().unwrap(),
                g2,
                prediction,
                manifolds,
                false,
            );
        } else if g1.shape_type() != Custom && g2.shape_type() == Custom {
            Self::static_world_vs_collider::<ContactData>(
                &pos12.inverse(),
                g2.as_shape::<LevelCollider>().unwrap(),
                g1,
                prediction,
                manifolds,
                true,
            );
        } else {
            assert_eq!(g1.shape_type(), Custom);
            assert_eq!(g2.shape_type(), Custom);

            // cast to LevelCollider
            let g1 = g1.as_shape::<LevelCollider>().unwrap();
            let g2 = g2.as_shape::<LevelCollider>().unwrap();

            let scene = get_scene_ref(g1.scene_id);

            if g1.is_static && !g2.is_static {
                Self::world_vs_world::<ContactData>(pos12, g1, g2, prediction, manifolds, false);
            } else if !g1.is_static && !g2.is_static {
                let body_1 = g1
                    .id
                    .map(|id| &scene.level_colliders[&(id as LevelColliderID)])
                    .unwrap();
                let body_2 = g2
                    .id
                    .map(|id| &scene.level_colliders[&(id as LevelColliderID)])
                    .unwrap();

                let extents_1 = body_1.local_bounds_max.unwrap() - body_1.local_bounds_min.unwrap()
                    + Vector3::new(1, 1, 1);
                let extents_2 = body_2.local_bounds_max.unwrap() - body_2.local_bounds_min.unwrap()
                    + Vector3::new(1, 1, 1);

                let volume_1 = extents_1.x * extents_1.y * extents_1.z;
                let volume_2 = extents_2.x * extents_2.y * extents_2.z;

                // Swap the bodies so we're always doing the least amount of work possible for collision detection
                let swap = volume_1 < volume_2;

                if swap {
                    Self::world_vs_world::<ContactData>(
                        &pos12.inverse(),
                        g2,
                        g1,
                        prediction,
                        manifolds,
                        true,
                    );
                } else {
                    Self::world_vs_world::<ContactData>(
                        pos12, g1, g2, prediction, manifolds, false,
                    );
                }
            }
        }

        Ok(())
    }

    fn contact_manifold_convex_convex(
        &self,
        _pos12: &Pose3,
        _g1: &dyn Shape,
        _g2: &dyn Shape,
        _normal_constraints1: Option<&dyn NormalConstraints>,
        _normal_constraints2: Option<&dyn NormalConstraints>,
        _prediction: Real,
        _manifold: &mut ContactManifold<ContactManifoldData, ContactData>,
    ) -> Result<(), Unsupported> {
        info!(
            "manifolds convex convex {:?} <-> {:?}",
            _g1.shape_type(),
            _g2.shape_type()
        );

        Err(Unsupported)
    }
}

impl SableDispatcher {
    fn static_world_vs_collider<ContactData: Default + Copy>(
        pos12: &Pose3,
        g1: &LevelCollider,
        g2: &dyn Shape,
        prediction: Real,
        manifolds: &mut Vec<ContactManifold<ContactManifoldData, ContactData>>,
        swap: bool,
    ) {
        let physics_state = unsafe { get_physics_state() };
        let scene = get_scene_ref(g1.scene_id);

        let collider_info = g1
            .id
            .map(|id| &scene.level_colliders[&(id as LevelColliderID)]);
        let center_of_mass_1 =
            collider_info.map_or(Vector3::zeros(), |b| b.center_of_mass.unwrap());

        let mut local_aabb = g2.compute_aabb(pos12);

        let margin: Real = 0.1;
        local_aabb.maxs += Vector::splat(prediction + margin);
        local_aabb.mins -= Vector::splat(prediction + margin);
        let local_aabb =
            Self::adjust_aabb_for_body(local_aabb, collider_info, center_of_mass_1, prediction);
        let (local_min, local_max) =
            Self::calculate_local_bounds(local_aabb, center_of_mass_1, prediction);

        let mut manifold_index = 0;

        let chunk_access: &dyn ChunkAccess = if let Some(info) = collider_info
            && info.has_own_chunks()
        {
            info
        } else {
            scene
        };

        for x in local_min.x..=local_max.x {
            for y in local_min.y..=local_max.y {
                for z in local_min.z..=local_max.z {
                    let Some(chunk) = chunk_access.get_chunk(x >> 4, y >> 4, z >> 4) else {
                        // chunk doesn't exist
                        continue;
                    };
                    let (block_id, _voxel_collider_state) = chunk.get_block(x & 15, y & 15, z & 15);

                    // block id's are unsigned, and offset by 1 to allow for a single "empty" at 0
                    if block_id == 0 {
                        continue;
                    }

                    let voxel_collider_data = &physics_state
                        .voxel_collider_map
                        .get((block_id - 1) as usize, IVec3::new(x, y, z));

                    if voxel_collider_data.is_none() {
                        continue;
                    }

                    let Some(voxel_collider_data) = &voxel_collider_data else {
                        unreachable!()
                    };

                    if voxel_collider_data.is_fluid {
                        continue;
                    }

                    for (min_x, min_y, min_z, max_x, max_y, max_z) in
                        &voxel_collider_data.collision_boxes
                    {
                        if manifolds.len() <= manifold_index {
                            manifolds.push(ContactManifold::new());
                        }

                        let center = Vector3::new(
                            ((min_x + max_x) / 2.0) as f64,
                            ((min_y + max_y) / 2.0) as f64,
                            ((min_z + max_z) / 2.0) as f64,
                        ) + Vector3::new(x as f64, y as f64, z as f64)
                            - center_of_mass_1;
                        let center =
                            Vector::new(center.x as Real, center.y as Real, center.z as Real);

                        let half_extents = Vector::new(
                            (max_x - min_x) / 2.0,
                            (max_y - min_y) / 2.0,
                            (max_z - min_z) / 2.0,
                        );

                        // Translate to match the center of the current block
                        let mut block_isometry = *pos12;
                        block_isometry.translation -= center;

                        if !swap {
                            DefaultQueryDispatcher
                                .contact_manifold_convex_convex(
                                    &block_isometry,
                                    &rapier3d::parry::shape::Cuboid::new(Vector::new(
                                        half_extents.x,
                                        half_extents.y,
                                        half_extents.z,
                                    )),
                                    g2,
                                    None,
                                    None,
                                    prediction,
                                    &mut manifolds[manifold_index],
                                )
                                .expect("uh oh");
                        } else {
                            DefaultQueryDispatcher
                                .contact_manifold_convex_convex(
                                    &block_isometry.inverse(),
                                    g2,
                                    &rapier3d::parry::shape::Cuboid::new(Vector::new(
                                        half_extents.x,
                                        half_extents.y,
                                        half_extents.z,
                                    )),
                                    None,
                                    None,
                                    prediction,
                                    &mut manifolds[manifold_index],
                                )
                                .expect("uh oh");
                        }

                        manifolds[manifold_index].data.user_data =
                            voxel_collider_data.get_user_data();

                        if collider_info.is_some()
                            && let Some(_velocities) = collider_info.unwrap().fake_velocities
                        {
                            manifolds[manifold_index].data.user_data |= NEEDS_HOOKS_USER_DATA;
                        }

                        for point in &mut manifolds[manifold_index].points {
                            let diff = Vector::new(center.x, center.y, center.z);
                            match swap {
                                true => point.local_p2 -= diff,
                                false => point.local_p1 += diff,
                            }
                        }

                        manifold_index += 1;
                    }
                }
            }
        }

        if manifolds.len() > manifold_index {
            manifolds.truncate(manifold_index);
        }
    }

    fn world_vs_world<ContactData: Default + Copy>(
        pos12: &Pose3,
        g1: &LevelCollider,
        g2: &LevelCollider,
        prediction: Real,
        manifolds: &mut Vec<ContactManifold<ContactManifoldData, ContactData>>,
        swap: bool,
    ) {
        let physics_state = unsafe { get_physics_state() };
        let scene = get_scene_ref(g1.scene_id);

        let collider_info_1 = g1
            .id
            .map(|id| &scene.level_colliders[&(id as LevelColliderID)]);
        let collider_info_2 = &scene.level_colliders[&(g2.id.unwrap() as LevelColliderID)];
        let center_of_mass_1 =
            collider_info_1.map_or(Vector3::zeros(), |b| b.center_of_mass.unwrap());
        let center_of_mass_2 = collider_info_2.center_of_mass.unwrap();

        let chunk_access_1: &dyn ChunkAccess = if let Some(info) = collider_info_1
            && info.has_own_chunks()
        {
            info
        } else {
            scene
        };

        let chunk_access_2: &dyn ChunkAccess = if collider_info_2.has_own_chunks() {
            collider_info_2
        } else {
            scene
        };

        // let local_aabb = g2.compute_aabb(&pos12);
        // let local_aabb = Self::adjust_aabb_for_body(local_aabb, body_1, center_of_mass_1, prediction);
        // let (local_min, local_max) = Self::calculate_local_bounds(local_aabb, center_of_mass_1, prediction);

        let mut manifold_index = 0;

        let pairs = find_collision_pairs(
            collider_info_2,
            collider_info_1,
            pos12,
            prediction,
            256,
            false,
        );
        // if (true) {
        //     return;
        // }
        for (static_pos, dynamic_pos) in pairs.iter() {
            let static_x = static_pos.x;
            let static_y = static_pos.y;
            let static_z = static_pos.z;

            let other_bx = dynamic_pos.x;
            let other_by = dynamic_pos.y;
            let other_bz = dynamic_pos.z;

            let Some(chunk) = chunk_access_1.get_chunk(static_x >> 4, static_y >> 4, static_z >> 4)
            else {
                // chunk doesn't exist
                continue;
            };
            let (block_id, voxel_collider_state) =
                chunk.get_block(static_x & 15, static_y & 15, static_z & 15);

            // block id's are unsigned, and offset by 1 to allow for a single "empty" at 0
            if block_id == 0 {
                continue;
            }

            let voxel_collider_data = &physics_state.voxel_collider_map.get(
                (block_id - 1) as usize,
                IVec3::new(static_x, static_y, static_z),
            );

            let Some(voxel_collider_data) = &voxel_collider_data else {
                continue;
            };

            for (min_x, min_y, min_z, max_x, max_y, max_z) in &voxel_collider_data.collision_boxes {
                if manifolds.len() <= manifold_index {
                    manifolds.push(ContactManifold::new());
                }

                let center = Vector3::new(
                    ((min_x + max_x) / 2.0) as f64,
                    ((min_y + max_y) / 2.0) as f64,
                    ((min_z + max_z) / 2.0) as f64,
                ) + Vector3::new(static_x as f64, static_y as f64, static_z as f64)
                    - center_of_mass_1;
                let center = Vector3::new(center.x as Real, center.y as Real, center.z as Real);

                let half_extents = Vector3::new(
                    (max_x - min_x) / 2.0,
                    (max_y - min_y) / 2.0,
                    (max_z - min_z) / 2.0,
                );

                // Translate to match the center of the current block
                let mut block_isometry = *pos12;
                block_isometry.translation -= Vector::new(center.x, center.y, center.z);

                // let block_bounds = Aabb::new(
                //     Point3::new(-half_extents.x, -half_extents.y, -half_extents.z)
                //         + center,
                //     Point3::new(half_extents.x, half_extents.y, half_extents.z)
                //         + center,
                // )
                //     .transform_by(&pos12.inverse());

                // let (other_block_min, other_block_max) = Self::calculate_local_bounds(block_bounds, center_of_mass_2, prediction);

                // for other_bx in other_block_min.x..=other_block_max.x {
                //     for other_by in other_block_min.y..=other_block_max.y {
                //         for other_bz in other_block_min.z..=other_block_max.z {
                let Some(other_chunk) =
                    chunk_access_2.get_chunk(other_bx >> 4, other_by >> 4, other_bz >> 4)
                else {
                    // chunk doesn't exist
                    continue;
                };
                let (other_block_id, other_voxel_collider_state) =
                    other_chunk.get_block(other_bx & 15, other_by & 15, other_bz & 15);

                // block id's are unsigned, and offset by 1 to allow for a single "empty" at 0
                if other_block_id == 0 {
                    continue;
                }

                if Self::can_ignore_collision(voxel_collider_state, other_voxel_collider_state) {
                    continue;
                }

                let other_voxel_collider_data = &physics_state.voxel_collider_map.get(
                    (other_block_id - 1) as usize,
                    IVec3::new(other_bx, other_by, other_bz),
                );

                let Some(other_voxel_collider_data) = &other_voxel_collider_data else {
                    continue;
                };

                for (
                    other_min_x,
                    other_min_y,
                    other_min_z,
                    other_max_x,
                    other_max_y,
                    other_max_z,
                ) in &other_voxel_collider_data.collision_boxes
                {
                    if manifolds.len() <= manifold_index {
                        manifolds.push(ContactManifold::new());
                    }

                    let other_center =
                        Vector3::new(
                            ((other_min_x + other_max_x) / 2.0) as f64,
                            ((other_min_y + other_max_y) / 2.0) as f64,
                            ((other_min_z + other_max_z) / 2.0) as f64,
                        ) + Vector3::new(other_bx as f64, other_by as f64, other_bz as f64)
                            - center_of_mass_2;
                    let other_center = Vector3::new(
                        other_center.x as Real,
                        other_center.y as Real,
                        other_center.z as Real,
                    );

                    let other_half_extents = Vector3::new(
                        (other_max_x - other_min_x) / 2.0,
                        (other_max_y - other_min_y) / 2.0,
                        (other_max_z - other_min_z) / 2.0,
                    );

                    // combine block isometries
                    let mut combined_block_isometry = block_isometry;

                    let transformed = combined_block_isometry.rotation.mul_vec3(Vector::new(
                        other_center.x,
                        other_center.y,
                        other_center.z,
                    ));

                    combined_block_isometry.translation += transformed;

                    let mut new_manifold: ContactManifold<ContactManifoldData, ContactData> =
                        ContactManifold::new();
                    contact_manifold_cuboid_cuboid_shapes(
                        &combined_block_isometry,
                        &rapier3d::parry::shape::Cuboid::new(Vector::new(
                            half_extents.x,
                            half_extents.y,
                            half_extents.z,
                        )),
                        &rapier3d::parry::shape::Cuboid::new(Vector::new(
                            other_half_extents.x,
                            other_half_extents.y,
                            other_half_extents.z,
                        )),
                        prediction,
                        &mut new_manifold,
                    );

                    if !is_interior_collision(
                        chunk_access_1,
                        chunk_access_2,
                        collider_info_1,
                        collider_info_2,
                        IVec3::new(static_x, static_y, static_z),
                        IVec3::new(other_bx, other_by, other_bz),
                        center,
                        other_center,
                        center_of_mass_1,
                        center_of_mass_2,
                        &mut new_manifold,
                    ) {
                        let index = scene
                            .manifold_info_map
                            .counter
                            .fetch_add(1, Ordering::Relaxed);

                        scene.manifold_info_map.list.insert(
                            index,
                            if swap {
                                SableManifoldInfo {
                                    pos_a: IVec3::new(other_bx, other_by, other_bz),
                                    pos_b: IVec3::new(static_x, static_y, static_z),
                                    col_a: other_block_id as usize,
                                    col_b: block_id as usize,
                                }
                            } else {
                                SableManifoldInfo {
                                    pos_a: IVec3::new(static_x, static_y, static_z),
                                    pos_b: IVec3::new(other_bx, other_by, other_bz),
                                    col_a: block_id as usize,
                                    col_b: other_block_id as usize,
                                }
                            },
                        );

                        new_manifold.data.user_data = voxel_collider_data.get_user_data()
                            | other_voxel_collider_data.get_user_data()
                            | ((index << 1) as u32);

                        if let Some(_velocities) = collider_info_2.fake_velocities {
                            new_manifold.data.user_data |= NEEDS_HOOKS_USER_DATA;
                        }
                        if let Some(info) = collider_info_1
                            && info.fake_velocities.is_some()
                        {
                            new_manifold.data.user_data |= NEEDS_HOOKS_USER_DATA;
                        }

                        manifolds[manifold_index] = new_manifold;

                        for point in &mut manifolds[manifold_index].points {
                            point.local_p1 += Vector::new(center.x, center.y, center.z);
                            point.local_p2 +=
                                Vector::new(other_center.x, other_center.y, other_center.z);
                        }

                        manifold_index += 1;
                    }
                }
            }
        }

        // swap bodies in the manifolds
        if swap {
            for manifold in manifolds.iter_mut() {
                for point in &mut manifold.points {
                    // swap positions
                    std::mem::swap(&mut point.local_p1, &mut point.local_p2);
                }

                // swap normals
                std::mem::swap(&mut manifold.local_n1, &mut manifold.local_n2);
            }
        }

        if manifolds.len() > manifold_index {
            manifolds.truncate(manifold_index);
        }
    }

    /// Adjusts the AABB for a given body
    #[inline(always)]
    fn adjust_aabb_for_body(
        mut local_aabb: Aabb,
        body: Option<&ActiveLevelColliderInfo>,
        center_of_mass: Vector3<f64>,
        prediction: Real,
    ) -> Aabb {
        if let Some(body) = body {
            let local_bounds_min = body.local_bounds_min.unwrap();
            let local_bounds_max = body.local_bounds_max.unwrap();

            let body_aabb = Aabb::new(
                Vector::new(
                    (local_bounds_min.x as f64 - center_of_mass.x) as Real - prediction,
                    (local_bounds_min.y as f64 - center_of_mass.y) as Real - prediction,
                    (local_bounds_min.z as f64 - center_of_mass.z) as Real - prediction,
                ),
                Vector::new(
                    ((local_bounds_max.x + 1) as f64 - center_of_mass.x) as Real + prediction,
                    ((local_bounds_max.y + 1) as f64 - center_of_mass.y) as Real + prediction,
                    ((local_bounds_max.z + 1) as f64 - center_of_mass.z) as Real + prediction,
                ),
            );

            local_aabb = local_aabb.intersection(&body_aabb).unwrap_or(local_aabb);
        }

        local_aabb
    }

    /// Calculates local bounds based on AABB and prediction
    #[inline(always)]
    fn calculate_local_bounds(
        aabb: Aabb,
        center_of_mass: Vector3<f64>,
        prediction: Real,
    ) -> (IVec3, IVec3) {
        let maxs = Vector3::<f64>::new(
            aabb.maxs.x as f64 + center_of_mass.x,
            aabb.maxs.y as f64 + center_of_mass.y,
            aabb.maxs.z as f64 + center_of_mass.z,
        ) + Vector3::repeat(prediction as f64);

        let mins = Vector3::<f64>::new(
            aabb.mins.x as f64 + center_of_mass.x,
            aabb.mins.y as f64 + center_of_mass.y,
            aabb.mins.z as f64 + center_of_mass.z,
        ) - Vector3::repeat(prediction as f64);

        let local_min = IVec3::new(
            mins.x.floor() as i32,
            mins.y.floor() as i32,
            mins.z.floor() as i32,
        );

        let local_max = IVec3::new(
            maxs.x.floor() as i32,
            maxs.y.floor() as i32,
            maxs.z.floor() as i32,
        );

        (local_min, local_max)
    }

    #[inline(always)]
    #[must_use]
    fn can_ignore_collision(
        voxel_collider_state: VoxelPhysicsState,
        other_voxel_collider_state: VoxelPhysicsState,
    ) -> bool {
        (other_voxel_collider_state == voxel_collider_state && voxel_collider_state == Face)
            || voxel_collider_state == Interior
            || other_voxel_collider_state == Interior
            || (voxel_collider_state == Edge && other_voxel_collider_state == Face)
            || (voxel_collider_state == Face && other_voxel_collider_state == Edge)
    }
}

fn to_f64(vec: Vector) -> Vector3<f64> {
    Vector3::new(vec.x as f64, vec.y as f64, vec.z as f64)
}

fn get_block_pos(vec: Vector3<f64>) -> IVec3 {
    IVec3::new(
        vec.x.floor() as i32,
        vec.y.floor() as i32,
        vec.z.floor() as i32,
    )
}

fn is_interior_collision<ManifoldData: Default + Clone, ContactData: Default + Copy>(
    chunk_access_1: &dyn ChunkAccess,
    chunk_access_2: &dyn ChunkAccess,
    collider_info_1: Option<&ActiveLevelColliderInfo>,
    collider_info_2: &ActiveLevelColliderInfo,
    block_a: IVec3,
    block_b: IVec3,
    center: Vector3<Real>,
    other_center: Vector3<Real>,
    center_of_mass_1: Vector3<f64>,
    center_of_mass_2: Vector3<f64>,
    manifold: &mut ContactManifold<ManifoldData, ContactData>,
) -> bool {
    let physics_state = unsafe { get_physics_state() };

    manifold.points.retain(|point| {
        if collider_info_1.is_none()
            || (collider_info_1.unwrap().local_bounds_min.unwrap()
                != collider_info_1.unwrap().local_bounds_max.unwrap())
        {
            let world_p1 =
                to_f64(point.local_p1 * 0.997 + Vector::new(center.x, center.y, center.z))
                    + center_of_mass_1;

            let normal1 = to_f64(manifold.local_n1);
            let displaced_p1 = world_p1 + normal1 * 0.01;

            if is_inside_voxel_collider(chunk_access_1, physics_state, block_a, displaced_p1) {
                return false;
            }
        }

        if collider_info_2.local_bounds_min.unwrap() != collider_info_2.local_bounds_max.unwrap() {
            let normal2 = to_f64(manifold.local_n2);

            // we have to "pull in the points" a tiny bit incase they're outside of the block slightly off-normal
            let world_p2 = to_f64(
                point.local_p2 * INTERIOR_COLLISION_SCALE_FACTOR
                    + Vector::new(other_center.x, other_center.y, other_center.z),
            ) + center_of_mass_2;

            let displaced_p2 = world_p2 + normal2 * INTERIOR_COLLISION_CHECK_DISTANCE;

            if is_inside_voxel_collider(chunk_access_2, physics_state, block_b, displaced_p2) {
                return false;
            }
        }

        true
    });

    false
}

fn is_inside_voxel_collider(
    chunk_access: &dyn ChunkAccess,
    physics_state: &PhysicsState,
    ignore_block: IVec3,
    world_pos: Vector3<f64>,
) -> bool {
    let block_pos = get_block_pos(world_pos);

    if ignore_block == block_pos {
        return false;
    }

    let Some(chunk) = chunk_access.get_chunk(block_pos.x >> 4, block_pos.y >> 4, block_pos.z >> 4)
    else {
        return false;
    };
    let (block_id, _) = chunk.get_block(block_pos.x & 15, block_pos.y & 15, block_pos.z & 15);

    if block_id == 0 {
        return false;
    }

    let voxel_data = physics_state
        .voxel_collider_map
        .get((block_id - 1) as usize, block_pos);

    let Some(voxel_data) = voxel_data else {
        return false;
    };

    if voxel_data.is_fluid {
        return false;
    }

    let local_pos = Vector3::new(
        world_pos.x - block_pos.x as f64,
        world_pos.y - block_pos.y as f64,
        world_pos.z - block_pos.z as f64,
    );

    for &(min_x, min_y, min_z, max_x, max_y, max_z) in &voxel_data.collision_boxes {
        if local_pos.x as Real >= min_x
            && local_pos.x as Real <= max_x
            && local_pos.y as Real >= min_y
            && local_pos.y as Real <= max_y
            && local_pos.z as Real >= min_z
            && local_pos.z as Real <= max_z
        {
            return true;
        }
    }

    false
}

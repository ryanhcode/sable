use crate::collider::LevelCollider;
use log::info;
use rapier3d::geometry::{ContactManifoldData, Shape};
use rapier3d::glamx::{DVec3, IVec3, Pose3};
use rapier3d::math::Vec3;
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
use marten::level::{CollisionBox, NEEDS_HOOKS_USER_DATA, VoxelPhysicsState};
use std::sync::atomic::Ordering;

/// The distance we scale collision points local to the box collider for before we check interior collisions
/// This helps avoid a missed interior collision when points are slightly outside of their voxel on an axis
/// not aligned with their normal. Example: A horizontal interior collision with a point 0.5001 above the
/// block center.
const INTERIOR_COLLISION_SCALE_FACTOR: Real = 0.99;

/// The distance at which we offset the normal of a collision point to check if it is inside a voxel collider
/// to rule it as an interior collision
const INTERIOR_COLLISION_CHECK_DISTANCE: f64 = 0.015;

pub struct SableDispatcher;

impl SableDispatcher {
    /// Computes the local, inclusive block bounds of a global aabb with inflation
    #[allow(clippy::cast_possible_truncation)]
    #[allow(unused)]
    fn get_local_block_bounds(mut local_aabb: Aabb, inflation: Real) -> (IVec3, IVec3) {
        // Inflate the aabb by the prediction distance
        local_aabb.maxs += Vec3::splat(inflation);
        local_aabb.mins -= Vec3::splat(inflation);

        (
            local_aabb.mins.floor().as_ivec3(),
            local_aabb.maxs.floor().as_ivec3(),
        )
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
        _local_vel12: Vec3,
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
                    + IVec3::ONE;
                let extents_2 = body_2.local_bounds_max.unwrap() - body_2.local_bounds_min.unwrap()
                    + IVec3::ONE;

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
        let center_of_mass_1 = collider_info.map_or(DVec3::ZERO, |b| b.center_of_mass.unwrap());

        let mut local_aabb = g2.compute_aabb(pos12);

        let margin: Real = 0.1;
        local_aabb.maxs += Vec3::splat(prediction + margin);
        local_aabb.mins -= Vec3::splat(prediction + margin);
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
                    let pos = IVec3::new(x, y, z);
                    let Some(chunk) = chunk_access.get_chunk(pos >> 4) else {
                        // chunk doesn't exist
                        continue;
                    };
                    let (block_id, _voxel_collider_state) = chunk.get_block(pos & 15);

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

                    for CollisionBox { min, max } in &voxel_collider_data.collision_boxes {
                        if manifolds.len() <= manifold_index {
                            manifolds.push(ContactManifold::new());
                        }

                        let center = (((min + max) / 2.0).as_dvec3() + pos.as_dvec3()
                            - center_of_mass_1)
                            .as_vec3();

                        let half_extents = (max - min) / 2.0;

                        // Translate to match the center of the current block
                        let mut block_isometry = *pos12;
                        block_isometry.translation -= center;

                        if !swap {
                            DefaultQueryDispatcher
                                .contact_manifold_convex_convex(
                                    &block_isometry,
                                    &rapier3d::parry::shape::Cuboid::new(half_extents),
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
                                    &rapier3d::parry::shape::Cuboid::new(half_extents),
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
                            let diff = Vec3::new(center.x, center.y, center.z);
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
        let center_of_mass_1 = collider_info_1.map_or(DVec3::ZERO, |b| b.center_of_mass.unwrap());
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
            let Some(chunk) = chunk_access_1.get_chunk(static_pos >> 4) else {
                // chunk doesn't exist
                continue;
            };
            let (block_id, voxel_collider_state) = chunk.get_block(static_pos & 15);

            // block id's are unsigned, and offset by 1 to allow for a single "empty" at 0
            if block_id == 0 {
                continue;
            }

            let voxel_collider_data = &physics_state
                .voxel_collider_map
                .get((block_id - 1) as usize, *static_pos);

            let Some(voxel_collider_data) = &voxel_collider_data else {
                continue;
            };

            for CollisionBox { min, max } in &voxel_collider_data.collision_boxes {
                if manifolds.len() <= manifold_index {
                    manifolds.push(ContactManifold::new());
                }

                let center = (((min + max) / 2.0).as_dvec3() + static_pos.as_dvec3()
                    - center_of_mass_1)
                    .as_vec3();

                let half_extents = (max - min) / 2.0;

                // Translate to match the center of the current block
                let mut block_isometry = *pos12;
                block_isometry.translation -= Vec3::new(center.x, center.y, center.z);

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
                let Some(other_chunk) = chunk_access_2.get_chunk(dynamic_pos >> 4) else {
                    // chunk doesn't exist
                    continue;
                };
                let (other_block_id, other_voxel_collider_state) =
                    other_chunk.get_block(dynamic_pos & 15);

                // block id's are unsigned, and offset by 1 to allow for a single "empty" at 0
                if other_block_id == 0 {
                    continue;
                }

                if Self::can_ignore_collision(voxel_collider_state, other_voxel_collider_state) {
                    continue;
                }

                let other_voxel_collider_data = &physics_state
                    .voxel_collider_map
                    .get((other_block_id - 1) as usize, *dynamic_pos);

                let Some(other_voxel_collider_data) = &other_voxel_collider_data else {
                    continue;
                };

                for CollisionBox {
                    min: other_min,
                    max: other_max,
                } in &other_voxel_collider_data.collision_boxes
                {
                    if manifolds.len() <= manifold_index {
                        manifolds.push(ContactManifold::new());
                    }

                    let other_center = (((other_min + other_max) / 2.0).as_dvec3()
                        + dynamic_pos.as_dvec3()
                        - center_of_mass_2)
                        .as_vec3();

                    let other_half_extents = (other_max - other_min) / 2.0;

                    // combine block isometries
                    let mut combined_block_isometry = block_isometry;

                    let transformed = combined_block_isometry.rotation.mul_vec3(other_center);

                    combined_block_isometry.translation += transformed;

                    let mut new_manifold: ContactManifold<ContactManifoldData, ContactData> =
                        ContactManifold::new();
                    contact_manifold_cuboid_cuboid_shapes(
                        &combined_block_isometry,
                        &rapier3d::parry::shape::Cuboid::new(half_extents),
                        &rapier3d::parry::shape::Cuboid::new(other_half_extents),
                        prediction,
                        &mut new_manifold,
                    );

                    if !is_interior_collision(
                        chunk_access_1,
                        chunk_access_2,
                        collider_info_1,
                        collider_info_2,
                        *static_pos,
                        *dynamic_pos,
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
                                    pos_a: *dynamic_pos,
                                    pos_b: *static_pos,
                                    col_a: other_block_id as usize,
                                    col_b: block_id as usize,
                                }
                            } else {
                                SableManifoldInfo {
                                    pos_a: *static_pos,
                                    pos_b: *dynamic_pos,
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
                            point.local_p1 += center;
                            point.local_p2 += other_center;
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
        center_of_mass: DVec3,
        prediction: Real,
    ) -> Aabb {
        if let Some(body) = body {
            let local_bounds_min = body.local_bounds_min.unwrap();
            let local_bounds_max = body.local_bounds_max.unwrap();

            let body_aabb = Aabb::new(
                (local_bounds_min.as_dvec3() - center_of_mass).as_vec3() - prediction,
                ((local_bounds_max + 1).as_dvec3() - center_of_mass).as_vec3() + prediction,
            );

            local_aabb = local_aabb.intersection(&body_aabb).unwrap_or(local_aabb);
        }

        local_aabb
    }

    /// Calculates local bounds based on AABB and prediction
    #[inline(always)]
    fn calculate_local_bounds(
        aabb: Aabb,
        center_of_mass: DVec3,
        prediction: Real,
    ) -> (IVec3, IVec3) {
        let maxs = aabb.maxs.as_dvec3() + center_of_mass + DVec3::splat(prediction as f64);
        let mins = aabb.mins.as_dvec3() + center_of_mass - DVec3::splat(prediction as f64);

        (mins.floor().as_ivec3(), maxs.floor().as_ivec3())
    }

    #[inline(always)]
    #[must_use]
    fn can_ignore_collision(
        voxel_collider_state: VoxelPhysicsState,
        other_voxel_collider_state: VoxelPhysicsState,
    ) -> bool {
        matches!(
            (voxel_collider_state, other_voxel_collider_state),
            (Face, Face) | (Interior, _) | (_, Interior) | (Edge, Face) | (Face, Edge)
        )
    }
}

fn is_interior_collision<ManifoldData: Default + Clone, ContactData: Default + Copy>(
    chunk_access_1: &dyn ChunkAccess,
    chunk_access_2: &dyn ChunkAccess,
    collider_info_1: Option<&ActiveLevelColliderInfo>,
    collider_info_2: &ActiveLevelColliderInfo,
    block_a: IVec3,
    block_b: IVec3,
    center: Vec3,
    other_center: Vec3,
    center_of_mass_1: DVec3,
    center_of_mass_2: DVec3,
    manifold: &mut ContactManifold<ManifoldData, ContactData>,
) -> bool {
    let physics_state = unsafe { get_physics_state() };

    manifold.points.retain(|point| {
        if collider_info_1.is_none()
            || (collider_info_1.unwrap().local_bounds_min.unwrap()
                != collider_info_1.unwrap().local_bounds_max.unwrap())
        {
            let world_p1 = (point.local_p1 * 0.997 + center).as_dvec3() + center_of_mass_1;

            let displaced_p1 = world_p1 + manifold.local_n1.as_dvec3() * 0.01;

            if is_inside_voxel_collider(chunk_access_1, physics_state, block_a, displaced_p1) {
                return false;
            }
        }

        if collider_info_2.local_bounds_min.unwrap() != collider_info_2.local_bounds_max.unwrap() {
            // let normal2 = to_f64(manifold.local_n2);

            // we have to "pull in the points" a tiny bit incase they're outside of the block slightly off-normal
            let world_p2 = (point.local_p2 * INTERIOR_COLLISION_SCALE_FACTOR + other_center)
                .as_dvec3()
                + center_of_mass_2;

            let displaced_p2 =
                world_p2 + manifold.local_n2.as_dvec3() * INTERIOR_COLLISION_CHECK_DISTANCE;

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
    world_pos: DVec3,
) -> bool {
    let floor_pos = world_pos.floor();
    let block_pos = floor_pos.as_ivec3();

    if ignore_block != block_pos
        && let Some(chunk) = chunk_access.get_chunk(block_pos >> 4)
        && let (block_id, _) = chunk.get_block(block_pos & 15)
        && block_id != 0
        && let Some(voxel_data) = physics_state
            .voxel_collider_map
            .get((block_id - 1) as usize, block_pos)
        && !voxel_data.is_fluid
    {
        let local_pos = (world_pos - floor_pos).as_vec3();
        voxel_data
            .collision_boxes
            .iter()
            .any(|&CollisionBox { min, max }| {
                local_pos.cmpge(min).all() && local_pos.cmple(max).all()
            })
    } else {
        false
    }
}

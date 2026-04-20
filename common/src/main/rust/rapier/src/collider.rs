use crate::PHYSICS_STATE;
use crate::scene::LevelColliderID;
use rapier3d::dynamics::MassProperties;
use rapier3d::geometry::{Shape, ShapeType, TypedShape};
use rapier3d::math::Vec3;
use rapier3d::parry::bounding_volume::{Aabb, BoundingSphere};
use rapier3d::prelude::*;
use std::f32::consts::PI;

const WORLD_SIZE: Real = 30_000_000.0;

#[derive(Debug, Clone, Copy)]
pub struct LevelCollider {
    /// Index in PhysicsState#sable_bodies
    pub id: Option<LevelColliderID>,

    /// If this is the static world collider
    pub is_static: bool,

    pub scene_id: i32,
}

impl LevelCollider {
    #[must_use]
    pub fn new(id: Option<LevelColliderID>, is_static: bool, scene_id: i32) -> Self {
        Self {
            id,
            is_static,
            scene_id,
        }
    }

    fn scaled(self, _scale: &Vec3) -> Self {
        Self { ..self }
    }
}

impl RayCast for LevelCollider {
    fn cast_local_ray_and_get_normal(
        &self,
        _ray: &rapier3d::parry::query::Ray,
        _max_time_of_impact: Real,
        _solid: bool,
    ) -> Option<rapier3d::parry::query::RayIntersection> {
        todo!()
    }
}

impl PointQuery for LevelCollider {
    fn project_local_point(
        &self,
        _pt: Vec3,
        _solid: bool,
    ) -> rapier3d::parry::query::PointProjection {
        todo!()
    }

    fn project_local_point_and_get_feature(
        &self,
        _pt: Vec3,
    ) -> (rapier3d::parry::query::PointProjection, FeatureId) {
        todo!()
    }
}

impl Shape for LevelCollider {
    fn compute_local_aabb(&self) -> Aabb {
        if self.is_static {
            Aabb::new(
                Vec3::new(-WORLD_SIZE, -WORLD_SIZE, -WORLD_SIZE),
                Vec3::new(WORLD_SIZE, WORLD_SIZE, WORLD_SIZE),
            )
        } else {
            let state = unsafe { PHYSICS_STATE.as_ref() }.expect("physics state to be present");
            let scene = state.scenes.get(&self.scene_id).expect("scene");

            let sable_body = &scene.level_colliders[&{ self.id.unwrap() }];

            let center_of_mass = sable_body.center_of_mass.unwrap();
            let local_min = sable_body.local_bounds_min.unwrap();
            let local_max = sable_body.local_bounds_max.unwrap();
            //TODO this gets duplicated a lot, there should be a factory function on AABB for it.
            let min = (local_min.as_dvec3() - center_of_mass).as_vec3();

            let max = ((local_max + 1).as_dvec3() - center_of_mass).as_vec3();

            Aabb::new(min, max)
        }
    }

    fn compute_local_bounding_sphere(&self) -> BoundingSphere {
        if self.is_static {
            BoundingSphere::new(Vec3::ZERO, WORLD_SIZE)
        } else {
            BoundingSphere::new(Vec3::ZERO, 1.0)
            // Bounding sphere that covers the entire bounding box
            // unsafe {
            //     let Some(state) = &PHYSICS_STATE else {
            //         panic!("no physics state!")
            //     };
            //
            //     let local_aabb = self.compute_local_aabb();
            //
            //     local_aabb.bounding_sphere()
            // }
        }
    }

    fn clone_dyn(&self) -> Box<dyn Shape> {
        Box::new(*self)
    }

    fn scale_dyn(&self, scale: Vec3, _num_subdivisions: u32) -> Option<Box<dyn Shape>> {
        Some(Box::new(self.scaled(&scale)))
    }

    fn mass_properties(&self, _density: Real) -> MassProperties {
        MassProperties {
            inv_mass: 0.0,
            inv_principal_inertia: Vec3::ZERO,
            local_com: Vec3::ZERO,
            principal_inertia_local_frame: Default::default(),
        }
    }

    fn shape_type(&self) -> ShapeType {
        ShapeType::Custom
    }

    fn as_typed_shape(&self) -> TypedShape<'_> {
        TypedShape::Custom(self)
    }

    fn ccd_thickness(&self) -> Real {
        0.25
    }

    fn ccd_angular_thickness(&self) -> Real {
        PI / 8.0
    }
}

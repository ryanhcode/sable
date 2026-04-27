use crate::PHYSICS_STATE;
use crate::scene::LevelColliderID;
use rapier3d::dynamics::MassProperties;
use rapier3d::geometry::{Shape, ShapeType, TypedShape};
use rapier3d::math::Vector;
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

    fn scaled(self, _scale: &Vector) -> Self {
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
        _pt: Vector,
        _solid: bool,
    ) -> rapier3d::parry::query::PointProjection {
        todo!()
    }

    fn project_local_point_and_get_feature(
        &self,
        _pt: Vector,
    ) -> (rapier3d::parry::query::PointProjection, FeatureId) {
        todo!()
    }
}

impl Shape for LevelCollider {
    fn compute_local_aabb(&self) -> Aabb {
        if self.is_static {
            Aabb::new(
                Vector::new(-WORLD_SIZE, -WORLD_SIZE, -WORLD_SIZE),
                Vector::new(WORLD_SIZE, WORLD_SIZE, WORLD_SIZE),
            )
        } else {
            unsafe {
                let Some(state) = &PHYSICS_STATE else {
                    panic!("no physics state!")
                };

                let Some(scene) = state.scenes.get(&self.scene_id) else {
                    panic!("No scene with given ID!");
                };

                let sable_body = &scene.level_colliders[&{ self.id.unwrap() }];

                let center_of_mass = sable_body.center_of_mass.unwrap();
                let local_min = sable_body.local_bounds_min.unwrap();
                let local_max = sable_body.local_bounds_max.unwrap();

                let min = Vector::new(
                    (local_min.x as f64 - center_of_mass.x) as Real,
                    (local_min.y as f64 - center_of_mass.y) as Real,
                    (local_min.z as f64 - center_of_mass.z) as Real,
                );

                let max = Vector::new(
                    ((local_max.x + 1) as f64 - center_of_mass.x) as Real,
                    ((local_max.y + 1) as f64 - center_of_mass.y) as Real,
                    ((local_max.z + 1) as f64 - center_of_mass.z) as Real,
                );

                Aabb::new(min, max)
            }
        }
    }

    fn compute_local_bounding_sphere(&self) -> BoundingSphere {
        if self.is_static {
            BoundingSphere::new(Vector::ZERO, WORLD_SIZE)
        } else {
            BoundingSphere::new(Vector::ZERO, 1.0)
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

    fn scale_dyn(&self, scale: Vector, _num_subdivisions: u32) -> Option<Box<dyn Shape>> {
        Some(Box::new(self.scaled(&scale)))
    }

    fn mass_properties(&self, _density: Real) -> MassProperties {
        MassProperties {
            inv_mass: 0.0,
            inv_principal_inertia: AngVector::new(0.0, 0.0, 0.0),
            local_com: Vector::ZERO,
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

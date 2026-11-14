use crate::ActiveLevelColliderInfo;
use crate::scene::{LevelColliderID, SimulationSceneData};
use rapier3d::dynamics::MassProperties;
use rapier3d::geometry::{Shape, ShapeType, SharedShape, TypedShape};
use rapier3d::glamx::{DVec3, IVec3};
use rapier3d::parry::bounding_volume::{Aabb, BoundingSphere};
use rapier3d::prelude::*;
use std::f32::consts::PI;

const WORLD_SIZE: Real = 30_000_000.0;

#[derive(Debug, Clone, Copy)]
pub struct LevelCollider {
    pub id: Option<LevelColliderID>,
    pub is_static: bool,
    #[allow(unused)]
    pub cached_aabb: Option<Aabb>,
    pub cached_aabb_scaled: Option<Aabb>,
}

impl LevelCollider {
    #[must_use]
    pub fn new(id: Option<LevelColliderID>, is_static: bool) -> Self {
        Self {
            id,
            is_static,
            cached_aabb: None,
            cached_aabb_scaled: None,
        }
    }

    fn scaled(self, _scale: &Vector) -> Self {
        Self { ..self }
    }
}

pub fn compute_local_aabb_from_bounds(min: IVec3, max: IVec3, com: DVec3) -> Aabb {
    Aabb::new(
        (min.as_dvec3() - com).as_vec3(),
        (max.as_dvec3() + 1.0 - com).as_vec3(),
    )
}

pub fn update_collider_aabb(sim: &mut SimulationSceneData, info: &ActiveLevelColliderInfo) {
    let (Some(min), Some(max), Some(com)) = (
        info.local_bounds_min,
        info.local_bounds_max,
        info.center_of_mass,
    ) else {
        return;
    };

    let new_aabb = compute_local_aabb_from_bounds(min, max, com);
    let collider = sim.collider_set.get_mut(info.collider).unwrap();
    let existing = *collider.shape().as_shape::<LevelCollider>().unwrap();
    collider.set_shape(SharedShape::new(LevelCollider {
        cached_aabb: Some(new_aabb),
        cached_aabb_scaled: Some(new_aabb.scaled(info.scale.as_vec3())),
        ..existing
    }));
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
                Vec3::new(-WORLD_SIZE, -WORLD_SIZE, -WORLD_SIZE),
                Vec3::new(WORLD_SIZE, WORLD_SIZE, WORLD_SIZE),
            )
        } else {
            self.cached_aabb_scaled
                .expect("compute_local_aabb called before bounds were set")
        }
    }

    fn compute_local_bounding_sphere(&self) -> BoundingSphere {
        if self.is_static {
            BoundingSphere::new(Vec3::ZERO, WORLD_SIZE)
        } else {
            BoundingSphere::new(Vec3::ZERO, 1.0)
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

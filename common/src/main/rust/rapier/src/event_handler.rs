use crate::collider::LevelCollider;
use crate::{PHYSICS_STATE, ReportedCollision};
use rapier3d::dynamics::RigidBodySet;
use rapier3d::geometry::{ColliderSet, CollisionEvent, ContactPair};
use rapier3d::na::Vector3;
use rapier3d::pipeline::EventHandler;
use rapier3d::prelude::*;

pub struct SableEventHandler {
    pub scene_id: i32,
}

impl EventHandler for SableEventHandler {
    fn handle_collision_event(
        &self,
        _bodies: &RigidBodySet,
        _colliders: &ColliderSet,
        _event: CollisionEvent,
        _contact_pair: Option<&ContactPair>,
    ) {
    }

    fn handle_contact_force_event(
        &self,
        _dt: Real,
        _bodies: &RigidBodySet,
        colliders: &ColliderSet,
        contact_pair: &ContactPair,
        total_force_magnitude: Real,
    ) {
        let Some(state) = (unsafe { &mut PHYSICS_STATE }) else {
            panic!("no physics state!")
        };

        let Some(scene) = state.scenes.get_mut(&self.scene_id) else {
            panic!("No scene with given ID!");
        };

        if total_force_magnitude < 0.1 {
            return;
        }

        for manifold in contact_pair.manifolds.iter() {
            for point in manifold.points.iter() {
                let collider_a = colliders.get(contact_pair.collider1).unwrap();
                let collider_b = colliders.get(contact_pair.collider2).unwrap();
                let Some(level_collider_a) = collider_a.shape().as_shape::<LevelCollider>() else {
                    continue;
                };
                let Some(level_collider_b) = collider_b.shape().as_shape::<LevelCollider>() else {
                    continue;
                };

                let local_n1 = manifold.local_n1;
                let local_n2 = manifold.local_n2;

                let local_p1 = point.local_p1;
                let local_p2 = point.local_p2;

                let collision = ReportedCollision {
                    body_a: level_collider_a.id,
                    body_b: level_collider_b.id,
                    force_amount: total_force_magnitude as f64,
                    local_normal_a: Vector3::<f64>::new(
                        local_n1.x as f64,
                        local_n1.y as f64,
                        local_n1.z as f64,
                    ),
                    local_normal_b: Vector3::<f64>::new(
                        local_n2.x as f64,
                        local_n2.y as f64,
                        local_n2.z as f64,
                    ),
                    local_point_a: Vector3::<f64>::new(
                        local_p1.x as f64,
                        local_p1.y as f64,
                        local_p1.z as f64,
                    ),
                    local_point_b: Vector3::<f64>::new(
                        local_p2.x as f64,
                        local_p2.y as f64,
                        local_p2.z as f64,
                    ),
                };

                scene.reported_collisions.push(collision);
            }
        }
    }
}

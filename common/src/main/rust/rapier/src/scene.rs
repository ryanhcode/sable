use crate::event_handler::SableEventHandler;
use crate::hooks::SablePhysicsHooks;
use crate::joints::SableJointSet;
use crate::rope::RopeMap;
use crate::{ActiveLevelColliderInfo, ReportedCollision};
use dashmap::DashMap;
use jni::JavaVM;
use marten::Real;
use marten::level::{ChunkSection, OctreeChunkSection};
use rapier3d::dynamics::{
    CCDSolver, ImpulseJointSet, IslandManager, MultibodyJointSet, RigidBodyHandle, RigidBodySet,
};
use rapier3d::geometry::{ColliderSet, DefaultBroadPhase, NarrowPhase};
use rapier3d::math::Vector;
use rapier3d::na::Vector3;
use rapier3d::pipeline::PhysicsPipeline;
use std::collections::HashMap;
use std::sync::atomic::AtomicUsize;

pub type LevelColliderID = usize;

pub trait ChunkAccess {
    #[allow(unused)]
    fn get_chunk_mut(&mut self, x: i32, y: i32, z: i32) -> Option<&mut ChunkSection>;
    fn get_chunk(&self, x: i32, y: i32, z: i32) -> Option<&ChunkSection>;
}

#[inline(always)]
pub fn pack_section_pos(i: i32, j: i32, k: i32) -> i64 {
    let mut l: i64 = 0;
    l |= (i as i64 & 4194303i64) << 42;
    l |= j as i64 & 1048575i64;
    l | (k as i64 & 4194303i64) << 20
}

pub type ChunkMap = HashMap<i64, ChunkSection>;

/// A physics scene
pub struct PhysicsScene {
    pub scene_id: i32,
    pub pipeline: PhysicsPipeline,
    pub rigid_body_set: RigidBodySet,
    pub collider_set: ColliderSet,

    pub island_manager: IslandManager,
    pub broad_phase: DefaultBroadPhase,
    pub narrow_phase: NarrowPhase,
    pub impulse_joint_set: ImpulseJointSet,
    pub multibody_joint_set: MultibodyJointSet,
    pub ccd_solver: CCDSolver,
    pub physics_hooks: SablePhysicsHooks,
    pub event_handler: SableEventHandler,

    /// A 3-dimensional map of chunk sections for collision.
    /// chunk coordinates -> chunk section
    pub main_level_chunks: ChunkMap,
    pub octree_chunks: HashMap<i64, OctreeChunkSection>,

    /// All collisions substantial enough to be considered for collision events.
    pub reported_collisions: Vec<ReportedCollision>,

    /// The companion joint set
    pub joint_set: SableJointSet,

    /// Rope map
    pub rope_map: RopeMap,

    /// The handle to a static rigidbody
    pub ground_handle: Option<RigidBodyHandle>,

    /// A map of unique IDs -> rigid bodies for the Java side in sable to reference.
    pub level_colliders: HashMap<LevelColliderID, ActiveLevelColliderInfo>,
    pub rigid_bodies: HashMap<LevelColliderID, RigidBodyHandle>,
    pub current_step_vm: Option<JavaVM>,

    /// The current gravity vector for all bodies. [m/s^2]
    pub gravity: Vector,

    /// Universal linear drag applied to all bodies
    pub universal_drag: Real,

    /// Universal angular drag applied to all bodies
    pub manifold_info_map: SableManifoldInfoMap,
}
#[derive(Default)]
pub struct SableManifoldInfoMap {
    pub list: DashMap<usize, SableManifoldInfo>,
    pub counter: AtomicUsize,
}

pub struct SableManifoldInfo {
    pub pos_a: Vector3<i32>,
    pub pos_b: Vector3<i32>,
    pub col_a: usize,
    pub col_b: usize,
}

impl ChunkAccess for PhysicsScene {
    fn get_chunk_mut(&mut self, x: i32, y: i32, z: i32) -> Option<&mut ChunkSection> {
        self.main_level_chunks.get_mut(&pack_section_pos(x, y, z))
    }

    fn get_chunk(&self, x: i32, y: i32, z: i32) -> Option<&ChunkSection> {
        self.main_level_chunks.get(&pack_section_pos(x, y, z))
    }
}

impl PhysicsScene {
    pub fn get_octree_chunk(&self, x: i32, y: i32, z: i32) -> Option<&OctreeChunkSection> {
        self.octree_chunks.get(&pack_section_pos(x, y, z))
    }

    pub fn get_octree_chunk_mut(
        &mut self,
        x: i32,
        y: i32,
        z: i32,
    ) -> Option<&mut OctreeChunkSection> {
        self.octree_chunks.get_mut(&pack_section_pos(x, y, z))
    }
}

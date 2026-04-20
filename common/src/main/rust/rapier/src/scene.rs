use crate::dispatcher::SableDispatcher;
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
    ImpulseJointSet, IslandManager, MultibodyJointSet, RigidBodyHandle, RigidBodySet,
};
use rapier3d::geometry::{ColliderSet, DefaultBroadPhase, NarrowPhase};
use rapier3d::glamx::IVec3;

use rapier3d::math::Vec3;
use rapier3d::parry::query::{DefaultQueryDispatcher, QueryDispatcher};
use rapier3d::pipeline::PhysicsPipeline;
use std::collections::HashMap;
use std::sync::atomic::AtomicUsize;

pub type LevelColliderID = usize;

pub trait ChunkAccess {
    #[allow(unused)]
    fn get_chunk_mut(&mut self, pos: IVec3) -> Option<&mut ChunkSection>;
    fn get_chunk(&self, pos: IVec3) -> Option<&ChunkSection>;
}

#[inline(always)]
pub fn pack_section_pos(IVec3 { x: i, y: j, z: k }: IVec3) -> i64 {
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
    pub gravity: Vec3,

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
    pub pos_a: IVec3,
    pub pos_b: IVec3,
    pub col_a: usize,
    pub col_b: usize,
}

impl ChunkAccess for PhysicsScene {
    fn get_chunk_mut(&mut self, pos: IVec3) -> Option<&mut ChunkSection> {
        self.main_level_chunks.get_mut(&pack_section_pos(pos))
    }

    fn get_chunk(&self, pos: IVec3) -> Option<&ChunkSection> {
        self.main_level_chunks.get(&pack_section_pos(pos))
    }
}

impl PhysicsScene {
    pub fn new(scene_id: i32, universal_drag: f32, gravity: Vec3) -> Self {
        Self {
            scene_id,
            pipeline: PhysicsPipeline::default(),
            rigid_body_set: RigidBodySet::default(),
            collider_set: ColliderSet::default(),
            island_manager: IslandManager::default(),
            broad_phase: DefaultBroadPhase::default(),
            narrow_phase: NarrowPhase::with_query_dispatcher(
                SableDispatcher.chain(DefaultQueryDispatcher),
            ),
            impulse_joint_set: ImpulseJointSet::default(),
            multibody_joint_set: MultibodyJointSet::default(),
            physics_hooks: SablePhysicsHooks,
            event_handler: SableEventHandler { scene_id },
            main_level_chunks: HashMap::<i64, ChunkSection>::default(),
            octree_chunks: HashMap::<i64, OctreeChunkSection>::default(),
            reported_collisions: Vec::with_capacity(16),
            joint_set: SableJointSet::default(),
            ground_handle: None,
            rope_map: RopeMap::default(),
            level_colliders: HashMap::<LevelColliderID, ActiveLevelColliderInfo>::default(),
            rigid_bodies: HashMap::<LevelColliderID, RigidBodyHandle>::default(),
            current_step_vm: None,
            gravity,
            universal_drag,
            manifold_info_map: SableManifoldInfoMap::default(),
        }
    }
    pub fn get_octree_chunk(&self, pos: IVec3) -> Option<&OctreeChunkSection> {
        self.octree_chunks.get(&pack_section_pos(pos))
    }

    pub fn get_octree_chunk_mut(&mut self, pos: IVec3) -> Option<&mut OctreeChunkSection> {
        self.octree_chunks.get_mut(&pack_section_pos(pos))
    }
}

//! A sparse voxel world.

use crate::octree::SubLevelOctree;
use glamx::{IVec3, Vec3};
use jni::JNIEnv;
use jni::descriptors::Desc;
use jni::objects::{GlobalRef, JMethodID};
use jni::sys::jdouble;

/// log_2 of the size of a chunk
pub const CHUNK_SHIFT: u8 = 4;

/// The size of a chunk in blocks
pub const CHUNK_SIZE: u8 = 1 << CHUNK_SHIFT;

/// Bitwise mask to apply to a block coordinate to get the chunk-relative coordinate of the block
pub const CHUNK_MASK: i32 = (CHUNK_SIZE - 1) as i32;

/// (Block Physics ID, Voxel Physics State)
pub type BlockState = (u32, VoxelPhysicsState);

/// A 16x16x16 voxel chunk.
/// Blocks are stored in xzy order. (x fastest changing)
#[derive(Debug)]
pub struct ChunkSection {
    blocks: Vec<BlockState>,
}

impl ChunkSection {
    /// Creates a new chunk section with the given blocks.
    pub fn new(blocks: Vec<BlockState>) -> Self {
        // Panic if the block count is invalid
        if blocks.len() != (CHUNK_SIZE as usize).pow(3) {
            panic!("Invalid block count: {}", blocks.len());
        }

        Self { blocks }
    }

    /// Computes the index of a coordinate inside the chunk.
    ///
    /// # Safety
    /// This method assumes that the coordinate is > than 0 and < than `CHUNK_SIZE` on all axes.
    #[inline(always)]
    fn get_index(&self, IVec3 { x, y, z }: IVec3) -> usize {
        (x + (z << 4) + (y << 8)) as usize
    }

    /// Sets the block at the given coordinate.
    ///
    /// # Safety
    /// This method assumes that the coordinate is > than 0 and < than `CHUNK_SIZE` on all axes.
    /// If the coordinate is out of bounds, behavior is undefined.
    pub fn set_block(&mut self, pos: IVec3, state: BlockState) {
        let index = self.get_index(pos);
        self.blocks[index] = state;
    }

    /// Gets the block at the given coordinate.
    ///
    /// # Safety
    /// This method assumes that the coordinate is >= than 0 and < than `CHUNK_SIZE` on all axes.
    /// If the coordinate is out of bounds, behavior is undefined.
    pub fn get_block(&self, pos: IVec3) -> BlockState {
        let index = self.get_index(pos);
        unsafe { *self.blocks.get_unchecked(index) }
    }
}

#[derive(Debug)]
pub struct SableMethodID(pub JMethodID);

unsafe impl<'local> Desc<'local, JMethodID> for &SableMethodID {
    type Output = JMethodID;

    fn lookup(self, _env: &mut JNIEnv<'local>) -> jni::errors::Result<Self::Output> {
        Ok(self.0)
    }
}

#[derive(Debug, Clone, Copy)]
pub struct CollisionBox {
    pub min: Vec3,
    pub max: Vec3,
}
impl From<[jdouble; 6]> for CollisionBox {
    fn from(value: [jdouble; 6]) -> Self {
        let [min_x, min_y, min_z, max_x, max_y, max_z] = value.map(|v| v as f32);
        Self {
            min: Vec3::new(min_x, min_y, min_z),
            max: Vec3::new(max_x, max_y, max_z),
        }
    }
}

/// The physics data of a blockstate
#[derive(Debug)]
pub struct VoxelColliderData {
    /// Collision boxes within the 0-1 voxel space.
    pub collision_boxes: Vec<CollisionBox>,

    /// If this should be treated as a fluid for buoyancy
    pub is_fluid: bool,

    /// The friction multiplier
    pub friction: f32,
    pub volume: f32,
    pub restitution: f32,

    /// If this block has special contact behavior
    pub contact_events: Option<GlobalRef>,

    pub contact_method: Option<SableMethodID>,

    pub dynamic: bool,
}

pub const NEEDS_HOOKS_USER_DATA: u32 = 1;
pub const NO_HOOKS_USER_DATA: u32 = 0;

impl VoxelColliderData {
    pub fn get_user_data(&self) -> u32 {
        if self.contact_events.is_some() || self.friction != 1.0 || self.restitution != 0.0 {
            NEEDS_HOOKS_USER_DATA
        } else {
            NO_HOOKS_USER_DATA
        }
    }

    pub fn needs_hooks(data: u32) -> bool {
        data & NEEDS_HOOKS_USER_DATA > 0
    }
}

pub const OCTREE_CHUNK_SHIFT: i32 = 6;
pub const OCTREE_CHUNK_SIZE: i32 = 1 << OCTREE_CHUNK_SHIFT;

pub struct OctreeChunkSection {
    pub octree: SubLevelOctree,
    pub liquid_octree: SubLevelOctree,
}

impl OctreeChunkSection {
    pub fn new() -> Self {
        Self {
            octree: SubLevelOctree::new(OCTREE_CHUNK_SHIFT),
            liquid_octree: SubLevelOctree::new(OCTREE_CHUNK_SHIFT),
        }
    }
}

impl Default for OctreeChunkSection {
    fn default() -> Self {
        Self::new()
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum VoxelPhysicsState {
    Empty,
    Face,
    Edge,
    Corner,
    Interior,
}

pub const ALL_VOXEL_PHYSICS_STATES: [VoxelPhysicsState; 5] = [
    VoxelPhysicsState::Empty,
    VoxelPhysicsState::Face,
    VoxelPhysicsState::Edge,
    VoxelPhysicsState::Corner,
    VoxelPhysicsState::Interior,
];

use std::collections::HashMap;

use jni::objects::GlobalRef;
use jni::sys::{jboolean, jdouble, jint};
use marten::Real;
use marten::level::{SableMethodID, VoxelColliderData};
use rapier3d::na::Vector3;

use crate::get_physics_state_mut;

type IVec3 = Vector3<i32>;

/// The physics data of a blockstate
#[derive(Debug)]
pub struct VoxelColliderMap {
    pub(crate) voxel_colliders: Vec<Option<VoxelColliderData>>,
    dynamic_colliders: HashMap<IVec3, Option<VoxelColliderData>>,
}

impl VoxelColliderMap {
    pub fn new() -> Self {
        Self {
            voxel_colliders: Vec::new(),
            dynamic_colliders: HashMap::new(),
        }
    }

    pub fn get(&self, index: usize, block_pos: IVec3) -> Option<&VoxelColliderData> {
        let collider = &self.voxel_colliders[index];

        if collider.is_some() && collider.as_ref().unwrap().dynamic {
            let dynamic_collider = self.dynamic_colliders.get(&block_pos);

            if let Some(data) = dynamic_collider {
                return data.as_ref();
            }
        }

        collider.as_ref()
    }
}

pub fn new_voxel_collider(
    friction: jdouble,
    volume: jdouble,
    restitution: jdouble,
    is_fluid: jboolean,
    dynamic: jboolean,
    global_ref: Option<GlobalRef>,
    global_method: Option<SableMethodID>,
) -> jint {
    let state = unsafe { get_physics_state_mut() };

    let next_index = state.voxel_collider_map.voxel_colliders.len();
    state
        .voxel_collider_map
        .voxel_colliders
        .push(Some(VoxelColliderData {
            collision_boxes: Vec::new(),
            is_fluid: is_fluid > 0,
            friction: friction as Real,
            volume: volume as Real,
            restitution: restitution as Real,
            contact_events: global_ref,
            contact_method: global_method,
            dynamic: dynamic > 0,
        }));

    next_index as jint
}

pub fn add_voxel_collider_box(index: jint, bounds: [jdouble; 6]) {
    let state = unsafe { get_physics_state_mut() };
    if let Some(data) = &mut state.voxel_collider_map.voxel_colliders[index as usize] {
        data.collision_boxes.push((
            bounds[0] as f32,
            bounds[1] as f32,
            bounds[2] as f32,
            bounds[3] as f32,
            bounds[4] as f32,
            bounds[5] as f32,
        ));
    }
}

pub fn clear_voxel_collider_boxes(index: jint) {
    let state = unsafe { get_physics_state_mut() };

    if let Some(data) = &mut state.voxel_collider_map.voxel_colliders[index as usize] {
        data.collision_boxes.clear()
    }
}

use std::cmp::min;

use marten::Real;
use marten::level::OCTREE_CHUNK_SHIFT;
use rapier3d::glamx::{DVec3, IVec3};
use rapier3d::math::{Pose3, Vec3};

use rapier3d::na::SimdComplexField;
use rayon::iter::ParallelIterator;
use rayon::prelude::{IntoParallelRefIterator, ParallelExtend};

use crate::scene::{PhysicsScene, pack_section_pos};
use crate::{ActiveLevelColliderInfo, get_scene_mut};

pub const DEFAULT_COLLISION_PARALLEL_CUTOFF: usize = 256;

/// Detects the collision pairs of a sable body
pub fn find_collision_pairs(
    sable_body: &ActiveLevelColliderInfo,
    other_sable_body: Option<&ActiveLevelColliderInfo>,
    isometry: &Pose3,
    prediction: Real,
    cutoff: usize,
    liquid: bool,
) -> Vec<(IVec3, IVec3)> {
    #[derive(Default)]
    struct StackObject {
        index: u32,
        depth: u32,
        min: IVec3,
    }

    let Some(octree) = &sable_body.octree else {
        panic!("No octree!")
    };

    let local_bounds_min = sable_body.local_bounds_min.unwrap();

    let center_of_mass = sable_body.center_of_mass.unwrap();

    let offset = local_bounds_min.as_dvec3() - center_of_mass;

    let offset = isometry.rotation.mul_vec3(offset.as_vec3());
    let translation = isometry.translation + offset;

    // start with the root node
    let mut current_level = Vec::with_capacity(128);

    let com_offset: DVec3 = other_sable_body
        .map(|body| body.center_of_mass.unwrap())
        .unwrap_or(DVec3::ZERO);

    current_level.push(StackObject::default());

    let mut pairs = Vec::with_capacity(16);
    // process nodes level by level to maintain some structure while parallelizing
    while !current_level.is_empty() {
        type LevelData = (Option<Vec<StackObject>>, Option<Vec<(IVec3, IVec3)>>);
        let mut next_level_data = Vec::<LevelData>::with_capacity(8);

        let do_level_parallel = current_level.len() >= cutoff;

        let process_stack_object = |entry: &StackObject| -> LevelData {
            let node = *unsafe { octree.buffer.get_unchecked(entry.index as usize) };
            let node_size: i32 = 1 << (octree.log_size as u32 - entry.depth);

            // Calculate the center and radius for this node
            let node_center = entry.min.as_vec3() + (node_size as f32 / 2.0);

            let transformed_center = isometry.rotation.mul_vec3(node_center) + translation;
            let radius = (node_size as Real / 2.0 * 1.7321) + prediction;

            let scene = get_scene_mut(sable_body.scene_id);

            let (has_any_intersections, blocks_opt) = get_overlapping_nodes(
                other_sable_body,
                com_offset,
                transformed_center,
                radius,
                scene,
                node >= 0,
                liquid,
            );

            if !has_any_intersections {
                return (None, None);
            }

            // leaf node - add collision pairs
            if node < 0 {
                let mut local_pairs = Vec::new();
                for static_block in blocks_opt.unwrap().iter() {
                    local_pairs.push((*static_block, entry.min + local_bounds_min));
                }

                return (None, Some(local_pairs));
            }

            if node > 0 {
                let mut local_next_level = Vec::with_capacity(8);

                for i in 0..8 {
                    local_next_level.push(StackObject {
                        index: (node + i) as u32,
                        depth: entry.depth + 1,
                        min: entry.min
                            + IVec3::new(
                                (i & 1) * node_size / 2,
                                ((i >> 1) & 1) * node_size / 2,
                                ((i >> 2) & 1) * node_size / 2,
                            ),
                    });
                }

                (Some(local_next_level), None)
            } else {
                (None, None)
            }
        };

        if do_level_parallel {
            next_level_data.par_extend(current_level.par_iter().map(process_stack_object))
        } else {
            next_level_data.extend(current_level.iter().map(process_stack_object))
        }

        let (a_parts, b_parts): (Vec<_>, Vec<_>) = next_level_data.into_iter().unzip();

        // filter out none's and add them
        for local_pairs in b_parts.into_iter().flatten() {
            pairs.extend(local_pairs);
        }

        current_level = a_parts.into_iter().flatten().flatten().collect();
    }

    pairs
}

fn get_overlapping_nodes(
    other_handle: Option<&ActiveLevelColliderInfo>,
    com_offset: DVec3,
    pos: Vec3,
    dist: Real,
    scene: &PhysicsScene,
    cancel_early: bool,
    liquid: bool,
) -> (bool, Option<Vec<IVec3>>) {
    // biggest power of two that doesn't go over radius
    let log2 = ((dist * 2.0).simd_ln() / 2.0f32.simd_ln()).floor() as i32;

    let log2 = if let Some(other_handle) = other_handle {
        let Some(oct) = &other_handle.octree else {
            panic!("No octree!")
        };
        min(log2, oct.log_size)
    } else {
        min(log2, OCTREE_CHUNK_SHIFT)
    };
    let min_block_pos = ((pos - dist).as_dvec3() + com_offset).floor().as_ivec3();

    let max_block_pos = ((pos + dist).as_dvec3() + com_offset).floor().as_ivec3();

    if let Some(other_handle) = other_handle {
        let other_min = other_handle.local_bounds_min.unwrap();

        let min_pos = ((min_block_pos - other_min) >> log2).max(IVec3::ZERO);
        let max_pos = (max_block_pos - other_min) >> log2;

        let Some(oct) = &other_handle.octree else {
            panic!("No octree!")
        };

        let mut blocks = if cancel_early {
            None
        } else {
            Some(Vec::with_capacity(16))
        };
        for x in min_pos.x..=max_pos.x {
            for y in min_pos.y..=max_pos.y {
                for z in min_pos.z..=max_pos.z {
                    let lp = IVec3::new(x, y, z) << log2;
                    if oct.query(lp, log2) > -2 {
                        if cancel_early {
                            return (true, None);
                        } else {
                            blocks.as_mut().unwrap().push(lp + other_min);
                        }
                    }
                }
            }
        }

        if cancel_early {
            return (false, None);
        } else {
            return (!blocks.as_ref().unwrap().is_empty(), blocks);
        }
    }

    // find all the octrees
    let [min_octree_pos, max_octree_pos] =
        [min_block_pos, max_block_pos].map(|v| v >> OCTREE_CHUNK_SHIFT);

    let mut blocks = cancel_early.then(|| Vec::with_capacity(8));

    for ox in min_octree_pos.x..=max_octree_pos.x {
        for oy in min_octree_pos.y..=max_octree_pos.y {
            for oz in min_octree_pos.z..=max_octree_pos.z {
                let opos = IVec3::new(ox, oy, oz);
                let chunk = scene.octree_chunks.get(&pack_section_pos(opos));
                let Some(chunk) = chunk else {
                    continue;
                };
                let min = min_block_pos >> log2;
                let max = max_block_pos >> log2;

                let chunk_octree = if liquid {
                    &chunk.liquid_octree
                } else {
                    &chunk.octree
                };
                let olp = opos << OCTREE_CHUNK_SHIFT;
                for x in min.x..=max.x {
                    for y in min.y..=max.y {
                        for z in min.z..=max.z {
                            let lp = IVec3::new(x, y, z) << log2;
                            let query = lp - olp;
                            if chunk_octree.query(query, log2) > -2 {
                                if cancel_early {
                                    return (true, None);
                                } else {
                                    blocks.as_mut().unwrap().push(lp);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if cancel_early {
        (false, None)
    } else {
        (!blocks.as_ref().unwrap().is_empty(), blocks)
    }
}

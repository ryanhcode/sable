use std::cmp::min;

use marten::Real;
use marten::level::OCTREE_CHUNK_SHIFT;
use rapier3d::glamx::Pose3;
use rapier3d::math::Vector;
use rapier3d::na::{SimdComplexField, Vector3};
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
) -> Vec<(Vector3<i32>, Vector3<i32>)> {
    struct StackObject {
        index: u32,
        depth: u32,
        min: Vector3<i32>,
    }

    let Some(octree) = &sable_body.octree else {
        panic!("No octree!")
    };

    let local_bounds_min = sable_body.local_bounds_min.unwrap();

    let center_of_mass = sable_body.center_of_mass.unwrap();

    let offset = Vector3::new(
        local_bounds_min.x as f64 - center_of_mass.x,
        local_bounds_min.y as f64 - center_of_mass.y,
        local_bounds_min.z as f64 - center_of_mass.z,
    );
    let offset = Vector3::new(offset.x as Real, offset.y as Real, offset.z as Real);

    let offset = isometry.rotation.mul_vec3(offset.into());
    let translation = isometry.translation + offset;

    // start with the root node
    let mut current_level = Vec::with_capacity(128);

    let com_offset: Vector3<f64> = if let Some(other_handle) = other_sable_body {
        let com = other_handle.center_of_mass.unwrap();
        Vector3::new(com.x, com.y, com.z)
    } else {
        Vector3::new(0.0, 0.0, 0.0)
    };

    current_level.push(StackObject {
        index: 0,
        depth: 0,
        min: Vector3::new(0, 0, 0),
    });

    let mut pairs = Vec::with_capacity(16);
    // process nodes level by level to maintain some structure while parallelizing
    while !current_level.is_empty() {
        type LevelData = (
            Option<Vec<StackObject>>,
            Option<Vec<(Vector3<i32>, Vector3<i32>)>>,
        );
        let mut next_level_data = Vec::<LevelData>::with_capacity(8);

        let do_level_parallel = current_level.len() >= cutoff;

        let process_stack_object = |entry: &StackObject| -> LevelData {
            let node = *unsafe { octree.buffer.get_unchecked(entry.index as usize) };
            let node_size = 1 << (octree.log_size as u32 - entry.depth);

            // Calculate the center and radius for this node
            let node_center = Vector3::new(
                entry.min.x as Real + node_size as Real / 2.0,
                entry.min.y as Real + node_size as Real / 2.0,
                entry.min.z as Real + node_size as Real / 2.0,
            );
            let node_center = Vector::new(node_center.x, node_center.y, node_center.z);
            let transformed_center = isometry.rotation.mul_vec3(node_center) + translation;
            let radius = node_size as Real / 2.0 * 1.7321 + prediction;

            let scene = get_scene_mut(sable_body.scene_id);

            let (has_any_intersections, blocks_opt) = get_overlapping_nodes(
                other_sable_body,
                com_offset,
                transformed_center.into(),
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
                            + Vector3::new(
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
    com_offset: Vector3<f64>,
    pos: Vector3<Real>,
    dist: Real,
    scene: &PhysicsScene,
    cancel_early: bool,
    liquid: bool,
) -> (bool, Option<Vec<Vector3<i32>>>) {
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

    let min_block_pos = Vector3::new(
        ((pos.x - dist) as f64 + com_offset.x).floor() as i32,
        ((pos.y - dist) as f64 + com_offset.y).floor() as i32,
        ((pos.z - dist) as f64 + com_offset.z).floor() as i32,
    );
    let max_block_pos = Vector3::new(
        ((pos.x + dist) as f64 + com_offset.x).floor() as i32,
        ((pos.y + dist) as f64 + com_offset.y).floor() as i32,
        ((pos.z + dist) as f64 + com_offset.z).floor() as i32,
    );

    if let Some(other_handle) = other_handle {
        let other_min = other_handle.local_bounds_min.unwrap();

        let min_pos = Vector3::new(
            (min_block_pos.x - other_min.x) >> log2,
            (min_block_pos.y - other_min.y) >> log2,
            (min_block_pos.z - other_min.z) >> log2,
        )
        .map(|x| x.max(0));

        let max_pos = Vector3::new(
            (max_block_pos.x - other_min.x) >> log2,
            (max_block_pos.y - other_min.y) >> log2,
            (max_block_pos.z - other_min.z) >> log2,
        );

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
                    if oct.query(x << log2, y << log2, z << log2, log2) > -2 {
                        if cancel_early {
                            return (true, None);
                        } else {
                            blocks.as_mut().unwrap().push(Vector3::new(
                                (x << log2) + other_min.x,
                                (y << log2) + other_min.y,
                                (z << log2) + other_min.z,
                            ));
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
    let min_octree_pos = Vector3::new(
        min_block_pos.x >> OCTREE_CHUNK_SHIFT,
        min_block_pos.y >> OCTREE_CHUNK_SHIFT,
        min_block_pos.z >> OCTREE_CHUNK_SHIFT,
    );
    let max_octree_pos = Vector3::new(
        max_block_pos.x >> OCTREE_CHUNK_SHIFT,
        max_block_pos.y >> OCTREE_CHUNK_SHIFT,
        max_block_pos.z >> OCTREE_CHUNK_SHIFT,
    );

    let mut blocks = if cancel_early {
        None
    } else {
        Some(Vec::with_capacity(8))
    };
    for ox in min_octree_pos.x..=max_octree_pos.x {
        for oy in min_octree_pos.y..=max_octree_pos.y {
            for oz in min_octree_pos.z..=max_octree_pos.z {
                let chunk = scene.octree_chunks.get(&pack_section_pos(ox, oy, oz));
                let Some(chunk) = chunk else {
                    continue;
                };

                let min_x = min_block_pos.x >> log2;
                let min_y = min_block_pos.y >> log2;
                let min_z = min_block_pos.z >> log2;
                let max_x = max_block_pos.x >> log2;
                let max_y = max_block_pos.y >> log2;
                let max_z = max_block_pos.z >> log2;
                let chunk_octree = if liquid {
                    &chunk.liquid_octree
                } else {
                    &chunk.octree
                };

                for x in min_x..=max_x {
                    for y in min_y..=max_y {
                        for z in min_z..=max_z {
                            if chunk_octree.query(
                                (x << log2) - (ox << OCTREE_CHUNK_SHIFT),
                                (y << log2) - (oy << OCTREE_CHUNK_SHIFT),
                                (z << log2) - (oz << OCTREE_CHUNK_SHIFT),
                                log2,
                            ) > -2
                            {
                                if cancel_early {
                                    return (true, None);
                                } else {
                                    blocks.as_mut().unwrap().push(Vector3::new(
                                        x << log2,
                                        y << log2,
                                        z << log2,
                                    ));
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

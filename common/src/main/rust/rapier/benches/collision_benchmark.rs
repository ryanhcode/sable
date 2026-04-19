use criterion::{Criterion, criterion_group, criterion_main};
use marten::Real;
use marten::octree::SubLevelOctree;
use rapier3d::math::Pose3;
use rapier3d::na::Vector3;
use rapier3d::prelude::ColliderHandle;
use sable_rapier::ActiveLevelColliderInfo;
use sable_rapier::algo::{DEFAULT_COLLISION_PARALLEL_CUTOFF, find_collision_pairs};
use std::hint::black_box;

fn setup_dummy_sable_handle_a() -> ActiveLevelColliderInfo {
    let mut octree = SubLevelOctree::new(7);
    setup_sphere(&mut octree);

    ActiveLevelColliderInfo {
        static_mount: None,
        collider: ColliderHandle::default(),
        local_bounds_min: Some(Vector3::<i32>::new(0, 0, 0)),
        local_bounds_max: Some(Vector3::<i32>::new(128, 128, 128)),
        center_of_mass: Some(Vector3::<f64>::new(62.5, 62.5, 62.5)),
        octree: Some(octree),
        chunk_map: None,
        scene_id: 0,
        fake_velocities: None,
    }
}

fn setup_dummy_sable_handle_b() -> ActiveLevelColliderInfo {
    let mut octree = SubLevelOctree::new(7);
    setup_sphere(&mut octree);

    ActiveLevelColliderInfo {
        static_mount: None,
        collider: ColliderHandle::default(),
        local_bounds_min: Some(Vector3::<i32>::new(128, 0, 0)),
        local_bounds_max: Some(Vector3::<i32>::new(256, 128, 128)),
        center_of_mass: Some(Vector3::<f64>::new(128.0 + 64.5, 64.5, 64.5)),
        octree: Some(octree),
        chunk_map: None,
        scene_id: 0,
        fake_velocities: None,
    }
}

fn setup_sphere(octree: &mut SubLevelOctree) {
    // sphere
    for x in 0..128 {
        for y in 0..128 {
            for z in 0..128 {
                let dx = x as f64 - 64.5;
                let dy = y as f64 - 64.5;
                let dz = z as f64 - 64.5;
                if dx * dx + dy * dy + dz * dz <= 63.5 * 63.5 {
                    octree.insert(x, y, z, 1);
                }
            }
        }
    }
}

fn benchmark_find_collision_pairs(c: &mut Criterion) {
    let sable_body_a = setup_dummy_sable_handle_a();
    let sable_body_b = setup_dummy_sable_handle_b();
    let prediction = 0.0 as Real;

    // for i in 0..100000 {
    //     let isometry = Isometry3::new(Vector3::new(123.5, 0.0, 0.0), Vector3::new(0.0, 0.0, 0.0)); // No rotation/translation
    //     let result = find_collision_pairs(&sable_body_a, Some(&sable_body_b), &isometry, prediction, 256);
    //     black_box(result);
    //
    // }
    //
    // if (true){ return;}

    // {
    //     let mut group = c.benchmark_group("find_pairs_parallel_cutoff");
    //
    //     for cutoff in [0, 64, 128, 256, 512, 1024, 2048, 4096].iter() {
    //         group.throughput(criterion::Throughput::Elements(1));
    //         group.bench_with_input(criterion::BenchmarkId::from_parameter(cutoff), cutoff, |b, &penetration| {
    //             let isometry = Isometry3::new(Vector3::new(120.0, 0.0, 0.0), Vector3::new(0.0, 0.0, 0.0)); // No rotation/translation
    //             b.iter(|| {
    //                 let result = find_collision_pairs(&sable_body_a, Some(&sable_body_b), &isometry, prediction, *cutoff as usize);
    //                 black_box(result)
    //             });
    //         });
    //     }
    // }
    //
    //
    // if (true){ return; }
    {
        let mut group = c.benchmark_group("find_pairs_parallel");

        for penetration in [0.0, 0.5, 1.0].iter() {
            group.throughput(criterion::Throughput::Elements(1));
            group.bench_with_input(
                criterion::BenchmarkId::from_parameter(penetration),
                penetration,
                |b, &penetration| {
                    let pose = Pose3::translation(124.25 - penetration * 8.0, 0.0, 0.0);
                    b.iter(|| {
                        let result = find_collision_pairs(
                            &sable_body_a,
                            Some(&sable_body_b),
                            &pose,
                            prediction,
                            DEFAULT_COLLISION_PARALLEL_CUTOFF,
                            false,
                        );
                        black_box(result)
                    });
                },
            );
        }
    }

    {
        let mut group = c.benchmark_group("find_pairs_sequential");

        for penetration in [0.0, 0.5, 1.0].iter() {
            group.throughput(criterion::Throughput::Elements(1));
            group.bench_with_input(
                criterion::BenchmarkId::from_parameter(penetration),
                penetration,
                |b, &penetration| {
                    let pose = Pose3::translation(124.25 - penetration * 8.0, 0.0, 0.0);
                    b.iter(|| {
                        let result = find_collision_pairs(
                            &sable_body_a,
                            Some(&sable_body_b),
                            &pose,
                            prediction,
                            usize::MAX,
                            false,
                        );
                        black_box(result)
                    });
                },
            );
        }
    }

    // criterion_group!(benches, benchmark_find_collision_pairs);
    // criterion_main!(benches);
    // c.bench_function("find_collision_pairs_parallel", |b| {
    //     b.iter(|| {
    //         let result = find_collision_pairs(&sable_body_a, Some(&sable_body_b), &isometry, prediction);
    //         black_box(result)
    //     })
    // });

    // c.bench_function("find_collision_pairs_sequential", |b| {
    //     b.iter(|| {
    //         let result = find_collision_pairs_sequential(&sable_body_a, Some(&sable_body_b), &isometry, prediction);
    //         black_box(result)
    //     })
    // });
}

criterion_group!(benches, benchmark_find_collision_pairs);
criterion_main!(benches);

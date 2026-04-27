use jni::JNIEnv;
use jni::objects::JClass;
use jni::sys::{jdouble, jint};
use marten::Real;

use crate::PHYSICS_STATE;

/// Global spring frequency for joints (Hz)
pub const JOINT_SPRING_FREQUENCY: Real = 550.0;

/// Global damping ratio for joints
pub const JOINT_SPRING_DAMPING_RATIO: Real = 4.0;

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_configFrequencyAndDamping<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    collision_natural_frequency: jdouble,
    collision_damping_ratio: jdouble,
) {
    unsafe {
        if let Some(state) = &mut PHYSICS_STATE {
            state
                .integration_parameters
                .contact_softness
                .natural_frequency = collision_natural_frequency as Real;
            state.integration_parameters.contact_softness.damping_ratio =
                collision_damping_ratio as Real;
        } else {
            panic!("No physics state!");
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_configSolverIterations<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    num_solver_iterations: jint,
    num_internal_pgs_iterations: jint,
    num_internal_stabilization_iterations: jint,
) {
    unsafe {
        if let Some(state) = &mut PHYSICS_STATE {
            state.integration_parameters.num_solver_iterations = num_solver_iterations as usize;
            state.integration_parameters.num_internal_pgs_iterations =
                num_internal_pgs_iterations as usize;
            state
                .integration_parameters
                .num_internal_stabilization_iterations =
                num_internal_stabilization_iterations as usize;
        } else {
            panic!("No physics state!");
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_ryanhcode_sable_physics_impl_rapier_Rapier3D_configMinIslandSize<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    island_size: jint,
) {
    unsafe {
        if let Some(state) = &mut PHYSICS_STATE {
            state.integration_parameters.min_island_size = island_size as usize;
        } else {
            panic!("No physics state!");
        }
    }
}

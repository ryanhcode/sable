use jni::sys::{jdouble, jint};
use marten::Real;

use crate::PHYSICS_STATE;

/// Global spring frequency for joints (Hz)
pub const JOINT_SPRING_FREQUENCY: Real = 550.0;

/// Global damping ratio for joints
pub const JOINT_SPRING_DAMPING_RATIO: Real = 4.0;

pub fn config_frequency_and_damping(
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

pub fn config_solver_iterations(
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

pub fn config_min_island_size(island_size: jint) {
    unsafe {
        if let Some(state) = &mut PHYSICS_STATE {
            state.integration_parameters.min_island_size = island_size as usize;
        } else {
            panic!("No physics state!");
        }
    }
}

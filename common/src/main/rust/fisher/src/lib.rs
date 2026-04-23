#[cfg(feature = "enabled")]
pub mod imp;

#[cfg(not(feature = "enabled"))]
#[macro_export]
macro_rules! decl_tracked_api {
    ( $( $any:tt )* ) => {};
}
#[cfg(not(feature = "enabled"))]
#[macro_export]
macro_rules! tracked_call {
        ( $( $any:tt )* ) => { $( $any )* };
    }
#[cfg(not(feature = "enabled"))]
pub fn setup_trace() {}

#[cfg(feature = "enabled")]
pub fn setup_trace() {
    use crate::imp::Writer;
    *imp::FISHER_WRITER.lock().unwrap() = Some(Writer::new("sable_trace.bin"));
}

use std::io::Write;

use crate::imp::FISHER_WRITER;

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

pub fn setup_trace() {
    use std::sync::Once;

    use crate::imp::Writer;
    static OPEN_FILE_ONCE: Once = Once::new();
    OPEN_FILE_ONCE
        .call_once(|| *imp::FISHER_WRITER.lock().unwrap() = Some(Writer::new("sable_trace.bin")));
}
pub fn finish_trace() {
    if let Some(mut v) = FISHER_WRITER.lock().unwrap().take() {
        v.flush().unwrap();
        drop(v);
    }
}

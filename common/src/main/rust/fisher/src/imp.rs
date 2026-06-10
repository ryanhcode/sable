use std::{
    array,
    fs::File,
    io::{self, BufWriter, Write},
    ops::{Deref, DerefMut},
    path::Path,
    sync::{Mutex, MutexGuard},
};
pub mod argument_encodings;
pub use argument_encodings::BorrowReader;

#[macro_export]
macro_rules! decl_tracked_api {
    (
        $v:vis mod $modname:ident { $($tok:ident),+ $(,)? }
    ) => {
        /// test
        #[expect(non_camel_case_types)]
        $v mod $modname {
            $(
                pub struct $tok;
            )+
            #[repr(u8)]
            #[derive(::std::fmt::Debug, ::std::marker::Copy, ::std::clone::Clone)]
            $v enum CallType {
                $( $tok, )+
                __Last,
            }
            impl CallType {
                pub fn new(id: u8) -> Self {
                    ::std::assert!(id < Self::__Last as u8);
                    unsafe { core::mem::transmute::<u8, Self>(id) }
                }
            }
            impl CallType {
                pub fn as_trackable(self) -> &'static dyn $crate::imp::Trackable {
                    match self {
                        $(
                            Self::$tok => const { $crate::imp::require_trackable( &$tok ) }
                        ),+
                        _ => ::std::unreachable!(),
                    }
                }
            }
        }
        macro_rules! __use_tracking_mod { () => { use self::$modname as __tracking; }; }
    };
}
pub const fn require_trackable<T: Trackable>(v: &'static T) -> &'static dyn Trackable {
    v
}
pub const fn require_argument_encoding<T: ArgumentEncode>(_: &T) {}

#[diagnostic::on_unimplemented(
    message = "token `{Self}` is declared but has no tracked call. Instrument a call of this token with `tracked_call!`",
    label = "this token has no corresponding tracked call",
    note = "all tokens must have a tracked call"
)]
pub trait Trackable {
    fn name(&self) -> &'static str;
    fn playback(&self, reader: &mut BorrowReader<'_>);
}
#[macro_export]
macro_rules! tracked_call {
    ( $f:ident( $($arg:expr),* $(,)?) ) => {
        {
            const TRACKED_ID: u8 = {
                __use_tracking_mod!();
                #[expect(non_local_definitions)]
                #[allow(unused_variables)]
                impl $crate::imp::Trackable for __tracking::$f {
                    fn name(&self) -> &'static str {
                        ::std::stringify!($f)
                    }
                    fn playback(&self, reader: &mut $crate::imp::BorrowReader) {
                        $f(
                            $(
                                {
                                    let _ = ::std::stringify!($arg);
                                    $crate::imp::ArgumentEncode::read(reader)
                                }
                            ),*
                        );
                    }
                }
                __tracking::CallType::$f as u8
            };

            let mut w = $crate::imp::get_writer();
            if w.is_some() {
                w.as_mut().unwrap().frame(TRACKED_ID).unwrap();
                let mut w_opt = Some(w);
                $crate::tracked_call! { is_last_arg($f, w_opt): () $( $arg ),* }
            } else {
                $f( $( $arg ),* )
            }
        }
    };
    // munch non-last arg
    (
        is_last_arg($f:ident, $w:ident): (
            $( ($out:expr, $last:literal) ),*
        )
        $arg:expr, $( $args:expr ),+
    ) => {
        $crate::tracked_call! {
            is_last_arg($f, $w): (
                $( ( $out, $last ), )*
                ($arg, false)
            )
            $( $args ),+
        }
    };
    // munch last arg
    (
        is_last_arg($f:ident, $w:ident): (
            $( ($out:expr, $last:literal) ),*
        )
        $arg:expr $(,)?
    ) => {
        $crate::tracked_call! {
            is_last_arg($f, $w): (
                $( ( $out, $last ), )*
                ($arg, true)
            )

        }
    };
    // call with some args
    ( is_last_arg($f:ident, $w:ident): ( $( ($arg:expr, $last:literal) ),+ ) ) => {
        $f(
            $(
                {
                    let arg = $arg;
                    $crate::imp::ArgumentEncode::write(&arg, &mut **($w.as_deref_mut().map(Option::as_mut).flatten().expect("lock to be held"))).expect("write to succeed");
                    if $last {
                        // release lock on recording
                        ::std::mem::drop(::std::mem::take(&mut $w));
                    }
                    arg
                }
            ),+
        )
    };
    // call with no args
    ( is_last_arg($f:ident, $w:ident): () ) => {
        ::std::mem::drop($w);
        $f()
    };

}

pub struct Writer(BufWriter<File>);
impl Deref for Writer {
    type Target = BufWriter<File>;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}
impl DerefMut for Writer {
    fn deref_mut(&mut self) -> &mut Self::Target {
        &mut self.0
    }
}
impl Writer {
    pub fn frame(&mut self, id: u8) -> io::Result<()> {
        self.write_all(array::from_ref(&id))
    }
    pub fn new(p: impl AsRef<Path>) -> Self {
        let f = File::options()
            .write(true)
            .create(true)
            .truncate(true)
            .open(p)
            .expect("trace file to open");
        Self(BufWriter::new(f))
    }
}
pub static FISHER_WRITER: Mutex<Option<Writer>> = const { Mutex::new(None) };

pub fn get_writer<'a>() -> MutexGuard<'a, Option<Writer>> {
    FISHER_WRITER.lock().expect("mutex not to be poisoned")
}

#[diagnostic::on_unimplemented(
    message = "tracked call parameter type `{Self}` doesn't implement `ArgumentEncode`",
    label = "this parameter doesn't implement `ArgumentEncode`",
    note = "in order to record and playback tracked calls all of their parameters must implement `ArgumentEncode`. Please add an implementation to `rust/fisher`"
)]
pub trait ArgumentEncode: Sized {
    fn read(reader: &mut BorrowReader<'_>) -> Self;
    fn write(&self, writer: &mut impl Write) -> io::Result<()>;
}

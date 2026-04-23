use std::{
    array,
    io::{self, Write},
};

use jni::{
    JavaVM,
    objects::GlobalRef,
    sys::{jboolean, jbyte, jchar, jdouble, jfloat, jint, jlong, jshort},
};
use marten::level::SableMethodID;

use super::ArgumentEncode;

/// Simple cursor that allows readers to temporarily borrow from its internal buffer
pub struct BorrowReader<'a> {
    bytes: &'a [u8],
    cursor: usize,
}
impl<'a> BorrowReader<'a> {
    fn get_bytes(&mut self, ct: usize) -> &'a [u8] {
        assert!(self.cursor + ct < self.bytes.len());
        self.cursor += ct;
        &self.bytes[self.cursor..self.cursor + ct]
    }
    fn get_bytes_const<const N: usize>(&mut self) -> &[u8; N] {
        self.get_bytes(N).first_chunk().unwrap()
    }
}

impl ArgumentEncode<'_> for () {
    fn read(_: &mut BorrowReader) -> Self {}
    fn write(&self, _: &mut impl Write) -> io::Result<()> {
        Ok(())
    }
}

macro_rules! numeric_argument_encodings {
    ( $( $intty:ty ),+ ) => {
        $(
            impl ArgumentEncode<'_> for $intty {
                fn read(reader: &mut BorrowReader<'_>) -> Self {
                    <$intty>::from_le_bytes(*reader.get_bytes_const())
                }

                fn write(&self, writer: &mut impl Write) -> io::Result<()> {
                    writer.write_all(&self.to_le_bytes())
                }
            }
        )+
    };
}
numeric_argument_encodings!(jboolean, jbyte, jchar, jshort, jint, jlong, jfloat, jdouble);

impl<'a, const N: usize, T: ArgumentEncode<'a>> ArgumentEncode<'a> for [T; N] {
    fn read(reader: &mut BorrowReader<'a>) -> Self {
        array::from_fn(|_| T::read(reader))
    }

    fn write(&self, writer: &mut impl Write) -> io::Result<()> {
        for i in self.iter() {
            i.write(writer)?
        }
        Ok(())
    }
}

#[cfg(target_endian = "big")]
compile_error!("fisher does not work on big endian platforms at the moment");

impl<'a, const N: usize> ArgumentEncode<'a> for &'a [jint; N] {
    fn read(reader: &mut BorrowReader<'a>) -> Self {
        let count = N * size_of::<jint>();
        let bytes = reader.get_bytes(count);
        // Safety: jint aka i32 is valid at every bit pattern of [u8; size_of::<i32>()]
        unsafe { core::slice::from_raw_parts(bytes.as_ptr().cast::<jint>(), N) }
            .first_chunk()
            .unwrap()
    }

    fn write(&self, writer: &mut impl Write) -> io::Result<()> {
        writer.write_all(unsafe {
            core::slice::from_raw_parts(self.as_ptr().cast::<u8>(), std::mem::size_of_val(*self))
        })
    }
}

impl ArgumentEncode<'_> for Vec<jdouble> {
    fn read(reader: &mut BorrowReader<'_>) -> Self {
        let len = u32::from_le_bytes(*reader.get_bytes_const());
        let bytes = reader.get_bytes(len as usize * size_of::<jdouble>());
        bytes
            .as_chunks()
            .0
            .iter()
            .copied()
            .map(jdouble::from_le_bytes)
            .collect()
    }

    fn write(&self, writer: &mut impl Write) -> io::Result<()> {
        writer.write_all(
            &(u32::try_from(self.len()).expect("array len to be <= u32::MAX")).to_le_bytes(),
        )?;
        writer.write_all(unsafe {
            core::slice::from_raw_parts(self.as_ptr().cast::<u8>(), std::mem::size_of_val(&**self))
        })
    }
}
macro_rules! ignore_option_impls {
    ( $( $opt:ty ),+ ) => {
        $(
            impl ArgumentEncode<'_> for Option<$opt> {
                fn read(_: &mut BorrowReader<'_>) -> Self {
                    None
                }
                fn write(&self, _: &mut impl Write) -> io::Result<()> {
                    Ok(())
                }
            }
        )+
    };
}
ignore_option_impls!(JavaVM, GlobalRef, SableMethodID);

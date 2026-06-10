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
    pub fn get_bytes(&mut self, ct: usize) -> &'a [u8] {
        assert!(self.cursor + ct <= self.bytes.len());
        let out = &self.bytes[self.cursor..self.cursor + ct];
        self.cursor += ct;
        out
    }
    pub fn get_bytes_const<const N: usize>(&mut self) -> &[u8; N] {
        self.get_bytes(N).first_chunk().unwrap()
    }
    pub fn new(bytes: &'a [u8]) -> Self {
        Self { bytes, cursor: 0 }
    }
    pub fn remaining(&self) -> usize {
        self.bytes.len() - self.cursor
    }
}

impl ArgumentEncode for () {
    fn read(_: &mut BorrowReader) -> Self {}
    fn write(&self, _: &mut impl Write) -> io::Result<()> {
        Ok(())
    }
}

macro_rules! numeric_argument_encodings {
    ( $( $intty:ty ),+ ) => {
        $(
            impl ArgumentEncode for $intty {
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

impl<const N: usize, T: ArgumentEncode> ArgumentEncode for [T; N] {
    fn read(reader: &mut BorrowReader) -> Self {
        array::from_fn(|_| T::read(reader))
    }

    fn write(&self, writer: &mut impl Write) -> io::Result<()> {
        for i in self.iter() {
            i.write(writer)?
        }
        Ok(())
    }
}
impl<T: ArgumentEncode> ArgumentEncode for Box<T> {
    fn read(reader: &mut BorrowReader) -> Self {
        Box::new(T::read(reader))
    }

    fn write(&self, writer: &mut impl Write) -> io::Result<()> {
        (**self).write(writer)
    }
}

impl ArgumentEncode for Vec<jdouble> {
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
impl ArgumentEncode for Vec<jint> {
    fn read(reader: &mut BorrowReader<'_>) -> Self {
        let len = u32::from_le_bytes(*reader.get_bytes_const());
        let bytes = reader.get_bytes(len as usize * size_of::<jint>());
        bytes
            .as_chunks()
            .0
            .iter()
            .copied()
            .map(jint::from_le_bytes)
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
            impl ArgumentEncode for Option<$opt> {
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

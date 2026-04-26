use std::{path::PathBuf, time::Instant};

use clap::Parser;
use fisher::imp::BorrowReader;
use sable_rapier::api::recording::CallType;

fn main() {
    let args = Arguments::parse();

    let file = std::fs::read(args.instr_file).unwrap();
    let mut reader = BorrowReader::new(&file);
    let start = Instant::now();
    while reader.remaining() != 0 {
        let &[byte] = reader.get_bytes_const();
        let ty = CallType::new(byte);

        // dbg!(ty);
        let trackable = ty.as_trackable();
        trackable.playback(&mut reader);
    }
    println!(
        "run took {}ms",
        Instant::now().saturating_duration_since(start).as_millis()
    );
}

#[derive(Parser)]
pub struct Arguments {
    /// The recorded file to parse
    instr_file: PathBuf,
}

use std::io::Write;
use std::path::Path;
use std::{env, fs};

fn main() {
    let target_os = env::var("CARGO_CFG_TARGET_OS").unwrap_or_default();
    if target_os != "windows" {
        return;
    }

    let src_dir = Path::new("src");
    let out_dir = env::var("OUT_DIR").unwrap();
    let def_path = Path::new(&out_dir).join("exports.def");

    let mut exports = Vec::new();

    collect_jni_exports(src_dir, &mut exports);

    let mut def_file = fs::File::create(&def_path).expect("Failed to create .def file");
    writeln!(def_file, "EXPORTS").unwrap();
    for name in &exports {
        writeln!(def_file, "    {name}").unwrap();
    }

    println!("cargo::rerun-if-changed=src");
    println!(
        "cargo::rustc-cdylib-link-arg={}",
        def_path.to_str().unwrap()
    );
}

fn collect_jni_exports(dir: &Path, exports: &mut Vec<String>) {
    let Ok(entries) = fs::read_dir(dir) else {
        return;
    };
    for entry in entries.flatten() {
        let path = entry.path();
        if path.is_dir() {
            collect_jni_exports(&path, exports);
        } else if path.extension().is_some_and(|e| e == "rs") {
            let Ok(contents) = fs::read_to_string(&path) else {
                continue;
            };
            for line in contents.lines() {
                let trimmed = line.trim();
                if let Some(rest) = trimmed.strip_prefix("pub extern \"system\" fn ")
                    && let Some(name) = rest.split(['<', '(']).next()
                {
                    let name = name.trim();
                    if name.starts_with("Java_") {
                        exports.push(name.to_string());
                    }
                }
            }
        }
    }
}

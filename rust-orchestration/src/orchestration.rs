use std::cmp::Ordering;
use std::collections::HashMap;
use std::collections::HashSet;

use std::hash::Hash;
use std::path::Path;
use std::path::PathBuf;
use std::process::Stdio;

use idesyde_rust_core::DecisionModelHeader;
use idesyde_rust_core::ExplorationModule;
use idesyde_rust_core::IdentificationModule;

#[derive(Debug, PartialEq, Eq, Hash)]
pub enum ExternalModuleType {
    StaticBinary,
    JarFile,
}

#[derive(Debug, PartialEq, Eq, Hash)]
pub struct ExternalIdentificationModule {
    run_path_: PathBuf,
    command_path_: PathBuf,
    module_type: ExternalModuleType,
}

impl IdentificationModule for ExternalIdentificationModule {
    fn unique_identifier(&self) -> String {
        self.command_path_.to_str().unwrap().to_string()
    }

    fn run_path(&self) -> &Path {
        self.run_path_.as_path()
    }

    fn identification_step(&self, step_number: i32) -> HashSet<DecisionModelHeader> {
        let mut headers = HashSet::new();
        let is_java = self
            .command_path_
            .extension()
            .and_then(|s| s.to_str())
            .map(|s| s == "jar")
            .unwrap_or(false);
        let output = match is_java {
            true => std::process::Command::new("java")
                .arg("-jar")
                .arg(self.command_path_.as_os_str())
                .arg("--no-integration")
                .arg(self.run_path_.as_os_str())
                .arg(step_number.to_string())
                .stdout(Stdio::piped())
                .stderr(Stdio::piped())
                .output(),
            false => std::process::Command::new(self.command_path_.as_os_str())
                .arg("--no-integration")
                .arg(self.run_path_.as_os_str())
                .arg(step_number.to_string())
                .stdout(Stdio::piped())
                .stderr(Stdio::piped())
                .output(),
        };
        if let Ok(out) = output {
            if let Ok(s) = String::from_utf8(out.stdout) {
                for p in s.lines() {
                    let b = std::fs::read(p)
                        .expect("Failed to read header file from disk during identification");
                    let header = rmp_serde::from_slice::<DecisionModelHeader>(b.as_slice()).expect(
                        "Failed to deserialize header file from disk during identification.",
                    );
                    headers.insert(header);
                }
            }
        }
        headers
    }
}

#[derive(Debug, PartialEq, Eq, Hash)]
pub struct ExternalExplorationModule {
    run_path_: PathBuf,
    command_path_: PathBuf,
    module_type: ExternalModuleType,
}

impl ExplorationModule for ExternalExplorationModule {
    fn unique_identifier(&self) -> String {
        self.command_path_.to_str().unwrap().to_string()
    }

    fn run_path(&self) -> &Path {
        self.run_path_.as_path()
    }

    fn available_criterias(
        &self,
        m: &dyn idesyde_rust_core::DecisionModel,
    ) -> std::collections::HashMap<String, f32> {
        HashMap::new() // TODO: put interfaces later
    }

    fn get_combination(
        &self,
        m: &dyn idesyde_rust_core::DecisionModel,
    ) -> idesyde_rust_core::ExplorationCombinationDescription {
        let output = match self.module_type {
            ExternalModuleType::JarFile => std::process::Command::new("java")
                .arg("-jar")
                .arg(self.command_path_.as_os_str())
                .arg("-c")
                .arg(self.run_path_.as_os_str())
                .stdout(Stdio::piped())
                .stderr(Stdio::piped())
                .output(),
            ExternalModuleType::StaticBinary => {
                std::process::Command::new(self.command_path_.as_os_str())
                    .arg("--no-integration")
                    .arg(self.run_path_.as_os_str())
                    .stdout(Stdio::piped())
                    .stderr(Stdio::piped())
                    .output()
            }
        };
        todo!()
    }

    fn explore(
        &self,
        m: &dyn idesyde_rust_core::DecisionModel,
    ) -> &dyn Iterator<Item = &dyn idesyde_rust_core::DecisionModel> {
        todo!()
    }
}

pub fn load_decision_model_headers_from_binary(header_path: &Path) -> HashSet<DecisionModelHeader> {
    let known_decision_model_paths = if let Ok(ls) = header_path.read_dir() {
        ls.flat_map(|dir_entry_r| {
            if let Ok(dir_entry) = dir_entry_r {
                if dir_entry
                    .path()
                    .file_name()
                    .and_then(|f| f.to_str())
                    .map_or(false, |f| f.starts_with("header"))
                    && dir_entry
                        .path()
                        .extension()
                        .map_or(false, |ext| ext.eq_ignore_ascii_case("msgpack"))
                {
                    return Some(dir_entry.path());
                }
            }
            None
        })
        .collect::<HashSet<PathBuf>>()
    } else {
        HashSet::new()
    };
    known_decision_model_paths
        .iter()
        .flat_map(|p| std::fs::read(p))
        .flat_map(|b| rmp_serde::decode::from_slice(&b).ok())
        .collect::<HashSet<DecisionModelHeader>>()
}

pub fn find_identification_modules(
    modules_path: &Path,
    run_path: &Path,
) -> HashSet<ExternalIdentificationModule> {
    let mut imodules = HashSet::new();
    if let Ok(read_dir) = modules_path.read_dir() {
        for e in read_dir {
            if let Ok(de) = e {
                let p = de.path();
                if p.is_file() {
                    let prog = p.read_link().unwrap_or(p);
                    let is_java = prog
                        .extension()
                        .and_then(|s| s.to_str())
                        .map(|s| s == "jar")
                        .unwrap_or(false);
                    if is_java {
                        imodules.insert(ExternalIdentificationModule {
                            run_path_: run_path.to_path_buf().clone(),
                            command_path_: prog.clone(),
                            module_type: ExternalModuleType::JarFile,
                        });
                    } else {
                        imodules.insert(ExternalIdentificationModule {
                            run_path_: run_path.to_path_buf().clone(),
                            command_path_: prog.clone(),
                            module_type: ExternalModuleType::StaticBinary,
                        });
                    }
                }
            }
        }
    }
    imodules
}

pub fn find_exploration_modules(
    modules_path: &Path,
    run_path: &Path,
) -> HashSet<ExternalExplorationModule> {
    let mut emodules = HashSet::new();
    if let Ok(read_dir) = modules_path.read_dir() {
        for e in read_dir {
            if let Ok(de) = e {
                let p = de.path();
                if p.is_file() {
                    let prog = p.read_link().unwrap_or(p);
                    let is_java = prog
                        .extension()
                        .and_then(|s| s.to_str())
                        .map(|s| s == "jar")
                        .unwrap_or(false);
                    if is_java {
                        emodules.insert(ExternalExplorationModule {
                            run_path_: run_path.to_path_buf().clone(),
                            command_path_: prog.clone(),
                            module_type: ExternalModuleType::JarFile,
                        });
                    } else {
                        emodules.insert(ExternalExplorationModule {
                            run_path_: run_path.to_path_buf().clone(),
                            command_path_: prog.clone(),
                            module_type: ExternalModuleType::StaticBinary,
                        });
                    }
                }
            }
        }
    }
    emodules
}

pub fn identification_procedure(
    run_path: &Path,
    imodules: &HashSet<Box<dyn IdentificationModule>>,
) -> HashSet<DecisionModelHeader> {
    let header_path = &run_path.join("identified");
    std::fs::create_dir_all(header_path)
        .expect("Failed to created 'identified' folder during identification.");
    let mut identified_headers = load_decision_model_headers_from_binary(&header_path);
    let mut step = identified_headers.len() as i32;
    let mut fix_point = false;
    while !fix_point {
        fix_point = true;
        for imodule in imodules {
            let potential = imodule.identification_step(step);
            fix_point = fix_point && potential.is_empty();
            let mut added = 0;
            for m in potential {
                println!(
                    "{}, {:?} {:?}",
                    m.category, m.covered_elements, m.covered_relations
                );
                if identified_headers.insert(m) {
                    println!("inside");
                    added += 1;
                };
            }
            fix_point = fix_point && (added == 0);
        }
        step += 1;
    }
    identified_headers
}

pub fn compute_dominant_decision_models(
    headers: &HashSet<DecisionModelHeader>,
) -> HashSet<DecisionModelHeader> {
    headers
        .iter()
        .filter(|&h| {
            headers.iter().all(|o| match h.partial_cmp(o) {
                Some(Ordering::Greater) | Some(Ordering::Equal) | None => true,
                _ => false,
            })
        })
        .map(|h| h.to_owned())
        .collect::<HashSet<DecisionModelHeader>>()
}

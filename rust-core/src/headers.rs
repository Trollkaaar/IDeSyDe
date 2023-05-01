use serde::{Deserialize, Serialize};
use std::{
    cmp::Ordering,
    collections::HashMap,
    hash::Hash,
    path::{Path, PathBuf},
};

use crate::DesignModel;

#[derive(Serialize, Clone, Deserialize, Debug)]
pub struct LabelledArcWithPorts {
    pub src: String,
    pub src_port: Option<String>,
    pub label: Option<String>,
    pub dst: String,
    pub dst_port: Option<String>,
}

impl PartialEq<LabelledArcWithPorts> for LabelledArcWithPorts {
    fn eq(&self, other: &LabelledArcWithPorts) -> bool {
        self.src == other.src
            && self.dst == other.dst
            && match (&self.src_port, &other.src_port) {
                (Some(a), Some(b)) => a == b,
                (None, None) => true,
                _ => false,
            }
            && match (&self.dst_port, &other.dst_port) {
                (Some(a), Some(b)) => a == b,
                (None, None) => true,
                _ => false,
            }
            && match (&self.label, &other.label) {
                (Some(a), Some(b)) => a == b,
                (None, None) => true,
                _ => false,
            }
    }
}

impl Eq for LabelledArcWithPorts {}

impl Hash for LabelledArcWithPorts {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.src.hash(state);
        self.dst.hash(state);
        if let Some(a) = &self.src_port {
            a.hash(state);
        }
        if let Some(a) = &self.dst_port {
            a.hash(state);
        };
        if let Some(a) = &self.label {
            a.hash(state);
        };
    }
}

#[derive(Serialize, Clone, Deserialize, Debug)]
pub struct DesignModelHeader {
    pub category: String,
    pub model_paths: Vec<String>,
    pub elements: Vec<String>,
    pub relations: Vec<LabelledArcWithPorts>,
}

impl DesignModel for DesignModelHeader {
    fn unique_identifier(&self) -> String {
        self.category.to_owned()
    }

    fn header(&self) -> DesignModelHeader {
        self.to_owned()
    }
}

impl PartialEq<DesignModelHeader> for DesignModelHeader {
    fn eq(&self, o: &DesignModelHeader) -> bool {
        self.category == o.category && self.elements == o.elements && self.relations == o.relations
    }
}

impl PartialOrd<DesignModelHeader> for DesignModelHeader {
    fn partial_cmp(&self, o: &DesignModelHeader) -> std::option::Option<std::cmp::Ordering> {
        let superset = o.elements.iter().all(|v| self.elements.contains(v))
            && o.relations.iter().all(|v| self.relations.contains(v));
        let subset = self.elements.iter().all(|v| o.elements.contains(v))
            && self.relations.iter().all(|v| o.relations.contains(v));
        return match (superset, subset) {
            (true, true) => Some(Ordering::Equal),
            (true, false) => Some(Ordering::Greater),
            (false, true) => Some(Ordering::Less),
            _ => None,
        };
    }
}

impl Eq for DesignModelHeader {}

impl Hash for DesignModelHeader {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.category.hash(state);
        for m in &self.elements {
            m.hash(state);
        }
        for e in &self.relations {
            e.hash(state);
        }
    }
}

#[derive(Serialize, Clone, Deserialize, Debug)]
pub struct DecisionModelHeader {
    pub category: String,
    pub body_path: Option<String>,
    pub covered_elements: Vec<String>,
    pub covered_relations: Vec<LabelledArcWithPorts>,
}

impl PartialEq<DecisionModelHeader> for DecisionModelHeader {
    fn eq(&self, o: &DecisionModelHeader) -> bool {
        self.category == o.category
            && self.covered_elements == o.covered_elements
            && self.covered_relations == o.covered_relations
    }
}

impl PartialOrd<DecisionModelHeader> for DecisionModelHeader {
    fn partial_cmp(&self, o: &DecisionModelHeader) -> std::option::Option<std::cmp::Ordering> {
        let superset = o
            .covered_elements
            .iter()
            .all(|v| self.covered_elements.contains(v))
            && o.covered_relations
                .iter()
                .all(|v| self.covered_relations.contains(v));
        let subset = self
            .covered_elements
            .iter()
            .all(|v| o.covered_elements.contains(v))
            && self
                .covered_relations
                .iter()
                .all(|v| o.covered_relations.contains(v));
        return match (superset, subset) {
            (true, true) => Some(Ordering::Equal),
            (true, false) => Some(Ordering::Greater),
            (false, true) => Some(Ordering::Less),
            _ => None,
        };
    }
}

impl Eq for DecisionModelHeader {}

impl Hash for DecisionModelHeader {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.category.hash(state);
        for m in &self.covered_elements {
            m.hash(state);
        }
        for e in &self.covered_elements {
            e.hash(state);
        }
    }
}

#[derive(Debug, Deserialize, Serialize)]
pub struct ExplorationBid {
    pub can_explore: bool,
    pub criteria: HashMap<String, f32>,
}

impl Hash for ExplorationBid {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.can_explore.hash(state);
        for k in self.criteria.keys() {
            k.hash(state);
        }
    }
}

impl PartialEq<ExplorationBid> for ExplorationBid {
    fn eq(&self, other: &ExplorationBid) -> bool {
        self.can_explore == other.can_explore && self.criteria == other.criteria
    }
}

impl Eq for ExplorationBid {}

impl PartialOrd<ExplorationBid> for ExplorationBid {
    fn partial_cmp(&self, other: &ExplorationBid) -> Option<Ordering> {
        if self.can_explore == other.can_explore {
            if self.criteria.keys().eq(other.criteria.keys()) {
                if self
                    .criteria
                    .iter()
                    .all(|(k, v)| v > other.criteria.get(k).unwrap_or(v))
                {
                    return Some(Ordering::Greater);
                } else if self
                    .criteria
                    .iter()
                    .all(|(k, v)| v == other.criteria.get(k).unwrap_or(v))
                {
                    return Some(Ordering::Equal);
                } else if self
                    .criteria
                    .iter()
                    .all(|(k, v)| v < other.criteria.get(k).unwrap_or(v))
                {
                    return Some(Ordering::Less);
                }
            }
        }
        None
    }
}

pub fn load_decision_model_headers_from_binary(
    header_path: &Path,
) -> Vec<(PathBuf, DecisionModelHeader)> {
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
        .collect::<Vec<PathBuf>>()
    } else {
        Vec::new()
    };
    known_decision_model_paths
        .iter()
        .map(|p| {
            (
                p,
                std::fs::read(p).expect("Failed to read decision model header file."),
            )
        })
        .map(|(p, b)| {
            (
                p.to_owned(),
                rmp_serde::decode::from_slice(&b)
                    .expect("Failed to deserialize deicsion model header."),
            )
        })
        .collect()
}

pub fn load_design_model_headers_from_binary(header_path: &Path) -> Vec<DesignModelHeader> {
    let known_design_model_paths = if let Ok(ls) = header_path.read_dir() {
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
        .collect::<Vec<PathBuf>>()
    } else {
        Vec::new()
    };
    known_design_model_paths
        .iter()
        .flat_map(|p| std::fs::read(p))
        .map(|b| {
            rmp_serde::decode::from_slice(&b).expect("Failed to serialize design model header")
        })
        .collect()
}

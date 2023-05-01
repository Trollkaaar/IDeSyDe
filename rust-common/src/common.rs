use std::collections::{HashMap, HashSet};

use idesyde_core::{
    headers::{DecisionModelHeader, LabelledArcWithPorts},
    DecisionModel,
};
use serde::Serialize;

#[derive(Debug, PartialEq, Eq, Serialize)]
pub struct CommunicatingAndTriggeredReactiveWorkload {
    pub tasks: Vec<String>,
    pub task_sizes: Vec<u32>,
    pub task_computational_needs: Vec<HashMap<String, HashMap<String, u32>>>,
    pub data_channels: Vec<String>,
    pub data_channel_sizes: Vec<u32>,
    pub data_graph_src: Vec<String>,
    pub data_graph_dst: Vec<String>,
    pub data_graph_message_size: Vec<u32>,
    pub periodic_sources: Vec<String>,
    pub periods_numerator: Vec<u32>,
    pub periods_denominator: Vec<u32>,
    pub offsets_numerator: Vec<u32>,
    pub offsets_denominator: Vec<u32>,
    pub upsamples: Vec<String>,
    pub upsample_repetitive_holds: Vec<u32>,
    pub upsample_initial_holds: Vec<u32>,
    pub downsamples: Vec<String>,
    pub downample_repetitive_skips: Vec<u32>,
    pub downample_initial_skips: Vec<u32>,
    pub trigger_graph_src: Vec<String>,
    pub trigger_graph_dst: Vec<String>,
    pub has_or_trigger_semantics: HashSet<String>,
}

impl DecisionModel for CommunicatingAndTriggeredReactiveWorkload {
    fn unique_identifier(&self) -> String {
        "CommunicatingAndTriggeredReactiveWorkload".to_string()
    }

    fn header(&self) -> idesyde_core::headers::DecisionModelHeader {
        let mut elems: Vec<String> = Vec::new();
        let mut rels: Vec<LabelledArcWithPorts> = Vec::new();
        elems.extend(self.tasks.iter().map(|x| x.to_owned()));
        elems.extend(self.data_channels.iter().map(|x| x.to_owned()));
        elems.extend(self.periodic_sources.iter().map(|x| x.to_owned()));
        elems.extend(self.upsamples.iter().map(|x| x.to_owned()));
        elems.extend(self.downsamples.iter().map(|x| x.to_owned()));
        for i in 0..self.data_graph_src.len() {
            rels.push(LabelledArcWithPorts {
                src: self.data_graph_src[i].to_owned(),
                src_port: None,
                label: Some(format!("{}", self.data_graph_message_size[i])),
                dst: self.data_graph_dst[i].to_owned(),
                dst_port: None,
            })
        }
        for i in 0..self.trigger_graph_src.len() {
            rels.push(LabelledArcWithPorts {
                src: self.trigger_graph_src[i].to_owned(),
                src_port: None,
                label: Some("trigger".to_string()),
                dst: self.trigger_graph_dst[i].to_owned(),
                dst_port: None,
            })
        }
        DecisionModelHeader {
            category: self.unique_identifier(),
            body_path: None,
            covered_elements: elems,
            covered_relations: rels,
        }
    }
}

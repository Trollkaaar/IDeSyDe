package idesyde.identification.models

import idesyde.identification.DecisionModel
import forsyde.io.java.core.Vertex
import forsyde.io.java.typed.viewers.SDFComb
import forsyde.io.java.typed.viewers.SDFPrefix
import forsyde.io.java.typed.viewers.SDFSignal
import org.jgrapht.graph.SimpleDirectedGraph


// class SDFExecution(DecisionModel):
//     """
//     This decision model captures all SDF actors and channels in
//     the design model and can only be identified if the 'Global' SDF
//     application (the union of all disjoint SDFs) is consistent, i.e.
//     it has a PASS.

//     After identification this decision model provides the global
//     SDF topology and the PASS with all elements included.
//     """

//     sdf_actors: Sequence[Vertex] = field(default_factory=list)
//     # sdf_constructors: Mapping[Process, SDFComb] = field(default_factory=dict)
//     sdf_impl: Mapping[Vertex, Vertex] = field(default_factory=dict)
//     sdf_delays: Sequence[Vertex] = field(default_factory=list)
//     sdf_channels: Mapping[Tuple[Vertex, Vertex], Sequence[Sequence[Vertex]]] = field(default_factory=dict)
//     sdf_topology: List[List[int]] = field(default_factory=list)
//     sdf_repetition_vector: List[int] = field(default_factory=list)
//     sdf_initial_tokens: List[int] = field(default_factory=list)
//     sdf_pass: Sequence[Vertex] = field(default_factory=list)

//     sdf_max_tokens: List[int] = field(default_factory=list)

//     def covered_vertexes(self):
//         yield from self.sdf_actors
//         for paths in self.sdf_channels.values():
//             for p in paths:
//                 yield from p
//         # yield from self.sdf_constructors.values()
//         yield from self.sdf_impl.values()

//     def compute_deduced_properties(self):
//         self.max_tokens = [0 for c in self.sdf_channels]
//         for (cidx, c) in enumerate(self.sdf_channels):
//             self.max_tokens[cidx] = max(
//                 self.sdf_topology[cidx][aidx] * self.sdf_repetition_vector[aidx]
//                 for (aidx, a) in enumerate(self.sdf_actors)
//             )

final case class SDFApplication(
    val actors: Seq[SDFComb],
    val delays: Seq[SDFPrefix],
    val signals: Seq[SDFSignal],
    // val implementations: Map[SDFComb, Vertex]
) extends SimpleDirectedGraph[SDFComb | SDFPrefix, SDFSignal](
      classOf[SDFSignal]
    )
    with DecisionModel:

  // override def dominates(o: DecisionModel) = {
  //   val extra: Boolean = o match {
  //     case o: SDFApplication => dominatesSdf(o)
  //     case _                 => true
  //   }
  //   super.dominates(o) && extra
  // }

  // def dominatesSdf(other: SDFApplication) = repetitionVector.size >= other.repetitionVector.size

  val coveredVertexes = {
    for (a <- actors) yield a.getViewedVertex
    for (d <- delays) yield d.getViewedVertex
    for (s <- signals) yield s.getViewedVertex
    // for ((_, v) <- implementations) yield v
  }

  override val uniqueIdentifier = "SDFApplication"

end SDFApplication

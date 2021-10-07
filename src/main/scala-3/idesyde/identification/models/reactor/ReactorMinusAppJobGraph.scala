package idesyde.identification.models.reactor

import idesyde.identification.DecisionModel
import idesyde.identification.models.reactor.ReactorMinusApplication
import idesyde.identification.models.reactor.ReactionChannel
import org.jgrapht.graph.SimpleDirectedGraph
import forsyde.io.java.typed.viewers.LinguaFrancaReactor
import forsyde.io.java.typed.viewers.LinguaFrancaReaction
import org.jgrapht.alg.shortestpath.AllDirectedPaths

import collection.JavaConverters.*
import org.jgrapht.traverse.ClosestFirstIterator
import org.jgrapht.traverse.DepthFirstIterator
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.GraphPath
import java.util.stream.Collectors
import scala.collection.mutable.Buffer
import scala.annotation.tailrec
import org.jgrapht.alg.shortestpath.CHManyToManyShortestPaths
import org.jgrapht.graph.SimpleDirectedWeightedGraph
import org.jgrapht.alg.shortestpath.DijkstraManyToManyShortestPaths
import org.jgrapht.alg.connectivity.GabowStrongConnectivityInspector
import org.jgrapht.alg.shortestpath.FloydWarshallShortestPaths
import org.jgrapht.traverse.BreadthFirstIterator
import org.apache.commons.math3.fraction.BigFraction
import org.apache.commons.math3.util.ArithmeticUtils
import org.jgrapht.graph.AsWeightedGraph
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm
import org.jgrapht.util.VertexToIntegerMapping
import org.jgrapht.graph.DefaultEdge
import forsyde.io.java.typed.viewers.LinguaFrancaSignal

// sealed class ReactionJobOrdering(
//     val reactionsPartialOrder: PartialOrdering[LinguaFrancaReaction],
//     val longestPathsBetweenJobs: ShortestPathAlgorithm[LinguaFrancaReaction, LinguaFrancaSignal]
// ) extends Ordering[ReactionJob] {

//   def compare(j: ReactionJob, jj: ReactionJob): Int =
//     if j.trigger.compareTo(jj.trigger) < 0 then -1
//     else if
//     // if !reactionsPartialOrder
//     //     .lteq(j.srcReaction, jj.srcReaction) && longestPathsBetweenJobs.getPath(j, jj) == null
//     // then j.deadline.subtract(j.trigger).compareTo(jj.deadline.subtract(jj.trigger))
//     else 1
// }

final case class ReactorMinusAppJobGraph(
    reactorMinus: ReactorMinusApplication
) extends SimpleDirectedGraph[ReactionJob, ReactionChannel](classOf[ReactionChannel]):

  given Ordering[LinguaFrancaReaction] = reactorMinus.reactionsOrdering

  val periodicJobs: Set[ReactionJob] = for (
    r <- reactorMinus.periodicReactions;
    period = reactorMinus.periodFunction.getOrElse(r, reactorMinus.hyperPeriod);
    i <- Seq.range(0, reactorMinus.hyperPeriod.divide(period).getNumerator.intValue)
  ) yield ReactionJob(r, period.multiply(i), period.multiply(i + 1))

  val pureJobs: Set[ReactionJob] = {
    // first, get all pure jobs from the periodic ones, even with activation overlap
    // val paths = AllDirectedPaths(reactorMinus)
    val periodicReactionToJobs = periodicJobs.groupBy(_.srcReaction)
    val overlappedPureJobs = reactorMinus.periodicReactions.flatMap(r => {
      val iterator                     = BreadthFirstIterator(reactorMinus, r)
      val periodicJobs                 = periodicReactionToJobs(r)
      var pureJobSet: Set[ReactionJob] = Set()
      while iterator.hasNext do
        val cur = iterator.next
        if (reactorMinus.pureReactions.contains(cur))
          pureJobSet = pureJobSet ++ periodicJobs.map(j => {
            ReactionJob(cur, j.trigger, j.deadline)
          })
      pureJobSet
    })
    val sortedOverlap = overlappedPureJobs
      .groupBy(j => (j.srcReaction, j.trigger))
      .map((_, js) => js.minBy(_.deadline))
    // sortedOverlap.toSet
    sortedOverlap
      .groupBy(_.srcReaction)
      .flatMap((r, js) => {
        val jsSorted = js.toSeq.sortBy(_.trigger)
        val nonOverlap =
          for (
            i <- 0 until (jsSorted.size - 1);
            job     = jsSorted(i);
            nextJob = jsSorted(i + 1)
          )
            yield ReactionJob(
              job.srcReaction,
              job.trigger,
              if job.deadline.compareTo(nextJob.trigger) <= 0 then job.deadline else nextJob.trigger
            )
        nonOverlap.appended(jsSorted.last)
      })
      .toSet
  }

  val jobs: Set[ReactionJob] = pureJobs.union(periodicJobs)

  for (j <- periodicJobs)
    if j.trigger.equals(j.deadline) then
      scribe.error(s"Job ${j.toString} has trigger == deadline! Behavior may be undefined")

  val reactionToJobs: Map[LinguaFrancaReaction, Set[ReactionJob]] =
    jobs.groupBy(j => j.srcReaction)

  val reactorToJobs: Map[LinguaFrancaReactor, Set[ReactionJob]] =
    jobs.groupBy(j => reactorMinus.containmentFunction(j.srcReaction))
  // reactorMinus.reactors
  // .map(a => a -> a.getReactionsPort(model).asScala.toSeq.flatMap(reactionsToJobs(_)))
  // .toMap

  val pureChannels: Set[ReactionChannel] =
    (for (
      ((r, rr) -> c) <- reactorMinus.channels;
      if reactorMinus.pureReactions.contains(rr);
      j  <- reactionToJobs(r);
      jj <- reactionToJobs(rr);
      // if the triggering time is the same
      if j != jj && j.trigger.equals(jj.trigger)
    ) yield ReactionChannel(j, jj, c)).toSet

  val priorityChannels: Set[ReactionChannel] =
    for (
      a <- reactorMinus.reactors;
      (t, jset) <- reactorToJobs(a).groupBy(
        _.trigger
      );
      if jset.size > 1;
      j :: jj :: _ <- jset.toSeq
        .sortBy(_.srcReaction)
        .sliding(2);
      if j != jj
    ) yield ReactionChannel(j, jj, a)

  val timelyChannels: Set[ReactionChannel] =
    for (
      a <- reactorMinus.reactors;
      js :: jjs :: _ <- reactorToJobs(
        a
      )
        .groupBy(_.trigger)
        .toSeq
        .sortBy((t, js) => t)
        .map((t, js) => js.toSeq.sortBy(_.srcReaction))
        .sliding(2)
    ) yield ReactionChannel(js.last, jjs.head, a)

  val stateChannels: Set[ReactionChannel] = priorityChannels ++ timelyChannels

  val outerStateChannels: Set[ReactionChannel] =
    for (
      a <- reactorMinus.reactors;
      jset = reactorToJobs(a) // jobs.filter(j => reactorMinus.containmentFunction(j._1) == a).toSeq
        .groupBy(_.trigger)
        .toSeq
        .sortBy((t, js) => t)
        .map((t, js) => js.toSeq.sortBy(_.srcReaction));
      js = jset.head; jjs = jset.last
      // same reactor
      // reactor = reactorMinus.containmentFunction.get(j._1);
      // if reactor == reactorMinus.containmentFunction(jj._1);
      // go through jobs to check if there is not connection between j and jj
      // if !jobs.exists(o =>
      //   // triggering time
      //   stateChannels.contains((o, j, reactor.get)) || stateChannels.contains((jj, o, reactor.get))
      //   )
    ) yield ReactionChannel(jjs.last, js.head, a)

  val inChannels: Set[ReactionChannel] = pureChannels ++ stateChannels

  val channels: Set[ReactionChannel] = inChannels ++ outerStateChannels

  for (j <- periodicJobs) addVertex(j)
  for (j <- pureJobs) addVertex(j)
  for (c <- pureChannels) addEdge(c.src, c.dst, c)
  for (c <- stateChannels) addEdge(c.src, c.dst, c)
  for (c <- outerStateChannels) addEdge(c.src, c.dst, c)

  val longestPathsBetweenJobs =
    val newWeights = (inChannels.map(c =>
      c -> reactorMinus.hyperPeriod
        .subtract(c.dst.trigger.subtract(c.src.trigger))
        .doubleValue
        .asInstanceOf[java.lang.Double]
    )
      ++ outerStateChannels.map(c =>
        c -> c.dst.trigger
          .add(reactorMinus.hyperPeriod)
          .subtract(c.src.trigger)
          .doubleValue
          .asInstanceOf[java.lang.Double]
      )).toMap.asJava
    FloydWarshallShortestPaths(AsWeightedGraph(this, newWeights))

  def checkJobPathIsReactionpath[LR <: Seq[LinguaFrancaReaction], LJ <: Seq[ReactionJob]](
      reactions: LR,
      jobs: LJ
  ): Boolean =
    val reactionIter = reactions.iterator
    val jobIter      = jobs.iterator
    while (reactionIter.hasNext && jobIter.hasNext) {
      val r = reactionIter.next
      var j = jobIter.next
      while (j.srcReaction == r && jobIter.hasNext) {
        j = jobIter.next
      }
    }
    !reactionIter.hasNext && !jobIter.hasNext

  lazy val jobLevelFixedLatencies: Map[(ReactionJob, ReactionJob), BigFraction] =
    // scribe.debug(s"SSC ${GabowStrongConnectivityInspector(this).getCondensation.vertexSet.size}")
    val endToEndReactions = reactorMinus.unambigousEndToEndReactions
    // val allPathsCalculator = AllDirectedPaths(this)
    // val reactionToJobs = jobs.groupBy(_.srcReaction)
    endToEndReactions
      .map((srcdst, reactionPath) => {
        val (src, dst) = srcdst
        val allSources = reactionToJobs(src)
        val sources = allSources.filter(j =>
          incomingEdgesOf(j)
            .stream()
            .map(_.src)
            .filter(jj => allSources.contains(jj))
            .noneMatch(jj => jj.trigger.compareTo(j.trigger) < 0)
        )
        val allSinks = reactionToJobs(dst)
        val sinks = allSinks.filter(j =>
          outgoingEdgesOf(j)
            .stream()
            .map(_.dst)
            .filter(jj => allSinks.contains(jj))
            .allMatch(jj => jj.deadline.compareTo(j.deadline) <= 0)
        )
        for (
          srcJob <- sources;
          dstJob <- sinks;
          pOpt = Option(longestPathsBetweenJobs.getPath(srcJob, dstJob))
          if pOpt.isDefined;
          p = pOpt.get;
          if checkJobPathIsReactionpath(reactionPath.toList, p.getVertexList.asScala.toList)
        ) yield (srcJob, dstJob) -> p
      })
      .map(jpaths => {
        jpaths.maxBy((_, p) => {
          p.getWeight
          // val lastJobOfPath = p.getVertexList.get(p.getLength - 1)
          // p.getWeight + lastJobOfPath.deadline.subtract(lastJobOfPath.trigger).doubleValue
        })
      })
      .map((srcdst, p) =>
        // redo the computation between ends, which is enough to get the static latency,
        srcdst ->
          p.getVertexList
            .get(p.getVertexList.size - 1)
            .trigger
            .subtract(p.getVertexList.get(0).trigger)
      )
      .toMap

  lazy val jobPrecedenceOrdering: Ordering[ReactionJob] = new Ordering[ReactionJob] {

    def compare(j: ReactionJob, jj: ReactionJob): Int =
      if j.trigger.compareTo(jj.trigger) == 0 then
        reactorMinus.reactionsOrdering.compare(j.srcReaction, jj.srcReaction)
      else j.trigger.compareTo(jj.trigger)
  }

  lazy val jobPriorityOrdering: Ordering[ReactionJob] = new Ordering[ReactionJob] {

    def compare(j: ReactionJob, jj: ReactionJob): Int =
      reactorMinus.reactionsOrdering.compare(j.srcReaction, jj.srcReaction)
  }

  val uniqueIdentifier = reactorMinus.uniqueIdentifier + "JobGraph"

end ReactorMinusAppJobGraph

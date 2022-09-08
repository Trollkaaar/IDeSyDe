package idesyde.identification.choco.models.sdf

import idesyde.identification.choco.interfaces.ChocoModelMixin
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.constraints.Propagator
import org.chocosolver.solver.constraints.PropagatorPriority
import org.chocosolver.util.ESat
import scala.collection.mutable.HashMap
import breeze.linalg._
import org.chocosolver.solver.constraints.Constraint
import org.chocosolver.solver.exception.ContradictionException
import org.chocosolver.solver.constraints.`extension`.Tuples
import idesyde.identification.choco.models.SingleProcessSingleMessageMemoryConstraintsMixin
import idesyde.identification.choco.models.TileAsyncInterconnectCommsMixin
import idesyde.utils.CoreUtils.wfor
import idesyde.identification.forsyde.models.mixed.SDFToSchedTiledHW

trait SDFTimingAnalysisSASMixin extends ChocoModelMixin {
  this: SingleProcessSingleMessageMemoryConstraintsMixin =>

  def sdfAndSchedulers: SDFToSchedTiledHW
  def timeFactor: Long = 1L

  def actors: Array[Int] = sdfAndSchedulers.sdfApplications.actorsSet
  def schedulers: Array[Int] = sdfAndSchedulers.platform.schedulerSet
  def balanceMatrix: Array[Array[Int]] = sdfAndSchedulers.sdfApplications.balanceMatrices.head
  def initialTokens: Array[Int] = sdfAndSchedulers.sdfApplications.initialTokens
  def actorDuration: Array[Array[Int]] = sdfAndSchedulers.wcets.map(ws => ws.map(w => w * timeFactor).map(_.ceil.intValue))

  def channelsCommunicate: Array[Array[Array[BoolVar]]]
  def channelsTravelTime: Array[Array[Array[IntVar]]]
  def firingsInSlots: Array[Array[Array[IntVar]]]
  def initialLatencies: Array[IntVar]
  def slotMaxDurations: Array[IntVar]
  def slotPeriods: Array[IntVar]
  def slotStartTime: Array[Array[IntVar]]
  def slotFinishTime: Array[Array[IntVar]]
  def invThroughputs: Array[IntVar]
  def globalInvThroughput: IntVar

  def maxRepetitionsPerActors = sdfAndSchedulers.sdfApplications.sdfRepetitionVectors
  def isSelfConcurrent(aIdx: Int) = sdfAndSchedulers.sdfApplications.isSelfConcurrent(aIdx)

  def actorMapping(actorId: Int) = processesMemoryMapping(actors.indexOf(actorId))

  private def maxSlots  = firingsInSlots.head.head.size
  private def slotRange = 0 until maxSlots
  private def maximumTokensProduced = actors.zipWithIndex.map((a, ai) => balanceMatrix.map(cs => cs(ai) * maxRepetitionsPerActors(a)).max).max

  def slots(ai: Int)(sj: Int) =
    (0 until maxSlots)
      .map(firingsInSlots(ai)(sj)(_))
      .toArray

  def postOnlySAS(): Unit = {
    actors.zipWithIndex.foreach((a, ai) => {
      // disable self concurreny if necessary
      // if (!isSelfConcurrent(a)) {
      //   for (slot <- slotRange) {
      //     chocoModel.sum(schedulers.map(p => firingsInSlots(a)(p)(slot)), "<=", 1).post()
      //   }
      // }
      // set total number of firings
      chocoModel.sum(firingsInSlots(ai).flatten, "=", maxRepetitionsPerActors(a)).post()
      schedulers.zipWithIndex
        .foreach((s, sj) => {
          // if (actors.size > 1) {
          //   chocoModel
          //     .min(s"0_in_${ai}_${sj}", (0 until actors.size).map(slots(ai)(sj)(_)): _*)
          //     .eq(0)
          //     .post()
          // }
          chocoModel.ifThenElse(
            actorMapping(ai).eq(sj).decompose(),
            chocoModel.sum(slots(ai)(sj), ">=", 1),
            chocoModel.sum(slots(ai)(sj), "=", 0)
          )
          chocoModel.sum(slots(ai)(sj), "<=", maxRepetitionsPerActors(a)).post()
        })
    })
    for (
      (s, sj) <- schedulers.zipWithIndex;
      slot    <- 0 until firingsInSlots.head.head.size
    ) {
      val onlyOneActor = Tuples(true)
      onlyOneActor.add(Array.fill(actors.size)(0))
      for ((a, ai) <- actors.zipWithIndex; q <- 1 to maxRepetitionsPerActors(a)) {
        val vec = (Array.fill(ai)(0) :+ q) ++ Array.fill(actors.size - ai - 1)(0)
        // println(vec.mkString(", "))
        onlyOneActor.add(vec)
        // chocoModel.ifOnlyIf(
        //   firingsInSlots(a)(sj)(slot).gt(0).decompose(),
        //   chocoModel.and(
        //     actors.filter(_ != a).map(firingsInSlots(_)(sj)(slot).eq(0).decompose()): _*
        //   )
        // )
      }
      chocoModel
        .table(actors.map(a => firingsInSlots(a)(sj)(slot)).toArray, onlyOneActor, "CT+")
        .post()
      // chocoModel.atMostNValues(allInSlot(sj)(slot), chocoModel.intVar(2), true).post()
    }
    // val firingsSlotSums = (0 until firingsInSlots.head.head.size).map(slot => chocoModel.sum(s"sum_${slot}", actors.flatMap(a => schedulers.map(p => firingsInSlots(a)(p)(slot))):_*)).toArray
    // for (
    //   slot <- 1 until firingsInSlots.head.head.size
    // ) {
    //   chocoModel.ifThen(firingsSlotSums(slot - 1).gt(0), firingsSlotSums(slot).gt(0))
    // }
  }

  def postSDFTimingAnalysisSAS(): Unit = {
    val maximumTokensProducedVal = maximumTokensProduced
    val consMat = balanceMatrix.map(bs => bs.map(b => if (b < 0) then -b else 0))
    val prodMat = balanceMatrix.map(bs => bs.map(b => if (b > 0) then b else 0))
    postOnlySAS()
    // timings
    for(s <- slotRange; p <- schedulers) {
      val actorFirings = actors.map(a => firingsInSlots(a)(p)(s))
      val duration = chocoModel.intVar(s"slotDur($p,$s)", 0, slotFinishTime.head.last.getUB(), true)
      chocoModel.scalar(actorFirings, actors.map(a => actorDuration(a)(p)), "=", duration)
      slotFinishTime(p)(s).eq(duration.add(slotStartTime(p)(s))).decompose().post()
      if (s > 0) {
        slotStartTime(p)(s).ge(slotFinishTime(p)(s - 1))
        // now take care of communications
        for (pOther <- schedulers; if pOther != p; c <- 0 until balanceMatrix.size) {
          val previousActorFirings = actors.map(a => firingsInSlots(a)(pOther)(s - 1))
          val consumed = chocoModel.intVar(s"cons($p, $s)", 0, maximumTokensProducedVal, true)
          val produced = chocoModel.intVar(s"prod($pOther, ${s - 1})", 0, maximumTokensProducedVal, true)
          val diffVar = chocoModel.intVar(s"diff($pOther,$p,$s)", 0, maximumTokensProducedVal, true)
          chocoModel.scalar(previousActorFirings, prodMat(c), "=", produced)
          chocoModel.scalar(actorFirings, consMat(c), "=", consumed)
          diffVar.eq(consumed.sub(produced)).decompose().post()
          // val diffSum = chocoModel.sum(s"diff($pOther,$p,$s)", firingsInSlots()
          chocoModel.ifThen(channelsCommunicate(c)(pOther)(p).and(diffVar.gt(0)).decompose(),
            slotStartTime(p)(s).ge(slotFinishTime(p)(s - 1).add(channelsTravelTime(c)(pOther)(p).mul(diffVar))).decompose()
          )
        }
      }
    }
    // throughput
    val thPropagator = SDFLikeThroughputPropagator(
      firingsInSlots,
      slotStartTime,
      slotFinishTime,
      invThroughputs,
      globalInvThroughput
    )
    chocoModel.post(
      new Constraint(
        "global_th_sdf_prop",
        thPropagator
      )
    )
    // tokens
    val propagator = SDFLikeTokensPropagator(
      actors.zipWithIndex.map((a, i) => maxRepetitionsPerActors(i)),
      balanceMatrix,
      initialTokens,
      channelsCommunicate,
      firingsInSlots
    )
    chocoModel.post(
      new Constraint(
        "global_sas_sdf_prop",
        propagator
      )
    )
  }


}

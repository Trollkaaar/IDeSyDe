package idesyde.choco

import upickle.default._

import idesyde.blueprints.ExplorationModule
import idesyde.utils.Logger
import idesyde.core.DecisionModel
import idesyde.core.headers.DecisionModelHeader
import idesyde.utils.SimpleStandardIOLogger
import idesyde.identification.common.models.mixed.SDFToTiledMultiCore
import idesyde.choco.ChocoExplorer
import spire.math.Rational
import idesyde.core.ExplorationCombinationDescription

object ChocoExplorationModule extends ExplorationModule {

  def combination(decisionModel: DecisionModel): ExplorationCombinationDescription = {
    val combos = explorers.map(e => e.combination(decisionModel))
    // keep only the dominant ones and take the biggest
    combos
      .filter(l => {
        combos
          .filter(_ != l)
          .forall(r => {
            l `<?>` r match {
              case '>' | '=' => true
              case _         => false
            }
          })
      })
      .head
  }

  given Fractional[Rational] = spire.compat.fractional[Rational]

  val logger = SimpleStandardIOLogger("WARN")

  given Logger = logger

  override def uniqueIdentifier: String = "ChocoExplorationModule"

  def explorers = Set(ChocoExplorer())

  def decodeDecisionModels(m: DecisionModelHeader): Seq[DecisionModel] = {
    m match {
      case DecisionModelHeader("SDFToTiledMultiCore", body_path, _, _) =>
        body_path.flatMap(decodeFromPath[SDFToTiledMultiCore])
      case _ => Seq()
    }
  }

  def main(args: Array[String]): Unit = standaloneExplorationModule(args)

}

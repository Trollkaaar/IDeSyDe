package idesyde.devicetree

import idesyde.blueprints.StandaloneIdentificationModule
import idesyde.core.DecisionModel
import idesyde.core.DesignModel
import idesyde.utils.Logger
import idesyde.core.headers.DesignModelHeader
import idesyde.core.headers.DecisionModelHeader
import idesyde.devicetree.identification.PlatformRules
import idesyde.devicetree.identification.CanParseDeviceTree
import os.Path
import idesyde.devicetree.identification.DeviceTreeDesignModel
import idesyde.devicetree.identification.OSDescriptionDesignModel
import org.virtuslab.yaml.*

object DeviceTreeIdentificationModule
    extends StandaloneIdentificationModule
    with PlatformRules
    with CanParseDeviceTree {

  override def inputsToDesignModel(p: Path): Option[DesignModelHeader | DesignModel] = {
    p.ext match {
      case "dts" =>
        parseDeviceTreeWithPrefix(os.read(p), p.baseName) match {
          case Success(result, next) => Some(DeviceTreeDesignModel(List(result)))
          case _                     => None
        }
      case "yaml" =>
        os.read(p).as[OSDescription] match {
          case Right(value) => Some(OSDescriptionDesignModel(value))
          case Left(value)  => None
        }
      case _ => None
    }
  }

  def designHeaderToModel(m: DesignModelHeader): Set[DesignModel] = Set()
  // if (
  //   m.category == "DeviceTreeDesignModel"
  // ) {
  //   m.model_paths
  //     .map(parseDeviceTree)
  //     .flatMap(r =>
  //       r match {
  //         case Success(result, next) => Some(DeviceTreeDesignModel(List(result)))
  //         case _                     => None
  //       }
  //     )
  //     .toSet
  // } else if (m.category == "OSDescriptionDesignModel") {
  //   m.model_paths.flatMap(s => {
  //     val p = if (s.startsWith("/")) os.root / s else os.pwd / s
  //     os.read(p).as[OSDescription] match {
  //       case Right(value) => Some(OSDescriptionDesignModel(value))
  //       case Left(value)  => None
  //     }
  //   })
  // } else Set()

  def decisionHeaderToModel(m: DecisionModelHeader): Option[DecisionModel] = None

  def uniqueIdentifier: String = "DeviceTreeIdentificationModule"

  def identificationRules: Set[(Set[DesignModel], Set[DecisionModel]) => Set[? <: DecisionModel]] =
    Set(
      identSharedMemoryMultiCore,
      identPartitionedCoresWithRuntimes
    )

  def reverseIdentificationRules
      : Set[(Set[DecisionModel], Set[DesignModel]) => Set[? <: DesignModel]] =
    Set()

  def main(args: Array[String]) = standaloneIdentificationModule(args)

}

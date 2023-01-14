package idesyde.identification.common.rules

import idesyde.identification.DecisionModel
import idesyde.identification.DesignModel
import idesyde.identification.common.models.mixed.SDFToTiledMultiCore
import idesyde.identification.common.models.platform.SchedulableTiledMultiCore
import idesyde.identification.common.models.platform.PartitionedSharedMemoryMultiCore
import idesyde.identification.common.models.sdf.SDFApplication
import idesyde.identification.common.models.mixed.SDFToPartitionedSharedMemory
import idesyde.identification.common.models.mixed.PeriodicWorkloadToPartitionedSharedMultiCore
import idesyde.identification.common.models.workload.CommunicatingExtendedDependenciesPeriodicWorkload
import spire.math.Rational

object MixedRules {

  def identSDFToTiledMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): Option[SDFToTiledMultiCore] = {
    val app = identified
      .find(_.isInstanceOf[SDFApplication])
      .map(_.asInstanceOf[SDFApplication])
    val plat = identified
      .find(_.isInstanceOf[SchedulableTiledMultiCore])
      .map(_.asInstanceOf[SchedulableTiledMultiCore])
    // if ((runtimes.isDefined && plat.isEmpty) || (runtimes.isEmpty && plat.isDefined))
    app.flatMap(a =>
      plat.map(p =>
        SDFToTiledMultiCore(
          sdfApplications = a,
          platform = p,
          processMappings = Array.empty,
          messageMappings = Array.empty,
          schedulerSchedules = Array.empty,
          messageSlotAllocations = Array.empty
        )
      )
    )
  }

  def identSDFToPartitionedSharedMemory(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): Option[SDFToPartitionedSharedMemory] = {
    val app = identified
      .find(_.isInstanceOf[SDFApplication])
      .map(_.asInstanceOf[SDFApplication])
    val plat = identified
      .find(_.isInstanceOf[PartitionedSharedMemoryMultiCore])
      .map(_.asInstanceOf[PartitionedSharedMemoryMultiCore])
    // if ((runtimes.isDefined && plat.isEmpty) || (runtimes.isEmpty && plat.isDefined))
    app.flatMap(a =>
      plat.map(p =>
        SDFToPartitionedSharedMemory(
          sdfApplications = a,
          platform = p,
          processMappings = Array.empty,
          memoryMappings = Array.empty,
          messageSlotAllocations = Array.empty
        )
      )
    )
  }

  def identPeriodicWorkloadToPartitionedSharedMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): Option[PeriodicWorkloadToPartitionedSharedMultiCore] = {
    val app = identified
      .find(_.isInstanceOf[CommunicatingExtendedDependenciesPeriodicWorkload])
      .map(_.asInstanceOf[CommunicatingExtendedDependenciesPeriodicWorkload])
    val plat = identified
      .find(_.isInstanceOf[PartitionedSharedMemoryMultiCore])
      .map(_.asInstanceOf[PartitionedSharedMemoryMultiCore])
    // if ((runtimes.isDefined && plat.isEmpty) || (runtimes.isEmpty && plat.isDefined))
    app.flatMap(a =>
      plat.map(p =>
        PeriodicWorkloadToPartitionedSharedMultiCore(
          workload = a,
          platform = p,
          processMappings = Map(),
          processSchedulings = Map(),
          channelMappings = Map(),
          channelSlotAllocations = Map(),
          maxUtilizations = Map()
        )
      )
    )
  }

}

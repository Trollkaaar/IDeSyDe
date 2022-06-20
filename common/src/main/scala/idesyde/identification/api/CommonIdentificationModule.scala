package idesyde.identification.api

import idesyde.identification.IdentificationRule
import idesyde.identification.DecisionModel

class CommonIdentificationModule extends IdentificationModule {

  val identificationRules: Set[IdentificationRule[? <: DecisionModel]] = Set()

}

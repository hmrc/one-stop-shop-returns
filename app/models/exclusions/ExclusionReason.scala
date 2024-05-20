/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package models.exclusions

import models.{Enumerable, WithName}

sealed trait ExclusionReason

object ExclusionReason extends Enumerable.Implicits {

  case object Reversal extends WithName("-1") with ExclusionReason

  case object NoLongerSupplies extends WithName("1") with ExclusionReason

  case object CeasedTrade extends WithName("2") with ExclusionReason

  case object NoLongerMeetsConditions extends WithName("3") with ExclusionReason

  case object FailsToComply extends WithName("4") with ExclusionReason

  case object VoluntarilyLeaves extends WithName("5") with ExclusionReason

  case object TransferringMSID extends WithName("6") with ExclusionReason

  val values: Seq[ExclusionReason] = Seq(
    Reversal,
    NoLongerSupplies,
    CeasedTrade,
    NoLongerMeetsConditions,
    FailsToComply,
    VoluntarilyLeaves,
    TransferringMSID
  )

  implicit val enumerable: Enumerable[ExclusionReason] =
    Enumerable(values.map(v => v.toString -> v): _*)
}



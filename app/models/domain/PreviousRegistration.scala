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

package models.domain

import models.Country
import play.api.libs.json.{Json, OFormat, Reads, Writes}

sealed trait PreviousRegistration

object PreviousRegistration {

  implicit val reads: Reads[PreviousRegistration] =
    PreviousRegistrationNew.format.widen[PreviousRegistration] orElse
      PreviousRegistrationLegacy.format.widen[PreviousRegistration]

  implicit val writes: Writes[PreviousRegistration] = Writes {
    case p: PreviousRegistrationNew => Json.toJson(p)(PreviousRegistrationNew.format)
    case l: PreviousRegistrationLegacy => Json.toJson(l)(PreviousRegistrationLegacy.format)
  }
}
case class PreviousRegistrationNew(country: Country, previousSchemesDetails: Seq[PreviousSchemeDetails]) extends PreviousRegistration

object PreviousRegistrationNew {

  implicit val format: OFormat[PreviousRegistrationNew] = Json.format[PreviousRegistrationNew]
}

case class PreviousRegistrationLegacy(country: Country, vatNumber: String) extends PreviousRegistration

object PreviousRegistrationLegacy {

  implicit val format: OFormat[PreviousRegistrationLegacy] = Json.format[PreviousRegistrationLegacy]
}

case class PreviousSchemeDetails(previousScheme: PreviousScheme, previousSchemeNumbers: PreviousSchemeNumbers)

object PreviousSchemeDetails {

  implicit val format: OFormat[PreviousSchemeDetails] = Json.format[PreviousSchemeDetails]
}


case class PreviousSchemeNumbers(
                                  previousSchemeNumber: String,
                                  previousIntermediaryNumber: Option[String]
                                )

object PreviousSchemeNumbers {

  implicit val format: OFormat[PreviousSchemeNumbers] = Json.format[PreviousSchemeNumbers]
}

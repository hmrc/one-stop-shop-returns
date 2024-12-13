/*
 * Copyright 2024 HM Revenue & Customs
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
import play.api.libs.json.*

sealed trait Address

object Address {
  def reads: Reads[Address] =
    UkAddress.reads.widen[Address] orElse
      InternationalAddress.reads.widen[Address] orElse
      DesAddress.reads.widen[Address]

  def writes: Writes[Address] = Writes {
    case u: UkAddress            => Json.toJson(u)(UkAddress.writes)
    case d: DesAddress           => Json.toJson(d)(DesAddress.writes)
    case i: InternationalAddress => Json.toJson(i)(InternationalAddress.writes)
  }

  implicit def format: Format[Address] = Format(reads, writes)
}

case class UkAddress(
   line1: String,
   line2: Option[String],
   townOrCity: String,
   county: Option[String],
   postCode: String
) extends Address {

  val country: Country = Country("GB", "United Kingdom")
}

object UkAddress {
  implicit val reads: Reads[UkAddress] = {

    import play.api.libs.functional.syntax._

    (__ \ "country" \ "code").read[String].flatMap[String] {
      t =>
        if (t == "GB") Reads(_ => JsSuccess(t)) else Reads(_ => JsError("countryCode must be GB"))
    }.andKeep(
      (
        (__ \ "line1").read[String] and
        (__ \ "line2").readNullable[String] and
        (__ \ "townOrCity").read[String] and
        (__ \ "county").readNullable[String] and
        (__ \ "postCode").read[String]
      )(UkAddress(_, _, _, _, _)
    ))
  }

  implicit val writes: OWrites[UkAddress] = new OWrites[UkAddress] {

    override def writes(o: UkAddress): JsObject = {
      val line2Obj = o.line2.map(x => Json.obj("line2" -> x)).getOrElse(Json.obj())
      val countyObj = o.county.map(x => Json.obj("county" -> x)).getOrElse(Json.obj())

      Json.obj(
        "line1" -> o.line1,
        "townOrCity" -> o.townOrCity,
        "postCode" -> o.postCode,
        "country" -> o.country
      ) ++ line2Obj ++ countyObj
    }
  }
}

case class DesAddress(
                       line1: String,
                       line2: Option[String],
                       line3: Option[String],
                       line4: Option[String],
                       line5: Option[String],
                       postCode: Option[String],
                       countryCode: String
                     ) extends Address

object DesAddress {

  implicit val reads: Reads[DesAddress] = {

    import play.api.libs.functional.syntax._
    (
      (__ \ "line1").read[String] and
        (__ \ "line2").readNullable[String] and
        (__ \ "line3").readNullable[String] and
        (__ \ "line4").readNullable[String] and
        (__ \ "line5").readNullable[String] and
        (__ \ "postCode").readNullable[String] and
        (__ \ "countryCode").read[String]
      )(DesAddress(_, _, _, _, _, _, _))
  }

  implicit val writes: OWrites[DesAddress] = new OWrites[DesAddress] {

    override def writes(o: DesAddress): JsObject = {
      val line2Obj = o.line2.map(x => Json.obj("line2" -> x)).getOrElse(Json.obj())
      val line3Obj = o.line3.map(x => Json.obj("line3" -> x)).getOrElse(Json.obj())
      val line4Obj = o.line4.map(x => Json.obj("line4" -> x)).getOrElse(Json.obj())
      val line5Obj = o.line5.map(x => Json.obj("line5" -> x)).getOrElse(Json.obj())
      val postCodeObj = o.postCode.map(x => Json.obj("postCode" -> x)).getOrElse(Json.obj())

      Json.obj(
        "line1" -> o.line1,
        "countryCode" -> o.countryCode
      ) ++ line2Obj ++ line3Obj ++ line4Obj ++ line5Obj ++ postCodeObj
    }
  }
}


case class InternationalAddress (
                                  line1: String,
                                  line2: Option[String],
                                  townOrCity: String,
                                  stateOrRegion: Option[String],
                                  postCode: Option[String],
                                  country: Country
                                ) extends Address

object InternationalAddress {

  implicit val reads: Reads[InternationalAddress] = {

    import play.api.libs.functional.syntax._

    (
      (__ \ "line1").read[String] and
        (__ \ "line2").readNullable[String] and
        (__ \ "townOrCity").read[String] and
        (__ \ "stateOrRegion").readNullable[String] and
        (__ \ "postCode").readNullable[String] and
        (__ \ "country").read[Country]
      )(InternationalAddress(_, _, _, _, _, _))
  }

  implicit val writes: OWrites[InternationalAddress] = new OWrites[InternationalAddress] {

    override def writes(o: InternationalAddress): JsObject = {
      val line2Obj = o.line2.map(x => Json.obj("line2" -> x)).getOrElse(Json.obj())
      val stateOrRegionObj = o.stateOrRegion.map(x => Json.obj("stateOrRegion" -> x)).getOrElse(Json.obj())
      val postCodeObj = o.postCode.map(x => Json.obj("postCode" -> x)).getOrElse(Json.obj())

      Json.obj(
        "line1" -> o.line1,
        "townOrCity" -> o.townOrCity,
        "country" -> o.country
      ) ++ line2Obj ++ stateOrRegionObj ++ postCodeObj
    }
  }
}

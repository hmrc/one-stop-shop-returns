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

import play.api.libs.json._

sealed trait NiPresence

object NiPresence {

  implicit val reads: Reads[NiPresence] = new Reads[NiPresence] {
    override def reads(json: JsValue): JsResult[NiPresence] = json match {
      case JsString("principalPlaceOfBusinessInNi") => JsSuccess(PrincipalPlaceOfBusinessInNi)
      case JsString("fixedEstablishmentInNi")       => JsSuccess(FixedEstablishmentInNi)
      case x: JsObject                              => x.validate[NoPresence](NoPresence.reads)
      case _                                        => JsError("Unable to read as NiPresence")
    }
  }

  implicit val writes: Writes[NiPresence] = new Writes[NiPresence] {
    override def writes(o: NiPresence): JsValue = o match {
      case PrincipalPlaceOfBusinessInNi => JsString("principalPlaceOfBusinessInNi")
      case FixedEstablishmentInNi       => JsString("fixedEstablishmentInNi")
      case x: NoPresence                => Json.toJson(x)(NoPresence.writes)
    }
  }
}

case object PrincipalPlaceOfBusinessInNi extends NiPresence
case object FixedEstablishmentInNi extends NiPresence

case class NoPresence(salesChannels: SalesChannels) extends NiPresence

object NoPresence {

  val reads: Reads[NoPresence] = {

    import play.api.libs.functional.syntax._

    (__ \ "presence").read[String].flatMap[String] {
      p =>
        if (p == "noPresence") Reads(_ => JsSuccess(p)) else Reads(_ => JsError("presence must be `noPresence`"))
    }.andKeep(
      (__ \ "salesChannels").read[SalesChannels]
    ).map(NoPresence(_))
  }

  val writes: Writes[NoPresence] = new Writes[NoPresence] {
    override def writes(o: NoPresence): JsValue =
      Json.obj(
        "presence"      -> "noPresence",
        "salesChannels" -> Json.toJson(o.salesChannels)
      )
  }
}

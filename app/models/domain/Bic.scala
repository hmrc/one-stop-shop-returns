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

import play.api.libs.json.{JsError, JsResult, JsString, JsSuccess, JsValue, Reads, Writes}

case class Bic private (
                         bankCode: String,
                         countryCode: String,
                         location: String,
                         branch: Option[String]
                       ) {

  override def toString = s"$bankCode$countryCode$location${branch.getOrElse("")}"
}

object Bic {

  private val bicPatternWithBranch    = """^([A-Za-z]{6})([A-Za-z2-9])([A-Na-nP-Zp-z0-9])([A-Za-z0-9]{3})$""".r
  private val bicPatternWithoutBranch = """^([A-Za-z]{6})([A-Za-z2-9])([A-Na-nP-Zp-z0-9])$""".r

  def apply(input: String): Option[Bic] = input.trim.replace(" ", "") match {
    case bicPatternWithBranch(bankCode, countryCode, location, branch) =>
      Some(Bic(bankCode.toUpperCase, countryCode.toUpperCase, location.toUpperCase, Some(branch.toUpperCase)))

    case bicPatternWithoutBranch(bankCode, countryCode, location) =>
      Some(Bic(bankCode.toUpperCase, countryCode.toUpperCase, location.toUpperCase, None))

    case _ =>
      None
  }

  implicit val reads: Reads[Bic] = new Reads[Bic] {

    override def reads(json: JsValue): JsResult[Bic] = json match {
      case JsString(value) =>
        apply(value) match {
          case Some(bic) => JsSuccess(bic)
          case None      => JsError("BIC is not in the correct format")
        }

      case _ =>
        JsError("BIC is not in the correct format")
    }
  }

  implicit val writes: Writes[Bic] = new Writes[Bic] {
    override def writes(o: Bic): JsValue = JsString(o.toString)
  }
}

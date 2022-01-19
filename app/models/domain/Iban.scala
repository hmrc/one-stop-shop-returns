/*
 * Copyright 2022 HM Revenue & Customs
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

import models.domain.IbanError.{InvalidChecksum, InvalidFormat}
import play.api.libs.json.{JsError, JsResult, JsString, JsSuccess, JsValue, Reads, Writes}

sealed trait IbanError

object IbanError {
  case object InvalidFormat extends IbanError
  case object InvalidChecksum extends IbanError
}

final case class Iban (countryCode: String, checkSum: String, remainder: String) {

  override def toString: String = s"$countryCode$checkSum$remainder"
}

object Iban {

  private val ibanFormat       = """([A-Z]{2})(\d{2})([A-Za-z0-9]{10,30})""".r.anchored
  private val invalidChecksums = Set("00", "01", "98", "99")

  def apply(input: String): Either[IbanError, Iban] = input.trim.replace(" ", "") match {
    case ibanFormat(countryCode, checkSum, remainder) =>

      val stringToCheck = s"${remainder.toUpperCase}$countryCode$checkSum"

      val numericRepresentation = BigInt(stringToCheck.map(characterMap).mkString)

      if (numericRepresentation % 97 == 1 && !invalidChecksums.contains(checkSum)) {
        Right(Iban(countryCode, checkSum, remainder))
      } else {
        Left(InvalidChecksum)
      }

    case _ =>
      Left(InvalidFormat)
  }

  implicit val reads: Reads[Iban] = new Reads[Iban] {
    override def reads(json: JsValue): JsResult[Iban] = json match {
      case JsString(value) =>
        apply(value) match {
          case Right(iban)           => JsSuccess(iban)
          case Left(InvalidFormat)   => JsError("IBAN is not in the correct format")
          case Left(InvalidChecksum) => JsError("Invalid checksum")
        }

      case _ =>
        JsError("IBAN is not in the correct format")
    }
  }

  implicit val writes: Writes[Iban] = new Writes[Iban] {
    override def writes(o: Iban): JsValue = JsString(o.toString)
  }

  private val characterMap = Map(
    '0' -> 0,
    '1' -> 1,
    '2' -> 2,
    '3' -> 3,
    '4' -> 4,
    '5' -> 5,
    '6' -> 6,
    '7' -> 7,
    '8' -> 8,
    '9' -> 9,
    'A' -> 10,
    'B' -> 11,
    'C' -> 12,
    'D' -> 13,
    'E' -> 14,
    'F' -> 15,
    'G' -> 16,
    'H' -> 17,
    'I' -> 18,
    'J' -> 19,
    'K' -> 20,
    'L' -> 21,
    'M' -> 22,
    'N' -> 23,
    'O' -> 24,
    'P' -> 25,
    'Q' -> 26,
    'R' -> 27,
    'S' -> 28,
    'T' -> 29,
    'U' -> 30,
    'V' -> 31,
    'W' -> 32,
    'X' -> 33,
    'Y' -> 34,
    'Z' -> 35
  )
}
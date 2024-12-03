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

package models

import crypto.EncryptedValue
import play.api.libs.json._
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats


import java.time.Instant

case class SavedUserAnswers(
                             vrn: Vrn,
                             period: Period,
                             data: JsValue,
                             lastUpdated: Instant
                    )

object SavedUserAnswers {

  implicit val format: OFormat[SavedUserAnswers] = Json.format[SavedUserAnswers]
}

trait EncryptedSavedUserAnswers

object EncryptedSavedUserAnswers {

  def reads: Reads[EncryptedSavedUserAnswers] =
    NewEncryptedSavedUserAnswers.reads.widen[EncryptedSavedUserAnswers] orElse
      LegacyEncryptedSavedUserAnswers.reads.widen[EncryptedSavedUserAnswers]

  def writes: Writes[EncryptedSavedUserAnswers] = Writes {
    case n: NewEncryptedSavedUserAnswers => Json.toJson(n)(NewEncryptedSavedUserAnswers.writes)
    case l: LegacyEncryptedSavedUserAnswers => Json.toJson(l)(LegacyEncryptedSavedUserAnswers.writes)
  }

  implicit val format: Format[EncryptedSavedUserAnswers] = Format(reads, writes)
}

case class NewEncryptedSavedUserAnswers(
                             vrn: Vrn,
                             period: Period,
                             data: String,
                             lastUpdated: Instant
                           ) extends EncryptedSavedUserAnswers

object NewEncryptedSavedUserAnswers {

  val reads: Reads[NewEncryptedSavedUserAnswers] = {

    import play.api.libs.functional.syntax._

    (
      (__ \ "vrn").read[Vrn] and
        (__ \ "period").read[Period] and
        (__ \ "data").read[String] and
        (__ \ "lastUpdated").read(MongoJavatimeFormats.instantFormat)
      ) (NewEncryptedSavedUserAnswers.apply _)
  }

  val writes: OWrites[NewEncryptedSavedUserAnswers] = {

    import play.api.libs.functional.syntax._

    (
      (__ \ "vrn").write[Vrn] and
        (__ \ "period").write[Period] and
        (__ \ "data").write[String] and
        (__ \ "lastUpdated").write(MongoJavatimeFormats.instantFormat)
      ) (newEncryptedSavedUserAnswers => Tuple.fromProductTyped(newEncryptedSavedUserAnswers))
  }

  implicit val format: OFormat[NewEncryptedSavedUserAnswers] = OFormat(reads, writes)
}

case class LegacyEncryptedSavedUserAnswers(
                                         vrn: Vrn,
                                         period: Period,
                                         data: EncryptedValue,
                                         lastUpdated: Instant
                                       ) extends EncryptedSavedUserAnswers

object LegacyEncryptedSavedUserAnswers {

  val reads: Reads[LegacyEncryptedSavedUserAnswers] = {

    import play.api.libs.functional.syntax._

    (
      (__ \ "vrn").read[Vrn] and
        (__ \ "period").read[Period] and
        (__ \ "data").read[EncryptedValue] and
        (__ \ "lastUpdated").read(MongoJavatimeFormats.instantFormat)
      ) (LegacyEncryptedSavedUserAnswers.apply _)
  }

  val writes: OWrites[LegacyEncryptedSavedUserAnswers] = {

    import play.api.libs.functional.syntax._

    (
      (__ \ "vrn").write[Vrn] and
        (__ \ "period").write[Period] and
        (__ \ "data").write[EncryptedValue] and
        (__ \ "lastUpdated").write(MongoJavatimeFormats.instantFormat)
      ) (legacyEncryptedSavedUserAnswers => Tuple.fromProductTyped(legacyEncryptedSavedUserAnswers))
  }

  implicit val format: OFormat[LegacyEncryptedSavedUserAnswers] = OFormat(reads, writes)
}

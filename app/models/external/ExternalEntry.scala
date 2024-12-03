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

package models.external

import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

case class ExternalEntry(userId: String, returnUrl: String, lastUpdated: Instant)

object ExternalEntry {

  val reads: Reads[ExternalEntry] = {

    import play.api.libs.functional.syntax._

    (
      (__ \ "userId").read[String] and
        (__ \ "returnUrl").read[String] and
        (__ \ "lastUpdated").read(MongoJavatimeFormats.instantFormat)
      )(ExternalEntry.apply _)
  }

  val writes: OWrites[ExternalEntry] = {

    import play.api.libs.functional.syntax._

    (
      (__ \ "userId").write[String] and
        (__ \ "returnUrl").write[String] and
        (__ \ "lastUpdated").write(MongoJavatimeFormats.instantFormat)
      )(externalEntry => Tuple.fromProductTyped(externalEntry))
  }

  implicit val format: OFormat[ExternalEntry] = OFormat(reads, writes)
}

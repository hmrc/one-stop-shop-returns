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

package repositories

import config.AppConfig
import logging.Logging
import models.external.ExternalEntry
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model._
import play.api.libs.json.Format
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ExternalEntryRepository @Inject()(
                                        val mongoComponent: MongoComponent,
                                        appConfig: AppConfig
                                      )(implicit ec: ExecutionContext)
  extends PlayMongoRepository[ExternalEntry](
    collectionName = "external-entry",
    mongoComponent = mongoComponent,
    domainFormat = ExternalEntry.format,
    indexes = Seq(
      IndexModel(
        Indexes.ascending("userId"),
        IndexOptions()
          .name("userIdRefIndex")
          .unique(true)
      ),
      IndexModel(
        Indexes.ascending("lastUpdated"),
        IndexOptions()
          .name("lastUpdatedIdx")
          .expireAfter(appConfig.externalEntryTtlDays, TimeUnit.DAYS)
      )
    )
  ) with Logging {

  import uk.gov.hmrc.mongo.play.json.Codecs.toBson

  implicit val instantFormat: Format[Instant] = MongoJavatimeFormats.instantFormat

  private def byUserId(userId: String): Bson =
    Filters.equal("userId", toBson(userId))

  def set(externalEntry: ExternalEntry): Future[ExternalEntry] = {

    collection
      .replaceOne(
        filter      = byUserId(externalEntry.userId),
        replacement = externalEntry,
        options     = ReplaceOptions().upsert(true)
      )
      .toFuture()
      .map(_ => externalEntry)
  }

  def get(userId: String): Future[Option[ExternalEntry]] =
    collection
      .find(
        byUserId(userId)
      ).headOption()

  def clear(userId: String): Future[Boolean] =
    collection
      .deleteOne(byUserId(userId))
      .toFuture()
      .map(_ => true)
}


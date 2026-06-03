/*
 * Copyright 2026 HM Revenue & Customs
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

import org.mongodb.scala.model.*
import config.AppConfig
import logging.Logging
import models.fileUpload.{FailureReason, UploadDocument}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.{Clock, Instant}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UploadRepository @Inject()(
                                  val mongoComponent: MongoComponent,
                                  appConfig: AppConfig,
                                  clock: Clock
                                )(implicit ec: ExecutionContext) 
  extends PlayMongoRepository[UploadDocument](
    collectionName = "upload-files",
    mongoComponent = mongoComponent,
    domainFormat = UploadDocument.format,
    indexes = Seq(
      IndexModel(
        Indexes.ascending("createdAt"),
        IndexOptions()
          .name("createdAtIdx")
          .expireAfter(appConfig.externalEntryTtlDays, TimeUnit.DAYS)
      )
    )
  ) with Logging {
  
  def insert(reference: String): Future[Unit] = {
    collection
      .insertOne(
        UploadDocument(
          _id = reference,
          status = "INITIATED",
          createdAt = Instant.now(clock)
        )
      )
      .toFuture()
      .map(_ => ())
  }

  def markAsUploaded(
                      reference: String,
                      checksum: String,
                      fileName: String,
                      size: Long,
                      downloadUrl: String
                    ): Future[Unit] = {

    collection
      .updateOne(
        Filters.equal("_id", reference),
        update = Updates.combine(
          Updates.set("status", "UPLOADED"),
          Updates.set("checksum", checksum),
          Updates.set("fileName", fileName),
          Updates.set("size", size),
          Updates.setOnInsert("createdAt", Instant.now(clock)),
          Updates.set("downloadUrl", downloadUrl)
        ),
        new UpdateOptions().upsert(true)
      )
      .toFuture()
      .map { result =>
        logger.info(s"Modified count: ${result.getModifiedCount}")
      }
  }

  def markAsFailed(
                    reference: String,
                    reason: FailureReason,
                    fileName: Option[String] = None
                  ): Future[Unit] = {
    collection
      .updateOne(
        Filters.equal("_id", reference),
        update = Updates.combine(
          Updates.set("status", "FAILED"),
          Updates.set("failureReason", reason.asString),
          fileName.map(fn => Updates.set("fileName", fn)).getOrElse(Updates.unset("fileName")),
          Updates.setOnInsert("createdAt", Instant.now(clock))
        ),
        new UpdateOptions().upsert(true)
      )
      .toFuture()
      .map(_ => ())
  }

  def getUpload(reference: String): Future[Option[UploadDocument]] =
    collection.find(Filters.equal("_id", reference)).first().toFutureOption()
}

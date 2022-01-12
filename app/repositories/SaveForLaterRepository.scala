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

package repositories

import config.AppConfig
import crypto.SavedUserAnswersEncryptor
import logging.Logging
import models.{EncryptedSavedUserAnswers, Period, SavedUserAnswers}
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes}
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.transaction.{TransactionConfiguration, Transactions}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SaveForLaterRepository @Inject()(
                                     val mongoComponent: MongoComponent,
                                     encryptor: SavedUserAnswersEncryptor,
                                     appConfig: AppConfig
                                   )(implicit ec: ExecutionContext)
  extends PlayMongoRepository[EncryptedSavedUserAnswers](
    collectionName = "saved-user-answers",
    mongoComponent = mongoComponent,
    domainFormat = EncryptedSavedUserAnswers.format,
    indexes = Seq(
      IndexModel(
        Indexes.ascending("vrn", "period"),
        IndexOptions()
          .name("userAnswersReferenceIndex")
          .unique(true)
      )
    )
  ) with Transactions with Logging {

  import uk.gov.hmrc.mongo.play.json.Codecs.toBson

  private implicit val tc: TransactionConfiguration = TransactionConfiguration.strict
  private val encryptionKey = appConfig.encryptionKey

  def insert(savedUserAnswers: SavedUserAnswers): Future[Option[(SavedUserAnswers)]] = {
    val encryptedAnswers = encryptor.encryptAnswers(savedUserAnswers, savedUserAnswers.vrn, encryptionKey)

    for {
      _ <- ensureIndexes
      result <- withSessionAndTransaction { session =>
        for {
          _ <- collection.insertOne(session, encryptedAnswers).toFuture()
        } yield Some(savedUserAnswers)
      }.recover {
        case e: Exception =>
          logger.warn(s"There was an error while inserting saved user answers ${e.getMessage}", e)
          None
      }
    } yield result

  }

  def get(vrn: Vrn): Future[Seq[SavedUserAnswers]] =
    collection
      .find(Filters.equal("vrn", toBson(vrn)))
      .toFuture
      .map(_.map {
        answers =>
          encryptor.decryptAnswers(answers, answers.vrn, encryptionKey)
      })

  def get(vrn: Vrn, period: Period): Future[Option[SavedUserAnswers]] =
    collection
      .find(
        Filters.and(
          Filters.equal("vrn", toBson(vrn)),
          Filters.equal("period", toBson(period))
        )
      ).headOption
      .map(_.map {
        answers =>
          encryptor.decryptAnswers(answers, answers.vrn, encryptionKey)
      })
}

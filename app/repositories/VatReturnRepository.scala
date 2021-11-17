/*
 * Copyright 2021 HM Revenue & Customs
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
import crypto.ReturnEncrypter
import logging.Logging
import models.{EncryptedVatReturn, Period, VatReturn}
import models.corrections.CorrectionPayload
import org.mongodb.scala.model.{Filters, Indexes, IndexModel, IndexOptions}
import repositories.MongoErrors.Duplicate
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.transaction.{TransactionConfiguration, Transactions}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VatReturnRepository @Inject()(
                                     val mongoComponent: MongoComponent,
                                     returnEncrypter: ReturnEncrypter,
                                     appConfig: AppConfig,
                                     correctionRepository: CorrectionRepository
                                   )(implicit ec: ExecutionContext)
  extends PlayMongoRepository[EncryptedVatReturn](
    collectionName = "returns",
    mongoComponent = mongoComponent,
    domainFormat = EncryptedVatReturn.format,
    indexes = Seq(
      IndexModel(
        Indexes.ascending("vrn", "period"),
        IndexOptions()
          .name("returnReferenceIndex")
          .unique(true)
      )
    )
  ) with Transactions with Logging {

  import uk.gov.hmrc.mongo.play.json.Codecs.toBson

  private implicit val tc: TransactionConfiguration = TransactionConfiguration.strict
  private val encryptionKey = appConfig.encryptionKey

  def insert(vatReturn: VatReturn, correction: CorrectionPayload): Future[Option[(VatReturn, CorrectionPayload)]] = {
    val encryptedVatReturn = returnEncrypter.encryptReturn(vatReturn, vatReturn.vrn, encryptionKey)

    for {
      _ <- ensureIndexes
      _ <- correctionRepository.ensureIndexes
      result <- withSessionAndTransaction { session =>
          for {
            _ <- collection.insertOne(session, encryptedVatReturn).toFuture()
            _ <- correctionRepository.collection.insertOne(session, correction).toFuture()
          } yield Some(vatReturn, correction)
        }.recover {
          case e: Exception =>
            logger.warn(s"There was an error while inserting vat return with correction ${e.getMessage}", e)
            None
        }
    } yield result

  }

  def insert(vatReturn: VatReturn): Future[Option[VatReturn]] = {
    val encryptedVatReturn = returnEncrypter.encryptReturn(vatReturn, vatReturn.vrn, encryptionKey)

    collection
      .insertOne(encryptedVatReturn)
      .toFuture
      .map(_ => Some(vatReturn))
      .recover {
        case Duplicate(_) => None
      }
  }

  def get(vrn: Vrn): Future[Seq[VatReturn]] =
    collection
      .find(Filters.equal("vrn", toBson(vrn)))
      .toFuture
      .map(_.map {
        vatReturn =>
          returnEncrypter.decryptReturn(vatReturn, vatReturn.vrn, encryptionKey)
      })

  def get(vrn: Vrn, period: Period): Future[Option[VatReturn]] =
    collection
      .find(
        Filters.and(
          Filters.equal("vrn", toBson(vrn)),
          Filters.equal("period", toBson(period))
        )
      ).headOption
      .map(_.map {
        vatReturn =>
          returnEncrypter.decryptReturn(vatReturn, vatReturn.vrn, encryptionKey)
      })
}

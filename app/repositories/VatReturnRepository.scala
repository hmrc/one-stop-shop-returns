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

package repositories

import crypto.{CorrectionEncryptor, ReturnEncryptor}
import logging.Logging
import models.corrections.CorrectionPayload
import models.{EncryptedVatReturn, LegacyEncryptedVatReturn, NewEncryptedVatReturn, Period, VatReturn}
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes}
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
                                     returnEncryptor: ReturnEncryptor,
                                     correctionEncryptor: CorrectionEncryptor,
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

  def insert(vatReturn: VatReturn, correction: CorrectionPayload): Future[Option[(VatReturn, CorrectionPayload)]] = {
    val encryptedVatReturn = returnEncryptor.encryptReturn(vatReturn, vatReturn.vrn)
    val encryptedCorrectionPayload = correctionEncryptor.encryptCorrectionPayload(correction, vatReturn.vrn)

    for {
      _ <- ensureIndexes()
      _ <- correctionRepository.ensureIndexes()
      result <- withSessionAndTransaction { session =>
        for {
          _ <- collection.insertOne(session, encryptedVatReturn).toFuture()
          _ <- correctionRepository.collection.insertOne(session, encryptedCorrectionPayload).toFuture()
        } yield Some((vatReturn, correction))
      }.recover {
        case e: Exception =>
          logger.warn(s"There was an error while inserting vat return with correction ${e.getMessage}", e)
          None
      }
    } yield result

  }

  def insert(vatReturn: VatReturn): Future[Option[VatReturn]] = {
    val encryptedVatReturn = returnEncryptor.encryptReturn(vatReturn, vatReturn.vrn)

    collection
      .insertOne(encryptedVatReturn)
      .toFuture()
      .map(_ => Some(vatReturn))
      .recover {
        case Duplicate(_) => None
      }
  }

  def get(): Future[Seq[VatReturn]] =
    collection
      .find()
      .toFuture()
      .map(_.map {
        case l: LegacyEncryptedVatReturn =>
          returnEncryptor.decryptLegacyReturn(l, l.vrn)
        case n: NewEncryptedVatReturn =>
          returnEncryptor.decryptReturn(n, n.vrn)
      })

  def get(vrn: Vrn): Future[Seq[VatReturn]] =
    collection
      .find(Filters.equal("vrn", toBson(vrn)))
      .toFuture()
      .map(_.map {
        case l: LegacyEncryptedVatReturn =>
          returnEncryptor.decryptLegacyReturn(l, l.vrn)
        case n: NewEncryptedVatReturn =>
          returnEncryptor.decryptReturn(n, n.vrn)
      })

  def getByPeriods(periods: Seq[Period]): Future[Seq[VatReturn]] = {
    collection
      .find(
        Filters.in("period", periods.map(toBson(_)):_*))
      .toFuture()
      .map(_.map {
        case l: LegacyEncryptedVatReturn =>
          returnEncryptor.decryptLegacyReturn(l, l.vrn)
        case n: NewEncryptedVatReturn =>
          returnEncryptor.decryptReturn(n, n.vrn)
      })
  }

  def get(vrn: Vrn, period: Period): Future[Option[VatReturn]] =
    collection
      .find(
        Filters.and(
          Filters.equal("vrn", toBson(vrn)),
          Filters.equal("period", toBson(period))
        )
      ).headOption()
      .map(_.map {
        case l: LegacyEncryptedVatReturn =>
          returnEncryptor.decryptLegacyReturn(l, l.vrn)
        case n: NewEncryptedVatReturn =>
          returnEncryptor.decryptReturn(n, n.vrn)
      })
}

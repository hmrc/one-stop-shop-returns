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

import crypto.CorrectionEncryptor
import models.Period
import models.corrections.{CorrectionPayload, EncryptedCorrectionPayload}
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes}
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs.toBson
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CorrectionRepository @Inject()(
                                      mongoComponent: MongoComponent,
                                      correctionEncryptor: CorrectionEncryptor
                                    )(implicit ec: ExecutionContext)
  extends PlayMongoRepository[EncryptedCorrectionPayload](
    collectionName = "corrections",
    mongoComponent = mongoComponent,
    domainFormat = EncryptedCorrectionPayload.format,
    indexes = Seq(
      IndexModel(
        Indexes.ascending("vrn", "period"),
        IndexOptions()
          .name("correctionReferenceIndex")
          .unique(true)
      )
    )
  ) {

  def get(): Future[Seq[CorrectionPayload]] =
    collection
      .find()
      .toFuture()
      .map(_.map { correction =>
        correctionEncryptor.decryptCorrectionPayload(correction, correction.vrn)
      })

  def getByPeriods(periods: Seq[Period]): Future[Seq[CorrectionPayload]] =
    collection
      .find(
        Filters.in("period", periods.map(toBson(_)):_*))
      .toFuture()
      .map(_.map { correction =>
        correctionEncryptor.decryptCorrectionPayload(correction, correction.vrn)
      })


  def get(vrn: Vrn): Future[Seq[CorrectionPayload]] =
    collection
      .find(Filters.equal("vrn", toBson(vrn)))
      .toFuture()
      .map(_.map(correctionEncryptor.decryptCorrectionPayload(_, vrn)))

  def get(vrn: Vrn, period: Period): Future[Option[CorrectionPayload]] =
    collection
      .find(Filters.and(
        Filters.equal("vrn", toBson(vrn)),
        Filters.equal("period", toBson(period))
      ))
      .headOption()
      .map(_.map(correctionEncryptor.decryptCorrectionPayload(_, vrn)))


  def getByCorrectionPeriod(vrn: Vrn, period: Period): Future[Seq[CorrectionPayload]] =
    collection
      .find(Filters.and(
        Filters.equal("vrn", toBson(vrn)),
        Filters.elemMatch("corrections", Filters.eq("correctionReturnPeriod", toBson(period)))
      ))
      .toFuture()
      .map(_.map(correctionEncryptor.decryptCorrectionPayload(_, vrn)))
}

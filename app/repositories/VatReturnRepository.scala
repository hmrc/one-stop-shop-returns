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
import models.InsertResult.{AlreadyExists, InsertSucceeded}
import models.{EncryptedVatReturn, InsertResult, Period, VatReturn}
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes, ReplaceOptions}
import repositories.MongoErrors.Duplicate
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VatReturnRepository @Inject()(
                                     mongoComponent: MongoComponent,
                                     returnEncrypter: ReturnEncrypter,
                                     appConfig: AppConfig
                                   )(implicit ec: ExecutionContext)
  extends PlayMongoRepository[EncryptedVatReturn] (
    collectionName = "returns",
    mongoComponent = mongoComponent,
    domainFormat   = EncryptedVatReturn.format,
    indexes        = Seq(
      IndexModel(
        Indexes.ascending("vrn", "period"),
        IndexOptions()
          .name("returnReferenceIndex")
          .unique(true)
      )
    )
  ) {

  import uk.gov.hmrc.mongo.play.json.Codecs.toBson

  private val encryptionKey = appConfig.encryptionKey

  def insert(vatReturn: VatReturn): Future[InsertResult] = {
    val encryptedVatReturn = returnEncrypter.encryptReturn(vatReturn, vatReturn.vrn, encryptionKey)

    collection
      .insertOne(encryptedVatReturn)
      .toFuture
      .map(_ => InsertSucceeded)
      .recover {
        case Duplicate(_) => AlreadyExists
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

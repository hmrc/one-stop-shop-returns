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
import models.correction.CorrectionPayload
import models.Period
import org.mongodb.scala.model.{Filters, Indexes, IndexModel, IndexOptions}
import repositories.MongoErrors.Duplicate
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs.toBson

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CorrectionRepository @Inject()(
                                      mongoComponent: MongoComponent,
                                      appConfig: AppConfig
                                    )(implicit ec: ExecutionContext)extends PlayMongoRepository[CorrectionPayload] (
  collectionName = "corrections",
  mongoComponent = mongoComponent,
  domainFormat = CorrectionPayload.format,
  indexes = Seq(
    IndexModel(
      Indexes.ascending("vrn", "period"),
      IndexOptions()
        .name("correctionReferenceIndex")
        .unique(true)
    )
  )
){

  def insert(correction: CorrectionPayload): Future[Option[CorrectionPayload]] = {
    collection
      .insertOne(correction)
      .toFuture()
      .map(_ => Some(correction))
      .recover {
        case Duplicate(_) => None
      }
  }

  def get(vrn: Vrn): Future[Seq[CorrectionPayload]] =
    collection
      .find(Filters.equal("vrn", toBson(vrn)))
      .toFuture()

  def get(vrn: Vrn, period: Period): Future[Option[CorrectionPayload]] =
    collection
      .find(Filters.and(
        Filters.equal("vrn", toBson(vrn)),
        Filters.equal("period", toBson(period))
      ))
      .headOption()

}

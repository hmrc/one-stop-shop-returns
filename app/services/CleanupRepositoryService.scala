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

package services

import config.AppConfig
import logging.Logging
import uk.gov.hmrc.mongo.MongoComponent
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}


trait CleanupRepositoryService

class CleanupRepositoryServiceImpl @Inject()(
                                              mongoComponent: MongoComponent,
                                              appConfig: AppConfig
                                            )(implicit ec: ExecutionContext)
  extends CleanupRepositoryService with Logging {

  val startCleanup: Future[Seq[Unit]] = trigger()

  def trigger(): Future[Seq[Unit]] = {
    if (appConfig.cleanupOldCollectionsEnabled) {
      logger.info("Cleanup old collections Enabled")
      val collectionsToDrop = appConfig.cleanupOldCollectionsList
      val futureCollectionsLeftToDrop = mongoComponent
        .database
        .listCollectionNames()
        .toFuture()
        .map(_.filter(collectionName => collectionsToDrop.contains(collectionName)))


      futureCollectionsLeftToDrop.flatMap { collectionsLeftToDrop =>
        logger.info(s"Collection cleanup: Of ${collectionsToDrop.size} requested, ${collectionsLeftToDrop.size} found to be dropped")
        Future.sequence(collectionsLeftToDrop.map { collectionToDrop =>
          mongoComponent
            .database
            .getCollection(collectionToDrop)
            .drop()
            .toFuture()
        })
      }

    } else {
      logger.info("Cleanup old collections disabled")
      Future.successful(Seq.empty)
    }
  }


}
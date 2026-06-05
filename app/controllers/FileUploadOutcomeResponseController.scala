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

package controllers

import controllers.actions.DefaultAuthenticatedControllerComponents
import logging.Logging
import models.fileUpload.FileUploadOutcome
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import repositories.UploadRepository
import uk.gov.hmrc.http.{HttpResponse, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class FileUploadOutcomeResponseController @Inject()(
                                                     uploadRepository: UploadRepository,
                                                     cc: DefaultAuthenticatedControllerComponents,
                                                     httpClientV2: HttpClientV2
                                                   )(implicit ec: ExecutionContext) extends BackendController(cc) with Logging {

  def get(reference: String): Action[AnyContent] = Action.async {
    implicit request =>
      uploadRepository.getUpload(reference).map {
        case Some(doc) =>
          Ok(Json.toJson(
            FileUploadOutcome(
              fileName = doc.fileName,
              status = doc.status,
              failureReason = doc.failureReason.map(_.asString)
            )
          ))
        case None =>
          NotFound(Json.obj("error" -> s"No upload found for reference $reference"))
      }
  }

  def getCsv(reference: String): Action[AnyContent] = Action.async {
    implicit request =>

      uploadRepository.getUpload(reference).flatMap {
        case Some(doc) if doc.failureReason.nonEmpty =>
          Future.successful(BadRequest(s"Upload failed. Reason = ${doc.failureReason.map(_.asString).getOrElse("")}"))
        case Some(doc) if doc.status != "UPLOADED" =>
          Future.successful(Conflict(s"Upload was not completed. Status = ${doc.status}"))
        case Some(doc) =>
          doc.downloadUrl match {
            case Some(url) =>
              httpClientV2
                .get(url"$url")
                .execute[HttpResponse]
                .map { csv =>
                  Ok(csv.body).as("text/csv")
                }.recover {
                  case e =>
                    logger.error(s"Failed to download uploaded file for reference = $reference", e)
                    InternalServerError(s"Failed to download uploaded file: ${e.getMessage}")
                }
            case None =>
              Future.successful(InternalServerError("Missing downladUrl"))
          }
        case None => Future.successful(NotFound)
      }
  }
}

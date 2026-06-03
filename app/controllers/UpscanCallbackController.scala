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

package controllers

import controllers.actions.AuthenticatedControllerComponents
import logging.Logging
import models.fileUpload.UpscanCallbackRequest
import play.api.libs.json.*
import play.api.mvc.Action
import services.upscan.UpscanCallbackService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class UpscanCallbackController @Inject()(
                                          callbackService: UpscanCallbackService,
                                          cc: AuthenticatedControllerComponents,
                                        )(implicit ec: ExecutionContext) extends BackendController(cc) with Logging {

  def callback: Action[JsValue] = Action(parse.json).async {
    implicit request =>
      
      logger.info(s"Upscan callback recieved: ${Json.prettyPrint(request.body)}")
      
      request.body.validate[UpscanCallbackRequest] match {
        case JsSuccess(callback, _) =>
          logger.info(s"Upscan callback parsed successfully: reference=${callback.reference}")
          callbackService.handleUpscanCallback(callback).map {
            _ =>
              logger.info(s"Upscan callback processed successfully for reference = ${callback.reference}")
              Ok
          }

        case JsError(errors) =>
          logger.error(s"Failed to parse Upscan callback JSON: ${JsError.toJson(errors)}")
          Future.successful(BadRequest(JsError.toJson(errors)))
      }
  }
}

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

package models.audit

import controllers.actions.AuthorisedRequest
import models.core.{CoreErrorResponse, CoreVatReturn}
import play.api.libs.json.{JsObject, Json, JsValue}

case class CoreVatReturnAuditModel(
                                    userId: String,
                                    userAgent: String,
                                    vrn: String,
                                    coreVatReturn: CoreVatReturn,
                                    result: SubmissionResult,
                                    errorResponse: Option[CoreErrorResponse]
                                  ) extends JsonAuditModel {

  override val auditType: String = "CoreVatReturn"

  override val transactionName: String = "core-vat-return"

  private val coreErrorResponse: JsObject =
    if(errorResponse.isDefined) {
      Json.obj("coreErrorResponse" -> errorResponse)
    } else {
      Json.obj()
    }

  override val detail: JsValue = Json.obj(
    "userId" -> userId,
    "browserUserAgent" -> userAgent,
    "requestersVrn" -> vrn,
    "coreVatReturn" -> Json.toJson(coreVatReturn),
    "submissionResult" -> Json.toJson(result)
  ) ++ coreErrorResponse
}

object CoreVatReturnAuditModel {

  def build(
             coreVatReturn: CoreVatReturn,
             result: SubmissionResult,
             errorResponse: Option[CoreErrorResponse]
           )(implicit request: AuthorisedRequest[_]): CoreVatReturnAuditModel =
    CoreVatReturnAuditModel(
      userId = request.userId,
      userAgent = request.headers.get("user-agent").getOrElse(""),
      request.vrn.vrn,
      coreVatReturn: CoreVatReturn,
      result: SubmissionResult,
      errorResponse: Option[CoreErrorResponse]
    )
}

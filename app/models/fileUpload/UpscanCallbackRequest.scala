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

package models.fileUpload

import play.api.libs.json.*

import java.time.Instant


final case class UploadDetails(
                                fileName: String,
                                fileMimeType: String, 
                                uploadTimestamp: Instant,
                                checksum: String,
                                size: Long
                              )

object UploadDetails {
  implicit val format: Format[UploadDetails] = Json.format[UploadDetails]
}

sealed trait UpscanCallbackRequest {
  def reference: String
  def fileStatus: String
}

case class UpscanCallbackSuccess(
                                  reference: String,
                                  fileStatus: String,
                                  uploadDetails: UploadDetails,
                                  downloadUrl: String
                                ) extends UpscanCallbackRequest

object UpscanCallbackSuccess {
  implicit val format: Format[UpscanCallbackSuccess] = Json.format[UpscanCallbackSuccess]
}

final case class FailureDetails(
                                 failureReason: FailureReason,
                                 message: Option[String] = None
                               )

object FailureDetails {
  implicit val format: OFormat[FailureDetails] = Json.format[FailureDetails]
}

case class UpscanCallbackFailure(
                                  reference: String,
                                  fileStatus: String,
                                  failureDetails: FailureDetails,
                                  uploadDetails: Option[UploadDetails] = None
                                ) extends UpscanCallbackRequest

object UpscanCallbackFailure {
  implicit val format: OFormat[UpscanCallbackFailure] = Json.format[UpscanCallbackFailure]
}

case class UpscanCallbackUploading(
                                    reference: String,
                                    fileStatus: String,
                                  ) extends UpscanCallbackRequest

object UpscanCallbackUploading {
  implicit val format: OFormat[UpscanCallbackUploading] = Json.format[UpscanCallbackUploading]
}

final case class FileUploadOutcome(
                                    fileName: Option[String],
                                    status: String,
                                    failureReason: Option[String] = None
                                  )

object FileUploadOutcome {
  implicit val format: OFormat[FileUploadOutcome] = Json.format[FileUploadOutcome]
}

object UpscanCallbackRequest {
  implicit val reads: Reads[UpscanCallbackRequest] = (json: JsValue) => {
    (json \ "fileStatus").asOpt[String] match {
      case Some("READY") => Json.fromJson[UpscanCallbackSuccess](json)
      case Some("FAILED") => Json.fromJson[UpscanCallbackFailure](json)
      case Some("UPLOADING") => Json.fromJson[UpscanCallbackUploading](json)
      case _ => JsError("Invalid or missing fileStatus. Expected 'READY' or 'FAILED'")
    }
  }
}


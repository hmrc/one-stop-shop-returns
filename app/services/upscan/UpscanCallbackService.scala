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

package services.upscan

import config.AppConfig
import logging.Logging
import models.fileUpload.FailureReason.*
import models.fileUpload.{FailureReason, UpscanCallbackFailure, UpscanCallbackRequest, UpscanCallbackSuccess, UpscanCallbackUploading}
import repositories.UploadRepository

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class UpscanCallbackService @Inject()(appConfig: AppConfig, uploadRepository: UploadRepository)(implicit ec: ExecutionContext) extends Logging {

  def handleUpscanCallback(callback: UpscanCallbackRequest): Future[Unit] = {
    callback match {

      case success: UpscanCallbackSuccess =>
        val fileName = success.uploadDetails.fileName.toLowerCase
        val fileType = success.uploadDetails.fileMimeType
        val allowedTypes = Set("text/csv", "application/csv")
        val isCsv = fileName.endsWith(".csv") && allowedTypes.contains(fileType)
        val failureReasonOption: Option[FailureReason] = {
          if (fileName.endsWith(".ods")) {
            Some(InvalidFileType)
          } else if (!isCsv) {
            Some(NotCSV)
          } else if (success.uploadDetails.size > appConfig.maxFileSize) {
            Some(TooLarge)
          } else {
            None
          }
        }
        
        failureReasonOption match {
          case Some(reason) =>
            logger.warn(s"Invalid upload for reference ${success.reference}: ${reason.asString}")
            uploadRepository.markAsFailed(
              reference = success.reference,
              reason = reason,
              fileName = Some(success.uploadDetails.fileName)
            )
          case None =>
            logger.info(s"Upscan SUCCESS for reference ${success.reference}")
            uploadRepository.markAsUploaded(
              reference = success.reference,
              checksum = success.uploadDetails.checksum,
              fileName = success.uploadDetails.fileName,
              size = success.uploadDetails.size,
              downloadUrl = success.downloadUrl
            )
        }
        
      case failure: UpscanCallbackFailure =>
        val mappedReason = mapFailureReason(failure)
        
        logger.warn(
          s"Upscan FAILURE for reference ${failure.reference}, " +
            s"reason = ${failure.failureDetails.failureReason.asString}, " +
            s"mappedReason = ${mappedReason.asString}"
        )
        
        uploadRepository.markAsFailed(
          reference = failure.reference,
          reason = mappedReason,
          fileName = failure.uploadDetails.map(_.fileName)
        )
        
      case uploading: UpscanCallbackUploading =>
        logger.info(s"File is still uploading for reference ${uploading.reference}")
        Future.successful(())
    }
  }
  
  private def mapFailureReason(failure: UpscanCallbackFailure): FailureReason = {
    val upscanReason = failure.failureDetails.failureReason
    val maybeFileName = failure.uploadDetails.map(_.fileName.toLowerCase)
    val message = failure.failureDetails.message.getOrElse("").toLowerCase
    
    upscanReason match {
      case Quarantine =>
        Quarantine

      case Unknown =>
        Unknown

      case Rejected =>
        maybeFileName match {
          case Some(name) if name.endsWith(".ods") =>
            InvalidFileType

          case Some(name) if !name.endsWith(".csv") && !message.contains("text/csv") && !message.contains("application/csv") =>
            NotCSV

          case None if message.contains("application/vnd.oasis.opendocument.spreadsheet") =>
           InvalidFileType

          case _ if !message.contains("text/csv") && !message.contains("application/csv") =>
            NotCSV

          case _ =>
            Rejected
        }

      case NotCSV =>
        NotCSV

      case TooLarge =>
        TooLarge

      case InvalidFileType =>
        InvalidFileType

      case InvalidArgument =>
        InvalidArgument
    }
  }
}

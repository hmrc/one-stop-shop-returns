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

sealed trait FailureReason {
  def asString: String
}

object FailureReason {
  
  case object Quarantine extends FailureReason {
    val asString = "QUARANTINE"
  }
  case object Rejected extends FailureReason {
    val asString =  "REJECTED"
  }
  case object InvalidArgument extends FailureReason {
    val asString = "INVALID_ARGUMENT"
  }
  case object NotCSV extends FailureReason {
    val asString = "NOT_CSV"
  }
  case object Unknown extends FailureReason {
    val asString = "UNKNOWN"
  }
  case object TooLarge extends FailureReason {
    val asString = "TOO_LARGE"
  }
  case object InvalidFileType extends FailureReason {
    val asString =  "INVALID_FILE_TYPE"
  }
  
  def fromString(value: String): FailureReason = {
    value match {
      case "QUARANTINE"         => Quarantine
      case "REJECTED"           => Rejected
      case "INVALID_ARGUMENT"   => InvalidArgument
      case "NOT_CSV"            => NotCSV
      case "TOO_LARGE"          => TooLarge
      case "INVALID_FILE_TYPE"  => InvalidFileType
      case _                    => Unknown
    }
  }
  
  implicit val reads: Reads[FailureReason] = {
    Reads {
      case JsString("QUARANTINE")        => JsSuccess(Quarantine)
      case JsString("REJECTED")          => JsSuccess(Rejected)
      case JsString("INVALID_ARGUMENT")  => JsSuccess(InvalidArgument)
      case JsString("NOT_CSV")           => JsSuccess(NotCSV)
      case JsString("TOO_LARGE")         => JsSuccess(TooLarge)
      case JsString("INVALID_FILE_TYPE") => JsSuccess(InvalidFileType)
      case JsString(_)                   => JsSuccess(Unknown)
      case _                             => JsError("Failure reason must be a string")
    }
  }
  
  implicit val writes: Writes[FailureReason] = {
    Writes(reason => JsString(reason.asString))
  }
}

package models.fileUpload

import base.SpecBase
import play.api.libs.json.{JsError, JsNumber, JsString, JsSuccess, Json}

class FailureReasonSpec extends SpecBase {
  
  "FailureReason" - {

    "must convert from string to FailureReason" in {
      FailureReason.fromString("QUARANTINE") mustBe FailureReason.Quarantine
      FailureReason.fromString("REJECTED") mustBe FailureReason.Rejected
      FailureReason.fromString("INVALID_ARGUMENT") mustBe FailureReason.InvalidArgument
      FailureReason.fromString("NOT_CSV") mustBe FailureReason.NotCSV
      FailureReason.fromString("TOO_LARGE") mustBe FailureReason.TooLarge
      FailureReason.fromString("INVALID_FILE_TYPE") mustBe FailureReason.InvalidFileType
    }

    "must convert unknown string to Unknown" in {
      FailureReason.fromString("SOMETHING_ELSE") mustBe FailureReason.Unknown
    }

    "must deserialise from JSON string" in {
      Json.fromJson[FailureReason](JsString("QUARANTINE")) mustBe JsSuccess(FailureReason.Quarantine)
      Json.fromJson[FailureReason](JsString("REJECTED")) mustBe JsSuccess(FailureReason.Rejected)
      Json.fromJson[FailureReason](JsString("INVALID_ARGUMENT")) mustBe JsSuccess(FailureReason.InvalidArgument)
      Json.fromJson[FailureReason](JsString("NOT_CSV")) mustBe JsSuccess(FailureReason.NotCSV)
      Json.fromJson[FailureReason](JsString("TOO_LARGE")) mustBe JsSuccess(FailureReason.TooLarge)
      Json.fromJson[FailureReason](JsString("INVALID_FILE_TYPE")) mustBe JsSuccess(FailureReason.InvalidFileType)
    }

    "must deserialise unknown JSON string to Unknown" in {
      Json.fromJson[FailureReason](JsString("SOMETHING_ELSE")) mustBe JsSuccess(FailureReason.Unknown)
    }

    "must serialise to JSON string" in {
      Json.toJson[FailureReason](FailureReason.Quarantine) mustBe JsString("QUARANTINE")
      Json.toJson[FailureReason](FailureReason.Rejected) mustBe JsString("REJECTED")
      Json.toJson[FailureReason](FailureReason.InvalidArgument) mustBe JsString("INVALID_ARGUMENT")
      Json.toJson[FailureReason](FailureReason.NotCSV) mustBe JsString("NOT_CSV")
      Json.toJson[FailureReason](FailureReason.Unknown) mustBe JsString("UNKNOWN")
      Json.toJson[FailureReason](FailureReason.TooLarge) mustBe JsString("TOO_LARGE")
      Json.toJson[FailureReason](FailureReason.InvalidFileType) mustBe JsString("INVALID_FILE_TYPE")
    }

    "must fail to deserialise non-string JSON" in {
      Json.fromJson[FailureReason](JsNumber(123)) mustBe JsError("Failure reason must be a string")
    }
    
  }

}

package models.fileUpload

import base.SpecBase
import models.fileUpload.FailureReason.{Quarantine, Rejected}
import play.api.libs.json.{JsError, JsNull, JsNumber, JsSuccess, Json}

import java.time.Instant

class UpscanCallbackRequestSpec extends SpecBase {

  private val uploadTimestamp = Instant.now(stubClock)

  "UpscanCallbackRequest" - {

    "UploadDetails" - {

      "must serialise/deserialise to and from UploadDetails" in {

        val json = Json.obj(
          "fileName" -> "test.csv",
          "fileMimeType" -> "text/csv",
          "uploadTimestamp" -> uploadTimestamp,
          "checksum" -> "abc123",
          "size" -> 1024
        )

        val expectedResult = UploadDetails(
          fileName = "test.csv",
          fileMimeType = "text/csv",
          uploadTimestamp = uploadTimestamp,
          checksum = "abc123",
          size = 1024
        )

        Json.toJson(expectedResult) mustBe json
        json.validate[UploadDetails] mustBe JsSuccess(expectedResult)

      }

      "must handle invalid data during deserialization" in {

        val json = Json.obj(
          "fileName" -> "test.csv",
          "uploadTimestamp" -> uploadTimestamp,
          "checksum" -> "abc123",
          "size" -> 1024
        )

        json.validate[UploadDetails] mustBe a[JsError]
      }

      "must fail to deserialise from a number" in {

        val json = Json.obj(
          "fileName" -> "test.csv",
          "fileMimeType" -> "text/csv",
          "uploadTimestamp" -> uploadTimestamp,
          "checksum" -> JsNumber(1234),
          "size" -> 1024
        )

        json.validate[UploadDetails] mustBe a[JsError]
      }

      "must fail to deserialise from null" in {
        val json = Json.obj(
          "fileName" -> JsNull,
          "fileMimeType" -> "text/csv",
          "uploadTimestamp" -> uploadTimestamp,
          "checksum" -> "abc123",
          "size" -> 1024
        )


        json.validate[UploadDetails] mustBe a[JsError]
      }
    }

    "UpscanCallbackSuccess" - {

      "must serialise/deserialise to and from UpscanCallbackSuccess" in {

        val json = Json.obj(
          "reference" -> "success-reference-123",
          "fileStatus" -> "READY",
          "uploadDetails" -> Json.obj(
            "fileName" -> "test.csv",
            "fileMimeType" -> "text/csv",
            "uploadTimestamp" -> uploadTimestamp,
            "checksum" ->"abc123",
            "size" -> 1024L
          ),
          "downloadUrl" -> "https://s3.test/download/123"
        )

        val expectedResult = UpscanCallbackSuccess(
          reference = "success-reference-123",
          fileStatus = "READY",
          uploadDetails = UploadDetails(
            fileName = "test.csv",
            fileMimeType = "text/csv",
            uploadTimestamp = uploadTimestamp,
            checksum = "abc123",
            size = 1024L
          ),
          downloadUrl = "https://s3.test/download/123"
        )

        Json.toJson(expectedResult) mustBe json
        json.validate[UpscanCallbackSuccess] mustBe JsSuccess(expectedResult)

      }

      "must handle invalid data during deserialization" in {

        val json = Json.obj(
          "fileStatus" -> "READY",
          "uploadDetails" -> Json.obj(
            "fileName" -> "test.csv",
            "fileMimeType" -> "text/csv",
            "uploadTimestamp" -> uploadTimestamp,
            "checksum" ->"abc123",
            "size" -> 1024L
          ),
          "downloadUrl" -> "https://s3.test/download/123")

        json.validate[UpscanCallbackSuccess] mustBe a[JsError]
      }

      "must fail to deserialise from a number" in {

        val json = Json.obj(
          "reference" -> "success-reference-123",
          "fileStatus" -> JsNumber(1234),
          "uploadDetails" -> Json.obj(
            "fileName" -> "test.csv",
            "fileMimeType" -> "text/csv",
            "uploadTimestamp" -> uploadTimestamp,
            "checksum" ->"abc123",
            "size" -> 1024L
          ),
          "downloadUrl" -> "https://s3.test/download/123"
        )

        json.validate[UpscanCallbackSuccess] mustBe a[JsError]
      }

      "must fail to deserialise from null" in {
        val json = Json.obj(
          "reference" -> "success-reference-123",
          "fileStatus" -> "READY",
          "uploadDetails" -> Json.obj(
            "fileName" -> "test.csv",
            "fileMimeType" -> JsNull,
            "uploadTimestamp" -> uploadTimestamp,
            "checksum" ->"abc123",
            "size" -> 1024L
          ),
          "downloadUrl" -> "https://s3.test/download/123"
        )


        json.validate[UpscanCallbackSuccess] mustBe a[JsError]
      }
    }

    "FailureDetails" - {

      "must serialise/deserialise to and from FailureDetails" in {

        val json = Json.obj(
          "failureReason" -> "REJECTED",
          "message" -> "Not a csv file"
        )

        val expectedResult = FailureDetails(
          failureReason = Rejected,
          message = Some("Not a csv file")
        )

        Json.toJson(expectedResult) mustBe json
        json.validate[FailureDetails] mustBe JsSuccess(expectedResult)
      }

      "must handle invalid data during deserialization" in {

        val json = Json.obj(
          "message" -> "Not a csv file"
        )

        json.validate[FailureDetails] mustBe a[JsError]
      }

      "when all optional values are absent" in {

        val json = Json.obj(
          "failureReason" -> "REJECTED"
        )

        val expectedResult = FailureDetails(
          failureReason = Rejected
        )

        Json.toJson(expectedResult) mustBe json
        json.validate[FailureDetails] mustBe JsSuccess(expectedResult)

      }

      "must fail to deserialise from a number" in {

        val json = Json.obj(
          "failureReason" -> JsNumber(1234),
          "message" -> "Not a csv file"
        )

        json.validate[FailureDetails] mustBe a[JsError]
      }

      "must fail to deserialise from null" in {
        val json = Json.obj(
          "failureReason" -> JsNull,
          "message" -> "Not a csv file"
        )


        json.validate[FailureDetails] mustBe a[JsError]
      }
    }

    "UpscanCallbackFailure" - {

      "must serialise/deserialise to and from UpscanCallbackFailure" in {

        val json = Json.obj(
          "reference" -> "failed-reference-123",
          "fileStatus" -> "FAILED",
          "failureDetails" -> Json.obj(
            "failureReason" -> "QUARANTINE"
          ),
          "uploadDetails" -> Json.obj(
            "fileName" -> "test.csv",
            "fileMimeType" -> "text/csv",
            "uploadTimestamp" -> uploadTimestamp,
            "checksum" -> "abc123",
            "size" -> 1024
          )
        )

        val expectedResult = UpscanCallbackFailure(
          reference = "failed-reference-123",
          fileStatus = "FAILED",
          failureDetails = FailureDetails(Quarantine, None),
          uploadDetails = Some(UploadDetails(
            fileName = "test.csv",
            fileMimeType = "text/csv",
            uploadTimestamp = uploadTimestamp,
            checksum = "abc123",
            size = 1024
          ))
        )

        Json.toJson(expectedResult) mustBe json
        json.validate[UpscanCallbackFailure] mustBe JsSuccess(expectedResult)
      }

      "must handle invalid data during deserialization" in {

        val json = Json.obj(
          "reference" -> "failed-reference-123",
          "fileStatus" -> "FAILED",
          "failureDetails" -> Json.obj(
            "failureReason" -> "QUARANTINE"
          ),
          "uploadDetails" -> Json.obj(
            "fileName" -> "test.csv",
            "fileMimeType" -> "text/csv",
            "checksum" -> "abc123",
            "size" -> 1024
          )
        )

        json.validate[UpscanCallbackFailure] mustBe a[JsError]
      }

      "when all optional values are absent" in {

        val json = Json.obj(
          "reference" -> "failed-reference-123",
          "fileStatus" -> "FAILED",
          "failureDetails" -> Json.obj(
            "failureReason" -> "QUARANTINE"
          )
        )

        val expectedResult = UpscanCallbackFailure(
          reference = "failed-reference-123",
          fileStatus = "FAILED",
          failureDetails = FailureDetails(Quarantine, None),
          uploadDetails = None
        )

        Json.toJson(expectedResult) mustBe json
        json.validate[UpscanCallbackFailure] mustBe JsSuccess(expectedResult)

      }

      "must fail to deserialise from a number" in {

        val json = Json.obj(
          "reference" -> JsNumber(1234),
          "fileStatus" -> "FAILED",
          "failureDetails" -> Json.obj(
            "failureReason" -> "QUARANTINE"
          ),
          "uploadDetails" -> Json.obj(
            "fileName" -> "test.csv",
            "fileMimeType" -> "text/csv",
            "uploadTimestamp" -> uploadTimestamp,
            "checksum" -> "abc123",
            "size" -> 1024
          )
        )

        json.validate[UpscanCallbackFailure] mustBe a[JsError]
      }

      "must fail to deserialise from null" in {
        val json = Json.obj(
          "reference" -> "failed-reference-123",
          "fileStatus" -> "FAILED",
          "failureDetails" -> Json.obj(
            "failureReason" -> JsNull
          ),
          "uploadDetails" -> Json.obj(
            "fileName" -> "test.csv",
            "fileMimeType" -> "text/csv",
            "uploadTimestamp" -> uploadTimestamp,
            "checksum" -> "abc123",
            "size" -> 1024
          )
        )

        json.validate[UpscanCallbackFailure] mustBe a[JsError]
      }
    }

    "UpscanCallbackUploading" - {

      "must serialise/deserialise to and from UpscanCallbackUploading" in {

        val json = Json.obj(
          "reference" -> "reference-1234",
          "fileStatus" -> "UPLOADING"
        )

        val expectedResult = UpscanCallbackUploading(
          reference = "reference-1234",
          fileStatus = "UPLOADING"
        )

        Json.toJson(expectedResult) mustBe json
        json.validate[UpscanCallbackUploading] mustBe JsSuccess(expectedResult)
      }

      "must handle invalid data during deserialization" in {

        val json = Json.obj(
          "reference" -> "reference-1234"
        )

        json.validate[UpscanCallbackUploading] mustBe a[JsError]
      }

      "must fail to deserialise from a number" in {

        val json = Json.obj(
          "reference" -> JsNumber(1234),
          "fileStatus" -> "UPLOADING"
        )

        json.validate[UploadDetails] mustBe a[JsError]
      }

      "must fail to deserialise from null" in {
        val json = Json.obj(
          "reference" -> JsNull,
          "fileStatus" -> "UPLOADING"
        )


        json.validate[UploadDetails] mustBe a[JsError]
      }
    }

    "FileUploadOutcome" - {

      "must serialise/deserialise to and from FileUploadOutcome" in {

        val json = Json.obj(
          "fileName" -> "test.csv",
          "status" -> "READY"
        )

        val expectedResult = FileUploadOutcome(
          fileName = Some("test.csv"),
          status = "READY",
          failureReason = None
        )

        Json.toJson(expectedResult) mustBe json
        json.validate[FileUploadOutcome] mustBe JsSuccess(expectedResult)
      }

      "must handle invalid data during deserialization" in {

        val json = Json.obj(
          "fileName" -> "test.csv"
        )

        json.validate[FileUploadOutcome] mustBe a[JsError]
      }

      "when all optional values are absent" in {

        val json = Json.obj(
          "status" -> "READY"
        )

        val expectedResult = FileUploadOutcome(
          fileName = None,
          status = "READY",
          failureReason = None
        )

        Json.toJson(expectedResult) mustBe json
        json.validate[FileUploadOutcome] mustBe JsSuccess(expectedResult)
      }

      "must fail to deserialise from a number" in {

        val json = Json.obj(
          "fileName" -> "test.csv",
          "status" -> JsNumber(1234)
        )

        json.validate[FileUploadOutcome] mustBe a[JsError]
      }

      "must fail to deserialise from null" in {
        val json = Json.obj(
          "fileName" -> "test.csv",
          "status" -> JsNull
        )

        json.validate[FileUploadOutcome] mustBe a[JsError]
      }
    }
  }
}

package models.fileUpload

import base.SpecBase
import play.api.libs.json.{JsError, JsNull, JsNumber, JsSuccess, Json}

import java.time.Instant

class UploadDocumentSpec extends SpecBase {

  private val now: Instant = Instant.parse("2025-01-01T00:00:00Z")

  "UploadDocument" - {

    "must serialise/deserialise to and from UploadDocument" in {

      val json = Json.obj(
        "_id" -> "testUploadFile",
        "status" -> "UPLOADED",
        "fileName" -> Some("test.csv"),
        "checksum" -> Some("testUploadValue"),
        "size" -> Some(1024L),
        "createdAt" -> Json.obj(
          "$date" -> Json.obj(
            "$numberLong" -> now.toEpochMilli.toString
          )
        ),
        "downloadUrl" -> Some("https://s3.test/download/123")
      )

      val expectedResult = UploadDocument(
        _id = "testUploadFile",
        status = "UPLOADED",
        fileName = Some("test.csv"),
        checksum = Some("testUploadValue"),
        size = Some(1024L),
        failureReason = None,
        createdAt = now,
        downloadUrl = Some("https://s3.test/download/123")
      )

      Json.toJson(expectedResult) mustBe json
      json.validate[UploadDocument] mustBe JsSuccess(expectedResult)
    }

    "must handle invalid data during deserialization" in {

      val json = Json.obj()

      json.validate[UploadDetails] mustBe a[JsError]
    }

    "when all optional values are absent" in {

      val json = Json.obj(
        "_id" -> "testUploadFile",
        "status" -> "UPLOADED",
        "createdAt" -> Json.obj(
          "$date" -> Json.obj(
            "$numberLong" -> now.toEpochMilli.toString
          )
        )
      )

      val expectedResult = UploadDocument(
        _id = "testUploadFile",
        status = "UPLOADED",
        fileName = None,
        checksum = None,
        size = None,
        failureReason = None,
        createdAt = now,
        downloadUrl = None
      )

      Json.toJson(expectedResult) mustBe json
      json.validate[UploadDocument] mustBe JsSuccess(expectedResult)
    }

    "must fail to deserialise from a number" in {

      val json = Json.obj(
        "_id" -> JsNumber(1234),
        "status" -> "UPLOADED",
        "fileName" -> Some("test.csv"),
        "checksum" -> Some("testUploadValue"),
        "size" -> Some(1024L),
        "createdAt" -> Json.obj(
          "$date" -> Json.obj(
            "$numberLong" -> now.toEpochMilli.toString
          )
        ),
        "downloadUrl" -> Some("https://s3.test/download/123")
      )

      json.validate[UpscanCallbackSuccess] mustBe a[JsError]
    }

    "must fail to deserialise from null" in {

      val json = Json.obj(
        "_id" -> "testUploadFile",
        "status" -> JsNull,
        "fileName" -> Some("test.csv"),
        "checksum" -> Some("testUploadValue"),
        "size" -> Some(1024L),
        "createdAt" -> Json.obj(
          "$date" -> Json.obj(
            "$numberLong" -> now.toEpochMilli.toString
          )
        ),
        "downloadUrl" -> Some("https://s3.test/download/123")
      )

      json.validate[UpscanCallbackSuccess] mustBe a[JsError]
    }
  }
}

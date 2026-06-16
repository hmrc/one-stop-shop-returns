package controllers

import base.SpecBase
import models.fileUpload.{FileUploadOutcome, UploadDocument}
import org.mockito.Mockito.when
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.inject.bind
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.UploadRepository

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class FileUploadOutcomeResponseControllerSpec extends SpecBase with ScalaCheckPropertyChecks {
  
  implicit val ec: ExecutionContext = ExecutionContext.global
  private val now: Instant = Instant.parse("2025-01-01T00:00:00Z")

  "FileUploadOutcomeResponseController" - {
    
    "must return 200 and the file name if upload exists" in {
      
      val mockRepository = mock[UploadRepository]
      val reference = "testUploadFile"
      val doc = UploadDocument(
        _id = reference,
        status = "UPLOADED",
        fileName = Some("test.csv"),
        checksum = Some("testUploadFile"),
        size = Some(1024L),
        createdAt = now
      )
      
      when(mockRepository.getUpload(reference)) `thenReturn` Future.successful(Some(doc))
      
      val app = applicationBuilder
        .overrides(bind[UploadRepository].toInstance(mockRepository))
        .build()
      
      running(app) {
        
        val request = FakeRequest(GET, routes.FileUploadOutcomeResponseController.get(reference).url)
        val result = route(app, request).value
        
        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(FileUploadOutcome(Some("test.csv"), "UPLOADED", None))
      }
    }

    "must return 404 if no upload exists for the reference" in {

      val mockRepository = mock[UploadRepository]
      val reference = "testUploadFile"

      when(mockRepository.getUpload(reference)) `thenReturn` Future.successful(None)

      val app = applicationBuilder
        .overrides(bind[UploadRepository].toInstance(mockRepository))
        .build()

      running(app) {

        val request = FakeRequest(GET, routes.FileUploadOutcomeResponseController.get(reference).url)
        val result = route(app, request).value

        status(result) mustEqual NOT_FOUND
        (contentAsJson(result) \ "error").as[String] must include("No upload found")
      }
    }
  }
}

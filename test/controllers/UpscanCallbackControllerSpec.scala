package controllers

import base.SpecBase
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.{verify, verifyNoInteractions, when}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.inject.bind
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import services.upscan.UpscanCallbackService

import scala.concurrent.{ExecutionContext, Future}

class UpscanCallbackControllerSpec extends SpecBase with ScalaCheckPropertyChecks {
  
  implicit val ec: ExecutionContext = ExecutionContext.global

  "UpscanCallbackController" - {

    "handle a success callback and call the service" in {

      val mockService = mock[UpscanCallbackService]
      when(mockService.handleUpscanCallback(any())).thenReturn(Future.successful(()))

      val app = applicationBuilder
        .overrides(bind[UpscanCallbackService].toInstance(mockService))
        .build()

      running(app) {
        val json = Json.parse(
          """
                  {
                    "reference": "123",
                    "fileStatus": "READY",
                    "uploadDetails": {
                      "fileName": "test.csv",
                      "fileMimeType": "text/csv",
                      "uploadTimestamp": "2026-02-09T12:00:00Z",
                      "checksum": "abc123",
                      "size": 1024
                    },
                    "downloadUrl": "https://s3.test/download/123"
                  }
                """)

        val request = FakeRequest(POST, routes.UpscanCallbackController.callback.url).withJsonBody(json)
        val result = route(app, request).value

        status(result) mustEqual OK
        verify(mockService).handleUpscanCallback(any())

      }
    }

    "handle a failure callback and call the service" in {

      val mockService = mock[UpscanCallbackService]
      when(mockService.handleUpscanCallback(any())).thenReturn(Future.successful(()))

      val app = applicationBuilder
        .overrides(bind[UpscanCallbackService].toInstance(mockService))
        .build()

      running(app) {
        val json = Json.parse(
          """
                  {
                    "reference": "123",
                    "fileStatus": "FAILED",
                    "failureDetails": {
                    "failureReason": "QUARANTINE"
                    }
                  }
                """)

        val request = FakeRequest(POST, routes.UpscanCallbackController.callback.url).withJsonBody(json)
        val result = route(app, request).value

        status(result) mustEqual OK
        verify(mockService).handleUpscanCallback(any())
      }
    }

    "return BadRequest on invalid JSON" in {

      val mockService = mock[UpscanCallbackService]

      val app = applicationBuilder
        .overrides(bind[UpscanCallbackService].toInstance(mockService))
        .build()

      running(app) {
        val json = Json.parse("""{"foo": "bar"}""")
        val request = FakeRequest(POST, routes.UpscanCallbackController.callback.url).withJsonBody(json)
        val result = route(app, request).value

        status(result) mustEqual BAD_REQUEST
        verifyNoInteractions(mockService)
      }
    }
  }

}

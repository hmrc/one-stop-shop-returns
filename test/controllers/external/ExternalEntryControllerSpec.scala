package controllers.external

import base.SpecBase
import models.external.{ExternalEntryUrlResponse, ExternalRequest, ExternalResponse}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{doNothing, times, verify, when}
import play.api.http.Status.{BAD_REQUEST, OK}
import play.api.inject
import play.api.libs.json.{JsNull, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.external.ExternalEntryService
import services.AuditService
import uk.gov.hmrc.play.bootstrap.backend.http.ErrorResponse

import scala.concurrent.Future

class ExternalEntryControllerSpec extends SpecBase {

  private val yourAccount = "your-account"
  private val startReturn = "start-your-return"
  private val payment = "make-payment"
  private val externalRequest = ExternalRequest("BTA", "exampleurl")


  ".onExternal" - {

    "when correct ExternalRequest is posted" - {
      "must return OK" in {
        val mockExternalService = mock[ExternalEntryService]
        val mockAuditService = mock[AuditService]

        when(mockExternalService.getExternalResponse(any(), any(), any(), any(), any())) thenReturn Right(ExternalResponse("url"))
        doNothing().when(mockAuditService).audit(any())(any(), any())

        val application = applicationBuilder
          .overrides(inject.bind[ExternalEntryService].toInstance(mockExternalService))
          .overrides(inject.bind[AuditService].toInstance(mockAuditService))
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.ExternalEntryController.onExternal(yourAccount).url).withJsonBody(
            Json.toJson(externalRequest)
          )

          val result = route(application, request).value
          status(result) mustBe OK
          contentAsJson(result).as[ExternalResponse] mustBe ExternalResponse("url")
          verify(mockAuditService, times(1)).audit(any())(any(), any())
        }
      }

      "when navigating to payment page must return OK" in {
        val mockExternalService = mock[ExternalEntryService]
        val mockAuditService = mock[AuditService]

        when(mockExternalService.getExternalResponse(any(), any(), any(), any(), any())) thenReturn Right(ExternalResponse("url"))
        doNothing().when(mockAuditService).audit(any())(any(), any())

        val application = applicationBuilder
          .overrides(inject.bind[ExternalEntryService].toInstance(mockExternalService))
          .overrides(inject.bind[AuditService].toInstance(mockAuditService))
          .build()

        running(application) {
          val request = FakeRequest(
            POST,
            routes.ExternalEntryController.onExternal(payment).url).withJsonBody(
            Json.toJson(externalRequest)
          )

          val result = route(application, request).value
          status(result) mustBe OK
          contentAsJson(result).as[ExternalResponse] mustBe ExternalResponse("url")
          verify(mockAuditService, times(1)).audit(any())(any(), any())
        }
      }

      "must respond with INTERNAL_SERVER_ERROR and not save return url if service responds with NotFound" - {
        "because no period provided where needed" in {
          val mockExternalService = mock[ExternalEntryService]
          val mockAuditService = mock[AuditService]

          when(mockExternalService.getExternalResponse(any(), any(), any(), any(), any())) thenReturn Left(ErrorResponse(500, "Unknown external entry"))
          doNothing().when(mockAuditService).audit(any())(any(), any())

          val application = applicationBuilder
            .overrides(inject.bind[ExternalEntryService].toInstance(mockExternalService))
            .overrides(inject.bind[AuditService].toInstance(mockAuditService))
            .build()

          running(application) {
            val request = FakeRequest(POST, routes.ExternalEntryController.onExternal(startReturn, None).url).withJsonBody(
              Json.toJson(externalRequest)
            )

            val result = route(application, request).value
            status(result) mustBe INTERNAL_SERVER_ERROR
          }
        }
      }
    }

    "must respond with BadRequest" - {
      "when no body provided" in {
        val application = applicationBuilder
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.ExternalEntryController.onExternal(startReturn, Some(period)).url).withJsonBody(JsNull)

          val result = route(application, request).value
          status(result) mustBe BAD_REQUEST
        }
      }

      "when malformed body provided" in {
        val application = applicationBuilder
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.ExternalEntryController.onExternal(startReturn, Some(period)).url).withJsonBody(Json.toJson("wrong body"))

          val result = route(application, request).value
          status(result) mustBe BAD_REQUEST
        }
      }
    }

  }

  ".getExternalEntry" - {

    "when correct request with authorization" - {
      "must respond with correct url when present" in {
        val mockExternalService = mock[ExternalEntryService]
        val url = "/pay-vat-on-goods-sold-to-eu/northern-ireland-register"

        when(mockExternalService.getSavedResponseUrl(any())) thenReturn
          Future.successful(Some(url))

        val application = applicationBuilder
          .overrides(inject.bind[ExternalEntryService].toInstance(mockExternalService))
          .build()

        running(application) {
          val request = FakeRequest(GET, controllers.external.routes.ExternalEntryController.getExternalEntry().url)
          val result = route(application, request).value
          status(result) mustBe OK
          contentAsJson(result).as[ExternalEntryUrlResponse] mustBe ExternalEntryUrlResponse(Some(url))
        }
      }

      "must respond with none when no url present" in {
        val mockExternalService = mock[ExternalEntryService]

        when(mockExternalService.getSavedResponseUrl(any())) thenReturn
          Future.successful(None)

        val application = applicationBuilder
          .overrides(inject.bind[ExternalEntryService].toInstance(mockExternalService))
          .build()

        running(application) {
          val request = FakeRequest(GET, controllers.external.routes.ExternalEntryController.getExternalEntry().url)

          val result = route(application, request).value
          status(result) mustBe OK
          contentAsJson(result).as[ExternalEntryUrlResponse] mustBe ExternalEntryUrlResponse(None)
        }
      }

    }

  }
}


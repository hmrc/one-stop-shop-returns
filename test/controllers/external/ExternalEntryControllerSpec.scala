package controllers.external

import base.SpecBase
import models.external.{ExternalRequest, ExternalResponse}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.http.Status.{BAD_REQUEST, OK}
import play.api.inject
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsNull, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.external.ExternalEntryService
import uk.gov.hmrc.play.bootstrap.backend.http.ErrorResponse

class ExternalEntryControllerSpec extends SpecBase {

  private val yourAccount = "your-account"
  private val startReturn = "start-your-return"
  private val payment = "make-payment"
  private val externalRequest = ExternalRequest("BTA", "exampleurl")


  ".onExternal" - {

    "when correct ExternalRequest is posted" - {
      "must return OK" in {
        val mockExternalService = mock[ExternalEntryService]

        when(mockExternalService.getExternalResponse(any(), any(), any(), any(), any())) thenReturn Right(ExternalResponse("url"))

        val application = applicationBuilder
          .overrides(inject.bind[ExternalEntryService].toInstance(mockExternalService))
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.ExternalEntryController.onExternal(yourAccount).url).withJsonBody(
            Json.toJson(externalRequest)
          )

          val result = route(application, request).value
          status(result) mustBe OK
          contentAsJson(result).as[ExternalResponse] mustBe ExternalResponse("url")
        }
      }

      "when navigating to payment page must return OK" in {
        val mockExternalService = mock[ExternalEntryService]

        when(mockExternalService.getExternalResponse(any(), any(), any(), any(), any())) thenReturn Right(ExternalResponse("url"))

        val application = applicationBuilder
          .overrides(inject.bind[ExternalEntryService].toInstance(mockExternalService))
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
        }
      }

      "must respond with INTERNAL_SERVER_ERROR and not save return url if service responds with NotFound" - {
        "because no period provided where needed" in {
          val mockExternalService = mock[ExternalEntryService]

          when(mockExternalService.getExternalResponse(any(), any(), any(), any(), any())) thenReturn Left(ErrorResponse(500, "Unknown external entry"))

          val application = applicationBuilder
            .overrides(inject.bind[ExternalEntryService].toInstance(mockExternalService))
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
}


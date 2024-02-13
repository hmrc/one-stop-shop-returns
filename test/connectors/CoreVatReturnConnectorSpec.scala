package connectors

import base.SpecBase
import com.github.tomakehurst.wiremock.client.WireMock._
import models.core.{CoreErrorResponse, EisErrorResponse}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.time.{Seconds, Span}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.running

import java.time.Instant
import java.util.UUID

class CoreVatReturnConnectorSpec extends SpecBase with WireMockHelper {

  private val url = "/one-stop-shop-returns-stub/vec/submitvatreturn/v1/oss"

  private def application: Application =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.if.host" -> "127.0.0.1",
        "microservice.services.if.port" -> server.port,
        "microservice.services.if.authorizationToken" -> "auth-token",
        "microservice.services.if.environment" -> "test-environment",
      )
      .build()

  "submit" - {
    "when the server returns ACCEPTED" - {
      "must return gracefully" in {
        val app = application

        server.stubFor(
          post(urlEqualTo(url))
            .willReturn(aResponse().withStatus(202))
        )

        running(app) {
          val connector = app.injector.instanceOf[CoreVatReturnConnector]
          val result = connector.submit(coreVatReturn).futureValue

          result mustBe Right(())
        }
      }
    }

    "when the server returns an error" - {
      "the error is parseable" in {

        val app = application

        val timestamp = "2021-01-18T12:40:45Z"
        val uuid = "f3204b9d-ed02-4d6f-8ff6-2339daef8241"

        val errorResponseJson =
          s"""{"errorDetail": {
            |  "timestamp": "$timestamp",
            |  "transactionId": "$uuid",
            |  "errorCode": "OSS_405",
            |  "errorMessage": "Method Not Allowed"
            |}}""".stripMargin

        server.stubFor(
          post(urlEqualTo(url))
            .willReturn(aResponse()
              .withStatus(404)
              .withBody(errorResponseJson)
            )
        )

        running(app) {
          val connector = app.injector.instanceOf[CoreVatReturnConnector]
          val result = connector.submit(coreVatReturn).futureValue

          val expectedResponse = EisErrorResponse(CoreErrorResponse(Instant.parse(timestamp),  Some(UUID.fromString(uuid)), "OSS_405", "Method Not Allowed"))

          result mustBe Left(expectedResponse)
        }

      }

      "Http Exception must result in EisErrorResponse" in {

        val app = application

        server.stubFor(
          post(urlEqualTo(url))
            .willReturn(aResponse()
              .withStatus(504)
              .withFixedDelay(21000)
            )
        )

        running(app) {
          val connector = app.injector.instanceOf[CoreVatReturnConnector]
          whenReady(connector.submit(coreVatReturn), Timeout(Span(30, Seconds))) { exp =>
            exp.isLeft mustBe true
            exp.left.toOption.get mustBe a[EisErrorResponse]
          }

        }

      }

      "the error is not parseable" in {

        val app = application

        val errorResponseJson = """{}"""

        server.stubFor(
          post(urlEqualTo(url))
            .willReturn(aResponse()
              .withStatus(404)
              .withBody(errorResponseJson)
            )
        )

        running(app) {
          val connector = app.injector.instanceOf[CoreVatReturnConnector]
          val result = connector.submit(coreVatReturn).futureValue

          val expectedResponse = EisErrorResponse(CoreErrorResponse(result.left.toOption.get.errorDetail.timestamp, result.left.toOption.get.errorDetail.transactionId, s"UNEXPECTED_404", errorResponseJson))

          result mustBe Left(expectedResponse)
        }
      }

      "the response has no json body" in {

        val app = application


        server.stubFor(
          post(urlEqualTo(url))
            .willReturn(aResponse()
              .withStatus(404)
            )
        )

        running(app) {
          val connector = app.injector.instanceOf[CoreVatReturnConnector]
          val result = connector.submit(coreVatReturn).futureValue

          val expectedResponse = EisErrorResponse(CoreErrorResponse(result.left.toOption.get.errorDetail.timestamp, result.left.toOption.get.errorDetail.transactionId, "UNEXPECTED_404", "The response body was empty"))

          result mustBe Left(expectedResponse)
        }
      }
    }

  }
}

package connectors

import base.SpecBase
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.matching.EqualToPattern
import models.{GatewayTimeout, EtmpListObligationsError}
import models.etmp.{EtmpObligations, EtmpObligationsQueryParameters}
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.time.{Seconds, Span}
import play.api.Application
import play.api.http.Status.{GATEWAY_TIMEOUT, NOT_FOUND, OK}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers.running
import utils.Formatters.etmpDateFormatter

import java.time.LocalDate

class VatReturnConnectorSpec extends SpecBase with WireMockHelper {

  private def application: Application =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.if.host" -> "127.0.0.1",
        "microservice.services.if.port" -> server.port,
        "microservice.services.if.authorizationToken" -> "auth-token",
        "microservice.services.if.environment" -> "test-environment",
        "microservice.services.etmp-list-obligations.host" -> "127.0.0.1",
        "microservice.services.etmp-list-obligations.port" -> server.port,
        "microservice.services.etmp-list-obligations.authorizationToken" -> "auth-token",
        "microservice.services.etmp-list-obligations.environment" -> "test-environment",
        "microservice.services.etmp-list-obligations.idType" -> "OSS",
        "microservice.services.etmp-list-obligations.regimeType" -> "OSS"
      )
      .build()

  "getObligations" - {

    val idType = "OSS"
    val vrn = "123456789"
    val regimeType = "OSS"
    val dateFrom = LocalDate.now(stubClock).format(etmpDateFormatter)
    val dateTo = LocalDate.now(stubClock).format(etmpDateFormatter)

    val queryParameters: EtmpObligationsQueryParameters = EtmpObligationsQueryParameters(fromDate = dateFrom, toDate = dateTo, status = None)
    val obligationsUrl = s"/one-stop-shop-returns-stub/enterprise/obligation-data/$idType/$vrn/$regimeType"

    "must return OK when server return OK and a recognised payload without a status" in {

      val obligations = arbitrary[EtmpObligations].sample.value
      val jsonStringBody = Json.toJson(obligations).toString()

      val app = application

      server.stubFor(
        get(urlEqualTo(s"$obligationsUrl?from=${queryParameters.fromDate}&to=${queryParameters.toDate}"))
          .withQueryParam("from", new EqualToPattern(dateFrom))
          .withQueryParam("to", new EqualToPattern(dateTo))
          .withHeader("Authorization", equalTo("Bearer auth-token"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(jsonStringBody)
          )
      )

      running(app) {
        val connector = app.injector.instanceOf[VatReturnConnector]
        val result = connector.getObligations(vrn, queryParameters).futureValue

        result mustBe Right(obligations)
      }
    }
    "must return OK when server return OK and a recognised payload with a status" in {

      val status = "F"
      val queryParameters: EtmpObligationsQueryParameters = EtmpObligationsQueryParameters(fromDate = dateFrom, toDate = dateTo, status = Some(status))

      val obligations = arbitrary[EtmpObligations].sample.value
      val jsonStringBody = Json.toJson(obligations).toString()

      val app = application

      server.stubFor(
        get(urlEqualTo(s"$obligationsUrl?from=${queryParameters.fromDate}&to=${queryParameters.toDate}&status=$status"))
          .withQueryParam("from", new EqualToPattern(dateFrom))
          .withQueryParam("to", new EqualToPattern(dateTo))
          .withQueryParam("status", new EqualToPattern(status))
          .withHeader("Authorization", equalTo("Bearer auth-token"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(jsonStringBody)
          )
      )

      running(app) {
        val connector = app.injector.instanceOf[VatReturnConnector]
        val result = connector.getObligations(vrn, queryParameters).futureValue

        result mustBe Right(obligations)
      }
    }

    "when the server returns an error" - {

      "Http Exception must result in GatewayTimeout" in {

        val app = application

        server.stubFor(
          get(urlEqualTo(s"$obligationsUrl?from=${queryParameters.fromDate}&to=${queryParameters.toDate}"))
            .withQueryParam("from", new EqualToPattern(dateFrom))
            .withQueryParam("to", new EqualToPattern(dateTo))
            .withHeader("Authorization", equalTo("Bearer auth-token"))
            .willReturn(aResponse()
              .withStatus(GATEWAY_TIMEOUT)
              .withFixedDelay(21000)
            )
        )

        running(app) {
          val connector = app.injector.instanceOf[VatReturnConnector]
          whenReady(connector.getObligations(vrn, queryParameters), Timeout(Span(30, Seconds))) { exp =>
            exp.isLeft mustBe true
            exp.left.toOption.get mustBe GatewayTimeout
          }
        }
      }

      "it's handled and returned" in {

        val app = application

        val errorResponseJson = """{}"""

        server.stubFor(
          get(urlEqualTo(s"$obligationsUrl?from=${queryParameters.fromDate}&to=${queryParameters.toDate}"))
            .withQueryParam("from", new EqualToPattern(dateFrom))
            .withQueryParam("to", new EqualToPattern(dateTo))
            .withHeader("Authorization", equalTo("Bearer auth-token"))
            .willReturn(aResponse()
              .withStatus(NOT_FOUND)
              .withBody(errorResponseJson)
            )
        )

        running(app) {
          val connector = app.injector.instanceOf[VatReturnConnector]
          val result = connector.getObligations(vrn, queryParameters).futureValue

          val expectedResponse = EtmpListObligationsError("404", errorResponseJson)

          result mustBe Left(expectedResponse)
        }
      }

      "the response has no json body" in {

        val app = application

        server.stubFor(
          get(urlEqualTo(s"$obligationsUrl?from=${queryParameters.fromDate}&to=${queryParameters.toDate}"))
            .withQueryParam("from", new EqualToPattern(dateFrom))
            .withQueryParam("to", new EqualToPattern(dateTo))
            .withHeader("Authorization", equalTo("Bearer auth-token"))
            .willReturn(aResponse()
              .withStatus(NOT_FOUND)
            )
        )

        running(app) {
          val connector = app.injector.instanceOf[VatReturnConnector]
          val result = connector.getObligations(vrn, queryParameters).futureValue

          val expectedResponse = EtmpListObligationsError("UNEXPECTED_404", "The response body was empty")

          result mustBe Left(expectedResponse)
        }
      }
    }
  }
}


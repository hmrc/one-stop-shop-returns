package connectors

import base.SpecBase
import com.github.tomakehurst.wiremock.client.WireMock._
import models.core.{CoreErrorResponse, EisErrorResponse}
import models.etmp.EtmpVatReturn
import models.responses.{ErrorResponse, GatewayTimeout, InvalidJson, UnexpectedResponseStatus}
import org.scalacheck.Gen
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.time.{Seconds, Span}
import play.api.Application
import play.api.http.Status.{BAD_REQUEST, GATEWAY_TIMEOUT, INTERNAL_SERVER_ERROR, NOT_FOUND}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers.running

import java.time.{Instant, LocalDate, LocalDateTime}
import java.util.UUID

class CoreVatReturnConnectorSpec extends SpecBase with WireMockHelper {

  private def application: Application =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.if.host" -> "127.0.0.1",
        "microservice.services.if.port" -> server.port,
        "microservice.services.if.authorizationToken" -> "auth-token",
        "microservice.services.if.environment" -> "test-environment",
        "microservice.services.display-vat-return.host" -> "127.0.0.1",
        "microservice.services.display-vat-return.port" -> server.port,
        "microservice.services.display-vat-return.authorizationToken" -> "auth-token",
        "microservice.services.display-vat-return.environment" -> "test-environment",
      ).build()

  "submit" - {

    val url = "/one-stop-shop-returns-stub/vec/submitvatreturn/v1/oss"

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

          val expectedResponse = EisErrorResponse(CoreErrorResponse(Instant.parse(timestamp), Some(UUID.fromString(uuid)), "OSS_405", "Method Not Allowed"))

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

  ".get" - {

    val url: String = "/one-stop-shop-returns-stub/vec/ossreturns/viewreturns/v1"

    "must return Right(VatReturn) when the server returns OK with a valid payload" in {

      val vatReturn: EtmpVatReturn = EtmpVatReturn(
        returnReference = vrn.vrn,
        returnVersion = LocalDateTime.now(stubClock),
        periodKey = "periodKey",
        returnPeriodFrom = LocalDate.now(stubClock).plusMonths(3),
        returnPeriodTo = LocalDate.now(stubClock),
        goodsSupplied = Seq.empty,
        totalVATGoodsSuppliedGBP = BigDecimal(100),
        totalVATAmountPayable = BigDecimal(100),
        totalVATAmountPayableAllSpplied = BigDecimal(100),
        correctionPreviousVATReturn = Seq.empty,
        totalVATAmountFromCorrectionGBP = BigDecimal(100),
        balanceOfVATDueForMS = Seq.empty,
        totalVATAmountDueForAllMSGBP = BigDecimal(100),
        paymentReference = "paymentReference"
      )

      val responseJson = Json.toJson(vatReturn).toString()

      val app = application

      server.stubFor(
        get(urlEqualTo(url))
          .willReturn(ok()
            .withBody(responseJson)
          )
      )

      running(app) {
        val connector = app.injector.instanceOf[CoreVatReturnConnector]
        val result = connector.get.futureValue

        result mustBe Right(vatReturn)
      }
    }

    "must return Left(InvalidJson) when server returns an unparsable payload" in {

      val app = application

      val invalidResponseBody = Json.toJson("""{}""").toString()

      server.stubFor(
        get(urlEqualTo(url))
          .willReturn(aResponse()
            .withBody(invalidResponseBody)
          )
      )

      running(app) {
        val connector = app.injector.instanceOf[CoreVatReturnConnector]
        val result = connector.get.futureValue

        result mustBe Left(InvalidJson)
      }
    }

    "must return Left(UnexpectedResponseStatus) when the server returns with an error" in {

      val status = Gen.oneOf(BAD_REQUEST, NOT_FOUND, INTERNAL_SERVER_ERROR).sample.value

      val app = application

      server.stubFor(
        get(urlEqualTo(url))
          .willReturn(aResponse()
            .withStatus(status))
      )

      running(app) {

        val connector = app.injector.instanceOf[CoreVatReturnConnector]

        val result = connector.get.futureValue

        result mustBe Left(UnexpectedResponseStatus(status, s"Unexpected response form Display VAT Return with status: $status and response body: "))
      }
    }

    "must return GatewayTimeout when Http Exception thrown" in {

      val status = GATEWAY_TIMEOUT
      val app = application

      server.stubFor(
        get(urlEqualTo(url))
          .willReturn(aResponse()
            .withStatus(status)
            .withFixedDelay(21000)
          )
      )

      running(app) {

        val connector = app.injector.instanceOf[CoreVatReturnConnector]

        whenReady(connector.get, Timeout(Span(30, Seconds))) { exp =>
          exp.isLeft mustBe true
          exp.left.toOption.get mustBe a[ErrorResponse]
          exp.left.toOption.get mustBe GatewayTimeout
        }
      }
    }
  }
}


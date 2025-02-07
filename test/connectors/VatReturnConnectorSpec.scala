package connectors

import base.SpecBase
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.matching.EqualToPattern
import models.etmp.{EtmpObligations, EtmpObligationsQueryParameters, EtmpVatReturn}
import models.*
import models.Period.toEtmpPeriodString
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.time.{Seconds, Span}
import play.api.Application
import play.api.http.Status.*
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers.running
import utils.Formatters.etmpDateFormatter

import java.time.{LocalDate, LocalDateTime}

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
        "microservice.services.etmp-list-obligations.regimeType" -> "OSS",
        "microservice.services.display-vat-return.host" -> "127.0.0.1",
        "microservice.services.display-vat-return.port" -> server.port,
        "microservice.services.display-vat-return.authorizationToken" -> "auth-token",
        "microservice.services.display-vat-return.environment" -> "test-environment"
      ).build()

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

  ".get" - {

    val url: String = s"/one-stop-shop-returns-stub/vec/ossreturns/viewreturns/v1/$vrn/${toEtmpPeriodString(period)}"

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
        val connector = app.injector.instanceOf[VatReturnConnector]
        val result = connector.get(vrn, period).futureValue

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
        val connector = app.injector.instanceOf[VatReturnConnector]
        val result = connector.get(vrn, period).futureValue

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

        val connector = app.injector.instanceOf[VatReturnConnector]

        val result = connector.get(vrn, period).futureValue

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

        val connector = app.injector.instanceOf[VatReturnConnector]

        whenReady(connector.get(vrn, period), Timeout(Span(30, Seconds))) { exp =>
          exp.left.toOption.get mustBe a[ErrorResponse]
          exp.left.toOption.get mustBe GatewayTimeout
        }
      }
    }
  }
}



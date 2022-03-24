package connectors

import base.SpecBase
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.matching.EqualToPattern
import models.financialdata.{FinancialData, FinancialDataQueryParameters, FinancialTransaction, Item}
import models.Period
import models.Quarter.Q3
import models.des.{DesErrorResponse, UnexpectedResponseStatus}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.time.{Seconds, Span}
import play.api.Application
import play.api.http.Status.{BAD_REQUEST, IM_A_TEAPOT, NOT_FOUND, SERVICE_UNAVAILABLE}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.running

import java.time.{LocalDate, ZonedDateTime}

class FinancialDataConnectorSpec extends SpecBase with WireMockHelper {

  private val desUrl = s"/one-stop-shop-returns-stub/enterprise/financial-data/VRN/${vrn.value}/ECOM"
  private val queryParameters = FinancialDataQueryParameters(fromDate = Some(period.firstDay), toDate = Some(LocalDate.now()))

  private def application: Application =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.des.host" -> "127.0.0.1",
        "microservice.services.des.port" -> server.port,
        "microservice.services.des.authorizationToken" -> "auth-token",
        "microservice.services.des.environment" -> "test-environment",
        "microservice.services.des.regimeType" -> "ECOM"
      )
      .build()

  "getFinancialData" - {
    "when the server returns OK and a recognised payload" - {
      "must return a FinancialDataResponse" in {
        val app = application

        val zonedNow = ZonedDateTime.now()
        val period = Period(2021, Q3)

        val responseJson =
          s"""{
            | "idType": "VRN",
            | "idNumber": "123456789",
            | "regimeType": "ECOM",
            | "processingDate": "${zonedNow.toString}",
            | "financialTransactions": [
            |   {
            |     "chargeType": "G Ret AT EU-OMS",
            |     "taxPeriodFrom": "${period.firstDay}",
            |     "taxPeriodTo": "${period.lastDay}",
            |     "originalAmount": 1000,
            |     "outstandingAmount": 500,
            |     "clearedAmount": 500,
            |     "items": [
            |       {
            |         "amount": 500,
            |         "clearingReason": "",
            |         "paymentReference": "",
            |         "paymentAmount": 500,
            |         "paymentMethod": ""
            |       }
            |     ]
            |   }
            | ]
            |}""".stripMargin


        val items = Seq(
          Item(
            amount = Some(BigDecimal(500)),
            clearingReason = Some(""),
            paymentReference = Some(""),
            paymentAmount = Some(BigDecimal(500)),
            paymentMethod = Some("")
          )
        )

        val financialTransactions = Seq(
          FinancialTransaction(
            chargeType = Some("G Ret AT EU-OMS"),
            mainType = None,
            taxPeriodFrom = Some(period.firstDay),
            taxPeriodTo = Some(period.lastDay),
            originalAmount = Some(BigDecimal(1000)),
            outstandingAmount = Some(BigDecimal(500)),
            clearedAmount = Some(BigDecimal(500)),
            items = Some(items)
          )
        )

        server.stubFor(
          get(urlEqualTo(desUrl + s"?dateFrom=${period.firstDay.toString}&dateTo=${queryParameters.toDate.get.toString}"))
            .withQueryParam("dateFrom", new EqualToPattern(period.firstDay.toString))
            .withQueryParam("dateTo", new EqualToPattern(LocalDate.now.toString))
            .withHeader("Authorization", equalTo("Bearer auth-token"))
            .withHeader("Environment", equalTo("test-environment"))
            .willReturn(ok(responseJson))
        )

        running(app) {
          val connector = app.injector.instanceOf[FinancialDataConnector]
          val result = connector.getFinancialData(vrn, queryParameters).futureValue

          val expectedResult = Right(Some(FinancialData(
            idType = Some("VRN"),
            idNumber = Some("123456789"),
            regimeType = Some("ECOM"),
            processingDate = zonedNow,
            financialTransactions = Option(financialTransactions))))

          result mustEqual expectedResult
        }
      }
    }

    "must return None when server returns Not Found" in {
        server.stubFor(
          get(urlEqualTo(desUrl + s"?dateFrom=${period.firstDay.toString}&dateTo=${queryParameters.toDate.get.toString}"))
            .withQueryParam("dateFrom", new EqualToPattern(period.firstDay.toString))
            .withQueryParam("dateTo", new EqualToPattern(LocalDate.now.toString))
            .withHeader("Authorization", equalTo("Bearer auth-token"))
            .withHeader("Environment", equalTo("test-environment"))
            .willReturn(
              aResponse()
                .withStatus(NOT_FOUND)
            )
        )

        running(application) {
          val connector = application.injector.instanceOf[FinancialDataConnector]
          val result = connector.getFinancialData(vrn, queryParameters).futureValue
          result mustBe Right(None)
        }

    }

    "must return DesErrorResponse" - {
      "when server returns Http Exception" in {
        server.stubFor(
          get(urlEqualTo(desUrl + s"?dateFrom=${period.firstDay.toString}&dateTo=${queryParameters.toDate.get.toString}"))
            .withQueryParam("dateFrom", new EqualToPattern(period.firstDay.toString))
            .withQueryParam("dateTo", new EqualToPattern(LocalDate.now.toString))
            .withHeader("Authorization", equalTo("Bearer auth-token"))
            .withHeader("Environment", equalTo("test-environment"))
            .willReturn(
              aResponse()
                .withStatus(504)
                .withFixedDelay(21000)
            )
        )

        running(application) {
          val connector = application.injector.instanceOf[FinancialDataConnector]
          whenReady(connector.getFinancialData(vrn, queryParameters), Timeout(Span(30, Seconds))) { exp =>
            exp.isLeft mustBe true
            exp.left.get mustBe a[DesErrorResponse]
          }

        }
      }

      Seq(BAD_REQUEST, SERVICE_UNAVAILABLE, IM_A_TEAPOT).foreach{
        status =>
          s"when server returns status $status" in {
            server.stubFor(
              get(urlEqualTo(desUrl + s"?dateFrom=${period.firstDay.toString}&dateTo=${queryParameters.toDate.get.toString}"))
                .withQueryParam("dateFrom", new EqualToPattern(period.firstDay.toString))
                .withQueryParam("dateTo", new EqualToPattern(LocalDate.now.toString))
                .withHeader("Authorization", equalTo("Bearer auth-token"))
                .withHeader("Environment", equalTo("test-environment"))
                .willReturn(
                  aResponse()
                    .withStatus(status)
                )
            )

            running(application) {
              val connector = application.injector.instanceOf[FinancialDataConnector]
              val result = connector.getFinancialData(vrn, queryParameters).futureValue
              result mustBe Left(UnexpectedResponseStatus(status, s"Unexpected response from DES, received status $status"))
            }
          }
      }

    }
  }

}

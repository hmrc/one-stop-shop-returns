package connectors

import base.SpecBase
import com.github.tomakehurst.wiremock.client.WireMock._
import models.corrections.ReturnCorrectionValue
import models.{Country, InvalidJson, UnexpectedResponseStatus}
import models.Period.toEtmpPeriodString
import org.scalacheck.Arbitrary.arbitrary
import play.api.Application
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK}
import play.api.libs.json.Json
import play.api.test.Helpers.running

class ReturnCorrectionConnectorSpec
  extends SpecBase
    with WireMockHelper {

  private val returnCorrectionValueResponse: ReturnCorrectionValue = arbitrary[ReturnCorrectionValue].sample.value

  private val country: Country = arbitraryCountry.arbitrary.sample.value
  private val periodKey = toEtmpPeriodString(arbitraryPeriod.arbitrary.sample.value)

  private def application: Application =
    applicationBuilder
      .configure(
        "microservice.services.return-correction.port" -> server.port(),
        "microservice.services.return-correction.host" -> "127.0.0.1",
        "microservice.services.return-correction.authorizationToken" -> "auth-token",
        "microservice.services.return-correction.environment" -> "test-environment"
      )
      .build()

  "ReturnCorrectionConnector" - {

    val url: String = s"/one-stop-shop-returns-stub/vec/ossreturns/returncorrection/v1/$vrn/${country.code}/$periodKey"

    "must return Right(ReturnCorrectionValue) when server returns CREATED" in {

      running(application) {

        val connector = application.injector.instanceOf[ReturnCorrectionConnector]

        val responseBody = Json.toJson(returnCorrectionValueResponse).toString()

        server.stubFor(
          get(urlEqualTo(url)).willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(responseBody)
          )
        )

        val result = connector.getMaximumCorrectionValue(vrn, country.code, periodKey).futureValue

        result mustBe Right(returnCorrectionValueResponse)
      }
    }

    "must return Left(InvalidJson) when the response cannot be parsed correctly" in {

      running(application) {

        val connector = application.injector.instanceOf[ReturnCorrectionConnector]

        val responseBody = """{"foo" : "bar"}"""

        server.stubFor(
          get(urlEqualTo(url)).willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(responseBody)
          )
        )

        val result = connector.getMaximumCorrectionValue(vrn, country.code, periodKey).futureValue

        result mustBe Left(InvalidJson)
      }
    }

    "must return Left(UnexpectedResponseStatus) when server returns an error" in {

      val errorMessage: String = s"Unexpected response from Return Correction. Received status: $INTERNAL_SERVER_ERROR with response body: "

      running(application) {

        val connector = application.injector.instanceOf[ReturnCorrectionConnector]

        server.stubFor(
          get(urlEqualTo(url)).willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
        )

        val result = connector.getMaximumCorrectionValue(vrn, country.code, periodKey).futureValue

        result mustBe Left(UnexpectedResponseStatus(INTERNAL_SERVER_ERROR, errorMessage))
      }
    }
  }
}

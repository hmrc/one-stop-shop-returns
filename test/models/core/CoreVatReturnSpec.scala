package models.core

import base.SpecBase
import play.api.libs.json.{JsSuccess, Json}

import java.time.{Instant, LocalDate, LocalDateTime}

class CoreVatReturnSpec extends SpecBase {

  "CoreVatReturn" - {
    "json format correctly" in {
      val testJson =
        s"""{
          |  "vatReturnReferenceNumber": "XI/XI195940512/Q1.2023",
          |  "version": "2021-07-01T09:21:29.922Z",
          |  "traderId": {
          |    "vatNumber": "123456789012",
          |    "issuedBy": "XI"
          |  },
          |  "period": {
          |    "year": 2021,
          |    "quarter": 3
          |  },
          |  "startDate": "2021-03-12",
          |  "endDate": "2021-04-21",
          |  "submissionDateTime": "2021-07-01T09:21:29.922Z",
          |  "totalAmountVatDueGBP": 1000,
          |  "msconSupplies": [
          |    {
          |      "msconCountryCode": "DE",
          |      "balanceOfVatDueGBP": 237,
          |      "grandTotalMsidGoodsGBP": 134,
          |      "grandTotalMsestGoodsGBP": 123,
          |      "correctionsTotalGBP": -20,
          |      "msidSupplies": [
          |        {
          |          "supplyType": "GOODS",
          |          "vatRate": 12.5,
          |          "vatRateType": "STANDARD",
          |          "taxableAmountGBP": 200,
          |          "vatAmountGBP": 134
          |        }
          |      ],
          |      "msestSupplies": [
          |        {
          |          "countryCode": "DE",
          |          "supplies": [
          |            {
          |              "supplyType": "GOODS",
          |              "vatRate": 12.4,
          |              "vatRateType": "REDUCED",
          |              "taxableAmountGBP": 1000,
          |              "vatAmountGBP": 123
          |            }
          |          ]
          |        }
          |      ],
          |      "corrections": [
          |        {
          |          "period": {
          |            "year": 2021,
          |            "quarter": 3
          |          },
          |          "totalVatAmountCorrectionGBP": -20
          |        }
          |      ]
          |    }
          |  ],
          |  "changeDate" : "${Instant.now(stubClock)}"
          |}""".stripMargin

      val result = Json.parse(testJson).validateOpt[CoreVatReturn]

      val expectedModel = CoreVatReturn(
        vatReturnReferenceNumber = "XI/XI195940512/Q1.2023",
        version = Instant.parse("2021-07-01T09:21:29.922Z"),
        traderId = CoreTraderId(vatNumber = "123456789012", issuedBy = "XI"),
        period = CorePeriod(year = 2021, quarter = 3),
        startDate = LocalDate.of(2021, 3, 12),
        endDate = LocalDate.of(2021, 4, 21),
        submissionDateTime = Instant.parse("2021-07-01T09:21:29.922Z"),
        totalAmountVatDueGBP = BigDecimal(1000),
        msconSupplies = List(CoreMsconSupply(
          msconCountryCode = "DE",
          balanceOfVatDueGBP = BigDecimal(237),
          grandTotalMsidGoodsGBP = BigDecimal(134),
          grandTotalMsestGoodsGBP = BigDecimal(123),
          correctionsTotalGBP = BigDecimal(-20),
          msidSupplies = List(CoreSupply(
            supplyType = "GOODS",
            vatRate = BigDecimal(12.5),
            vatRateType = "STANDARD",
            taxableAmountGBP = BigDecimal(200),
            vatAmountGBP = BigDecimal(134)
          )),
          msestSupplies = List(CoreMsestSupply(
            countryCode = Some("DE"),
            euTraderId = None,
            supplies = List(CoreSupply(
              supplyType = "GOODS",
              vatRate = BigDecimal(12.4),
              vatRateType = "REDUCED",
              taxableAmountGBP = BigDecimal(1000),
              vatAmountGBP = BigDecimal(123)
            ))
          )),
          corrections = List(CoreCorrection(
            period = CorePeriod(
              year = 2021,
              quarter = 3
            ),
            totalVatAmountCorrectionGBP = BigDecimal(-20)
          ))
        )),
        changeDate = Some(Instant.now(stubClock))
      )

      result mustBe JsSuccess(Some(expectedModel))
    }
  }

}

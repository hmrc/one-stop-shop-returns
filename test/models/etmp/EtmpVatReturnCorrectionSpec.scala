package models.etmp

import base.SpecBase
import play.api.libs.json.{JsSuccess, Json}

class EtmpVatReturnCorrectionSpec extends SpecBase {

  private val etmpVatReturnCorrection: EtmpVatReturnCorrection = arbitraryEtmpVatReturnCorrection.arbitrary.sample.value

  "EtmpVatReturnCorrection" - {

    "must serialise and deserialise from and to an EtmpVatReturnCorrection" in {

      val json = Json.obj(
        "periodKey" -> etmpVatReturnCorrection.periodKey,
        "periodFrom" -> etmpVatReturnCorrection.periodFrom,
        "periodTo" -> etmpVatReturnCorrection.periodTo,
        "msOfConsumption" -> etmpVatReturnCorrection.msOfConsumption,
        "totalVATAmountCorrectionGBP" -> etmpVatReturnCorrection.totalVATAmountCorrectionGBP,
        "totalVATAmountCorrectionEUR" -> etmpVatReturnCorrection.totalVATAmountCorrectionEUR
      )

      val expectedResult = EtmpVatReturnCorrection(
        periodKey = etmpVatReturnCorrection.periodKey,
        periodFrom = etmpVatReturnCorrection.periodFrom,
        periodTo = etmpVatReturnCorrection.periodTo,
        msOfConsumption = etmpVatReturnCorrection.msOfConsumption,
        totalVATAmountCorrectionGBP = etmpVatReturnCorrection.totalVATAmountCorrectionGBP,
        totalVATAmountCorrectionEUR = etmpVatReturnCorrection.totalVATAmountCorrectionEUR
      )

      Json.toJson(expectedResult) mustBe json
      json.validate[EtmpVatReturnCorrection] mustBe JsSuccess(expectedResult)
    }
  }
}

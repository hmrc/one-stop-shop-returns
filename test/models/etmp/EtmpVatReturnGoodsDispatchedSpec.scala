package models.etmp

import base.SpecBase
import play.api.libs.json.{JsSuccess, Json}

class EtmpVatReturnGoodsDispatchedSpec extends SpecBase {

  private val etmpVatReturnGoodsDispatched: EtmpVatReturnGoodsDispatched = arbitraryEtmpVatReturnGoodsDispatched.arbitrary.sample.value

  "EtmpVatReturnGoodsDispatched" - {

    "must serialise and deserialise from and to an EtmpVatReturnGoodsDispatched" in {

      val json = Json.obj(
        "msOfEstablishment" -> etmpVatReturnGoodsDispatched.msOfEstablishment,
        "msOfConsumption" -> etmpVatReturnGoodsDispatched.msOfConsumption,
        "vatRateType" -> etmpVatReturnGoodsDispatched.vatRateType,
        "taxableAmountGBP" -> etmpVatReturnGoodsDispatched.taxableAmountGBP,
        "vatAmountGBP" -> etmpVatReturnGoodsDispatched.vatAmountGBP
      )

      val expectedResult = EtmpVatReturnGoodsDispatched(
        msOfEstablishment = etmpVatReturnGoodsDispatched.msOfEstablishment,
        msOfConsumption = etmpVatReturnGoodsDispatched.msOfConsumption,
        vatRateType = etmpVatReturnGoodsDispatched.vatRateType,
        taxableAmountGBP = etmpVatReturnGoodsDispatched.taxableAmountGBP,
        vatAmountGBP = etmpVatReturnGoodsDispatched.vatAmountGBP
      )

      Json.toJson(expectedResult) mustBe json
      json.validate[EtmpVatReturnGoodsDispatched] mustBe JsSuccess(expectedResult)
    }
  }
}

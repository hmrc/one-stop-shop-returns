package models.etmp

import base.SpecBase
import play.api.libs.json.{JsSuccess, Json}

class EtmpVatReturnGoodsSuppliedSpec extends SpecBase {

  private val etmpVatReturnGoodsSupplied: EtmpVatReturnGoodsSupplied = arbitraryEtmpVatReturnGoodsSupplied.arbitrary.sample.value

  "EtmpVatReturnGoodsSupplied" - {

    "must serialise and deserialise from and to an EtmpVatReturnGoodsSupplied" in {

      val json = Json.obj(
        "msOfConsumption" -> etmpVatReturnGoodsSupplied.msOfConsumption,
        "vatRateType" -> etmpVatReturnGoodsSupplied.vatRateType,
        "taxableAmountGBP" -> etmpVatReturnGoodsSupplied.taxableAmountGBP,
        "vatAmountGBP" -> etmpVatReturnGoodsSupplied.vatAmountGBP
      )

      val expectedResult = EtmpVatReturnGoodsSupplied(
        msOfConsumption = etmpVatReturnGoodsSupplied.msOfConsumption,
        vatRateType = etmpVatReturnGoodsSupplied.vatRateType,
        taxableAmountGBP = etmpVatReturnGoodsSupplied.taxableAmountGBP,
        vatAmountGBP = etmpVatReturnGoodsSupplied.vatAmountGBP
      )

      Json.toJson(expectedResult) mustBe json
      json.validate[EtmpVatReturnGoodsSupplied] mustBe JsSuccess(expectedResult)
    }
  }
}

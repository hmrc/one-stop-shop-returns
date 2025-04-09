package models.etmp

import base.SpecBase
import play.api.libs.json.{JsSuccess, Json}

class EtmpVatReturnBalanceOfVatDueSpec extends SpecBase {

  private val etmpVatReturnBalanceOfVatDue: EtmpVatReturnBalanceOfVatDue = arbitraryEtmpVatReturnBalanceOfVatDue.arbitrary.sample.value

  "EtmpVatReturnBalanceOfVatDue" - {

    "must serialise and deserialise from and to an EtmpVatReturnBalanceOfVatDue" in {

      val json = Json.obj(
        "msOfConsumption" -> etmpVatReturnBalanceOfVatDue.msOfConsumption,
        "totalVATDueGBP" -> etmpVatReturnBalanceOfVatDue.totalVATDueGBP
      )

      val expectedResult = EtmpVatReturnBalanceOfVatDue(
        msOfConsumption = etmpVatReturnBalanceOfVatDue.msOfConsumption,
        totalVATDueGBP = etmpVatReturnBalanceOfVatDue.totalVATDueGBP
      )

      Json.toJson(expectedResult) mustBe json
      json.validate[EtmpVatReturnBalanceOfVatDue] mustBe JsSuccess(expectedResult)
    }
  }
}

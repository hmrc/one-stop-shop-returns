package models.etmp

import base.SpecBase
import play.api.libs.json.{JsSuccess, Json}

class EtmpVatReturnSpec extends SpecBase {

  private val etmpVatReturn: EtmpVatReturn = arbitraryEtmpVatReturn.arbitrary.sample.value

  "EtmpVatReturn" - {

    "must serialise and deserialise from and to an EtmpVatReturn" in {

      val json = Json.obj(
        "returnReference" -> etmpVatReturn.returnReference,
        "returnVersion" -> etmpVatReturn.returnVersion,
        "periodKey" -> etmpVatReturn.periodKey,
        "returnPeriodFrom" -> etmpVatReturn.returnPeriodFrom,
        "returnPeriodTo" -> etmpVatReturn.returnPeriodTo,
        "goodsSupplied" -> etmpVatReturn.goodsSupplied,
        "totalVATGoodsSuppliedGBP" -> etmpVatReturn.totalVATGoodsSuppliedGBP,
        "goodsDispatched" -> etmpVatReturn.goodsDispatched,
        "totalVATAmountPayable" -> etmpVatReturn.totalVATAmountPayable,
        "totalVATAmountPayableAllSpplied" -> etmpVatReturn.totalVATAmountPayableAllSpplied,
        "correctionPreviousVATReturn" -> etmpVatReturn.correctionPreviousVATReturn,
        "totalVATAmountFromCorrectionGBP" -> etmpVatReturn.totalVATAmountFromCorrectionGBP,
        "balanceOfVATDueForMS" -> etmpVatReturn.balanceOfVATDueForMS,
        "totalVATAmountDueForAllMSGBP" -> etmpVatReturn.totalVATAmountDueForAllMSGBP,
        "paymentReference" -> etmpVatReturn.paymentReference,
      )

      val expectedResult = EtmpVatReturn(
        returnReference = etmpVatReturn.returnReference,
        returnVersion = etmpVatReturn.returnVersion,
        periodKey = etmpVatReturn.periodKey,
        returnPeriodFrom = etmpVatReturn.returnPeriodFrom,
        returnPeriodTo = etmpVatReturn.returnPeriodTo,
        goodsSupplied = etmpVatReturn.goodsSupplied,
        totalVATGoodsSuppliedGBP = etmpVatReturn.totalVATGoodsSuppliedGBP,
        goodsDispatched = etmpVatReturn.goodsDispatched,
        totalVATAmountPayable = etmpVatReturn.totalVATAmountPayable,
        totalVATAmountPayableAllSpplied = etmpVatReturn.totalVATAmountPayableAllSpplied,
        correctionPreviousVATReturn = etmpVatReturn.correctionPreviousVATReturn,
        totalVATAmountFromCorrectionGBP = etmpVatReturn.totalVATAmountFromCorrectionGBP,
        balanceOfVATDueForMS = etmpVatReturn.balanceOfVATDueForMS,
        totalVATAmountDueForAllMSGBP = etmpVatReturn.totalVATAmountDueForAllMSGBP,
        paymentReference = etmpVatReturn.paymentReference
      )

      Json.toJson(expectedResult) mustBe json
      json.validate[EtmpVatReturn] mustBe JsSuccess(expectedResult)
    }

    "must deserialise to an EtmpVatReturn with empty Sequences" in {

      val json = Json.obj(
        "returnReference" -> etmpVatReturn.returnReference,
        "returnVersion" -> etmpVatReturn.returnVersion,
        "periodKey" -> etmpVatReturn.periodKey,
        "returnPeriodFrom" -> etmpVatReturn.returnPeriodFrom,
        "returnPeriodTo" -> etmpVatReturn.returnPeriodTo,
        "totalVATGoodsSuppliedGBP" -> etmpVatReturn.totalVATGoodsSuppliedGBP,
        "totalVATAmountPayable" -> etmpVatReturn.totalVATAmountPayable,
        "totalVATAmountPayableAllSpplied" -> etmpVatReturn.totalVATAmountPayableAllSpplied,
        "totalVATAmountFromCorrectionGBP" -> etmpVatReturn.totalVATAmountFromCorrectionGBP,
        "totalVATAmountDueForAllMSGBP" -> etmpVatReturn.totalVATAmountDueForAllMSGBP,
        "paymentReference" -> etmpVatReturn.paymentReference
      )

      val expectedResult = EtmpVatReturn(
        returnReference = etmpVatReturn.returnReference,
        returnVersion = etmpVatReturn.returnVersion,
        periodKey = etmpVatReturn.periodKey,
        returnPeriodFrom = etmpVatReturn.returnPeriodFrom,
        returnPeriodTo = etmpVatReturn.returnPeriodTo,
        goodsSupplied = Seq.empty,
        totalVATGoodsSuppliedGBP = etmpVatReturn.totalVATGoodsSuppliedGBP,
        goodsDispatched = Seq.empty,
        totalVATAmountPayable = etmpVatReturn.totalVATAmountPayable,
        totalVATAmountPayableAllSpplied = etmpVatReturn.totalVATAmountPayableAllSpplied,
        correctionPreviousVATReturn = Seq.empty,
        totalVATAmountFromCorrectionGBP = etmpVatReturn.totalVATAmountFromCorrectionGBP,
        balanceOfVATDueForMS = Seq.empty,
        totalVATAmountDueForAllMSGBP = etmpVatReturn.totalVATAmountDueForAllMSGBP,
        paymentReference = etmpVatReturn.paymentReference
      )

      json.validate[EtmpVatReturn] mustBe JsSuccess(expectedResult)
    }
  }
}

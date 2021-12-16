package models.core

import models.corrections.CorrectionPayload
import models.VatReturn
import play.api.libs.json.{Json, OFormat}

import java.time.{Instant, LocalDate}
import java.util.UUID

case class CoreTraderId(vatNumber: String, issuedBy: String)

object CoreTraderId {
  implicit val format: OFormat[CoreTraderId] = Json.format[CoreTraderId]
}

case class CorePeriod(year: Int, quarter: Int)

object CorePeriod {
  implicit val format: OFormat[CorePeriod] = Json.format[CorePeriod]
}

case class CoreSupply(
                       supplyType: String,
                       vatRate: BigDecimal,
                       vatRateType: String,
                       taxableAmountGBP: BigDecimal,
                       vatAmountGBP: BigDecimal
                     )

object CoreSupply {
  implicit val format: OFormat[CoreSupply] = Json.format[CoreSupply]
}

case class CoreEuTraderId(vatIdNumber: String, issuedBy: String)

object CoreEuTraderId {
  implicit val format: OFormat[CoreEuTraderId] = Json.format[CoreEuTraderId]
}

case class CoreMsestSupply(
                            euTraderId: CoreEuTraderId,
                            supplies: List[CoreSupply]
                          )

object CoreMsestSupply {
  implicit val format: OFormat[CoreMsestSupply] = Json.format[CoreMsestSupply]
}


case class CoreCorrection(
                           period: CorePeriod,
                           totalVatAmountCorrectionGBP: BigDecimal
                         )

object CoreCorrection {
  implicit val format: OFormat[CoreCorrection] = Json.format[CoreCorrection]
}

case class CoreMsconSupply(
                            msconCountryCode: String,
                            balanceOfVatDueGBP: BigDecimal,
                            grandTotalMsidGoodsGBP: BigDecimal,
                            grandTotalMsestGoodsGBP: BigDecimal,
                            correctionsTotalGBP: BigDecimal,
                            msidSupplies: List[CoreSupply],
                            msestSupplies: List[CoreMsestSupply],
                            corrections: List[CoreCorrection]
                          )

object CoreMsconSupply {
  implicit val format: OFormat[CoreMsconSupply] = Json.format[CoreMsconSupply]
}

case class CoreVatReturn(
                          vatReturnReferenceNumber: String,
                          version: String,
                          traderId: CoreTraderId,
                          period: CorePeriod,
                          startDate: LocalDate,
                          endDate: LocalDate,
                          submissionDateTime: Instant,
                          totalAmountVatDueGBP: BigDecimal,
                          msconSupplies: List[CoreMsconSupply]
                        )

object CoreVatReturn {
  implicit val format: OFormat[CoreVatReturn] = Json.format[CoreVatReturn]

  def apply(vatReturn: VatReturn, correctionPayload: CorrectionPayload): CoreVatReturn =
    CoreVatReturn(
      vatReturnReferenceNumber = vatReturn.reference.value,
      version = "",
      traderId = CoreTraderId(
        vatNumber = vatReturn.vrn.vrn, issuedBy = ""
      ),
      period = CorePeriod(
        year = vatReturn.period.year,
        quarter = vatReturn.period.quarter.toString.indexOf(1) // TODO
      ),
      startDate = vatReturn.startDate,
      endDate = vatReturn.endDate,
      submissionDateTime = vatReturn.submissionReceived,
      totalAmountVatDueGBP = ???,
      msconSupplies = List.empty // TODO
    )
}

case class CoreErrorResponse(
                              timestamp: Instant,
                              transactionId: UUID,
                              error: String,
                              errorMessage: String
                            )

object CoreErrorResponse {
  implicit val format: OFormat[CoreErrorResponse] = Json.format[CoreErrorResponse]
}
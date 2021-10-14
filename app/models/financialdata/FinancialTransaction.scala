package models.financialdata

import play.api.libs.json.{Format, Json}

import java.time.LocalDate

case class FinancialTransaction(
                               chargeType: Option[String],
                               mainType: Option[String],
                               taxPeriodFrom: Option[LocalDate],
                               taxPeriodTo: Option[LocalDate],
                               originalAmount: Option[BigDecimal],
                               outstandingAmount: Option[BigDecimal],
                               clearedAmount: Option[BigDecimal],
                               items: Option[Seq[Item]]
                               )

object FinancialTransaction {
  implicit val format: Format[FinancialTransaction] = Json.format[FinancialTransaction]
}
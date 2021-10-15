package models.financialdata

import play.api.libs.json.{Format, Json}

import java.time.ZonedDateTime

case class FinancialDataResponse(
                                 idType: Option[String],
                                 idNumber: Option[String],
                                 regimeType: Option[String],
                                 processingDate: ZonedDateTime,
                                 financialTransactions: Option[Seq[FinancialTransaction]]
                                )

object FinancialDataResponse {
  implicit val format: Format[FinancialDataResponse] = Json.format[FinancialDataResponse]
}

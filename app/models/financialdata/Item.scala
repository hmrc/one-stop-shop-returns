package models.financialdata

import play.api.libs.json.{Format, Json}

case class Item(
                 amount: Option[BigDecimal],
                 clearingReason: Option[String],
                 paymentReference: Option[String],
                 paymentAmount: Option[BigDecimal],
                 paymentMethod: Option[String]
               )

object Item {
  implicit val format: Format[Item] = Json.format[Item]
}

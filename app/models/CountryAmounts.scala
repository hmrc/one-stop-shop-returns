package models

case class CountryAmounts(totalVatFromNi: BigDecimal, totalVatFromEu: BigDecimal, totalVatFromCorrection: BigDecimal) {
  val totalVat: BigDecimal = totalVatFromNi + totalVatFromEu + totalVatFromCorrection
}


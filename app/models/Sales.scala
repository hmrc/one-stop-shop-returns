/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package models

import crypto.EncryptedValue
import play.api.libs.json.{Json, OFormat}

case class SalesFromEuCountry(
                               countryOfSale: Country,
                               taxIdentifier: Option[EuTaxIdentifier],
                               sales: List[SalesToCountry]
                             )

object SalesFromEuCountry {

  implicit val format: OFormat[SalesFromEuCountry] = Json.format[SalesFromEuCountry]
}

case class EncryptedSalesFromEuCountry(
                               countryOfSale: EncryptedCountry,
                               taxIdentifier: Option[EncryptedEuTaxIdentifier],
                               sales: List[EncryptedSalesToCountry]
                             )

object EncryptedSalesFromEuCountry {

  implicit val format: OFormat[EncryptedSalesFromEuCountry] = Json.format[EncryptedSalesFromEuCountry]
}

case class SalesToCountry(
                           countryOfConsumption: Country,
                           amounts: List[SalesDetails]
                         )

object SalesToCountry {

  implicit val format: OFormat[SalesToCountry] = Json.format[SalesToCountry]
}

case class EncryptedSalesToCountry(
                           countryOfConsumption: EncryptedCountry,
                           amounts: List[EncryptedSalesDetails]
                         )

object EncryptedSalesToCountry {

  implicit val format: OFormat[EncryptedSalesToCountry] = Json.format[EncryptedSalesToCountry]
}

case class SalesDetails(
                         vatRate: VatRate,
                         netValueOfSales: BigDecimal,
                         vatOnSales: BigDecimal
                       )

object SalesDetails {
  implicit val format: OFormat[SalesDetails] = Json.format[SalesDetails]
}

case class EncryptedSalesDetails(
                         vatRate: EncryptedVatRate,
                         netValueOfSales: EncryptedValue,
                         vatOnSales: EncryptedValue
                       )

object EncryptedSalesDetails {
  implicit val format: OFormat[EncryptedSalesDetails] = Json.format[EncryptedSalesDetails]
}

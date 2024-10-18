/*
 * Copyright 2024 HM Revenue & Customs
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

import play.api.libs.json.{Json, OFormat}

case class Country(code: String, name: String)

object Country {

  implicit val format: OFormat[Country] = Json.format[Country]
}

case class CountryWithValidationDetails(country: Country, vrnRegex: String, messageInput: String, exampleVrn: String, additionalMessage: Option[String] = None)

object CountryWithValidationDetails {

  lazy val euCountriesWithVRNValidationRules: Seq[CountryWithValidationDetails] = Seq(
    CountryWithValidationDetails(Country("AT", "Austria"),austriaVatNumberRegex, "the 9 characters", "U12345678"),
    CountryWithValidationDetails(Country("BE", "Belgium"), belgiumVatNumberRegex, "the 10 numbers", "0123456789"),
    CountryWithValidationDetails(Country("BG", "Bulgaria"), bulgariaVatNumberRegex, "9 or 10 numbers", "123456789"),
    CountryWithValidationDetails(Country("HR", "Croatia"), croatiaVatNumberRegex, "the 11 numbers", "01234567899"),
    CountryWithValidationDetails(Country("CY", "Republic of Cyprus"), cyprusVatNumberRegex, "the 9 characters", "12345678X"),
    CountryWithValidationDetails(Country("CZ", "Czech Republic"), czechRepublicVatNumberRegex, "8, 9 or 10 numbers", "123456789"),
    CountryWithValidationDetails(Country("DK", "Denmark"), denmarkVatNumberRegex, "the 8 numbers", "12345678", Some(". Do not include spaces.")),
    CountryWithValidationDetails(Country("EE", "Estonia"), estoniaVatNumberRegex, "the 9 numbers", "123456789"),
    CountryWithValidationDetails(Country("FI", "Finland"), finlandVatNumberRegex, "the 8 numbers", "12345678"),
    CountryWithValidationDetails(Country("FR", "France"), franceVatNumberRegex, "the 11 characters", "XX123456789", Some(". Do not include spaces.")),
    CountryWithValidationDetails(Country("DE", "Germany"), germanyVatNumberRegex, "the 9 numbers", "123456789"),
    CountryWithValidationDetails(Country("EL", "Greece"), greeceVatNumberRegex, "the 9 numbers", "123456789"),
    CountryWithValidationDetails(Country("HU", "Hungary"), hungaryVatNumberRegex, "the 8 numbers", "12345678"),
    CountryWithValidationDetails(Country("IE", "Ireland"), irelandVatNumberRegex, "8 or 9 characters", "1234567WI"),
    CountryWithValidationDetails(Country("IT", "Italy"), italyVatNumberRegex, "the 11 numbers", "01234567899"),
    CountryWithValidationDetails(Country("LV", "Latvia"), latviaVatNumberRegex, "the 11 numbers", "01234567899"),
    CountryWithValidationDetails(Country("LT", "Lithuania"), lithuaniaVatNumberRegex, "9 or 12 numbers", "123456789"),
    CountryWithValidationDetails(Country("LU", "Luxembourg"), luxembourgVatNumberRegex, "the 8 numbers", "12345678"),
    CountryWithValidationDetails(Country("MT", "Malta"), maltaVatNumberRegex, "the 8 numbers", "12345678"),
    CountryWithValidationDetails(Country("NL", "Netherlands"), netherlandsVatNumberRegex, "the 12 characters", "0123456789AB"),
    CountryWithValidationDetails(Country("PL", "Poland"), polandVatNumberRegex, "the 10 numbers", "1234567890"),
    CountryWithValidationDetails(Country("PT", "Portugal"), portugalVatNumberRegex, "the 9 numbers", "123456789"),
    CountryWithValidationDetails(Country("RO", "Romania"), romaniaVatNumberRegex, "between 2 and 10 numbers", "1234567890"),
    CountryWithValidationDetails(Country("SK", "Slovakia"), slovakiaVatNumberRegex, "the 10 numbers", "1234567890"),
    CountryWithValidationDetails(Country("SI", "Slovenia"), sloveniaVatNumberRegex, "the 8 numbers", "12345678"),
    CountryWithValidationDetails(Country("ES", "Spain"), spainVatNumberRegex, "the 9 characters", "X1234567X"),
    CountryWithValidationDetails(Country("SE", "Sweden"), swedenVatNumberRegex, "the 12 numbers", "012345678987")
  )

  private val austriaVatNumberRegex = """^ATU[0-9]{8}$"""
  private val belgiumVatNumberRegex = """^BE(0|1)[0-9]{9}$"""
  private val bulgariaVatNumberRegex = """^BG[0-9]{9,10}$"""
  private val cyprusVatNumberRegex = """^CY[0-9]{8}[A-Z]$"""
  private val czechRepublicVatNumberRegex = """^CZ[0-9]{8,10}$"""
  private val germanyVatNumberRegex = """^DE[0-9]{9}$"""
  private val denmarkVatNumberRegex = """^DK[0-9]{8}$"""
  private val estoniaVatNumberRegex = """^EE[0-9]{9}$"""
  private val greeceVatNumberRegex = """^EL[0-9]{9}$"""
  private val spainVatNumberRegex = """^ES[A-Z][0-9]{8}$|^ES[0-9]{8}[A-Z]$|^ES[A-Z][0-9]{7}[A-Z]$"""
  private val finlandVatNumberRegex = """^FI[0-9]{8}$"""
  private val franceVatNumberRegex = """^FR[A-Z0-9]{2}[0-9]{9}$"""
  private val croatiaVatNumberRegex = """^HR[0-9]{11}$"""
  private val hungaryVatNumberRegex = """^HU[0-9]{8}$"""
  private val irelandVatNumberRegex = """^IE[0-9][A-Z0-9\+\*][0-9]{5}[A-Z]$|^IE[0-9]{7}WI$"""
  private val italyVatNumberRegex = """^IT[0-9]{11}$"""
  private val lithuaniaVatNumberRegex = """^LT[0-9]{9}$|^LT[0-9]{12}$"""
  private val luxembourgVatNumberRegex = """^LU[0-9]{8}$"""
  private val latviaVatNumberRegex = """^LV[0-9]{11}$"""
  private val maltaVatNumberRegex = """^MT[0-9]{8}$"""
  private val netherlandsVatNumberRegex = """^NL[A-Z0-9\+\*]{12}$"""
  private val polandVatNumberRegex = """^PL[0-9]{10}$"""
  private val portugalVatNumberRegex = """^PT[0-9]{9}$"""
  private val romaniaVatNumberRegex = """^RO[0-9]{2,10}$"""
  private val swedenVatNumberRegex = """^SE[0-9]{12}$"""
  private val sloveniaVatNumberRegex = """^SI[0-9]{8}$"""
  private val slovakiaVatNumberRegex = """^SK[0-9]{10}$"""
}

case class EncryptedCountry(code: String, name: String)

object EncryptedCountry {

  implicit val format: OFormat[EncryptedCountry] = Json.format[EncryptedCountry]
}


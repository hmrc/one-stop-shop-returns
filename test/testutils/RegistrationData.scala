/*
 * Copyright 2022 HM Revenue & Customs
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

package testutils

import generators.Generators
import models.domain._
import models.Country
import org.scalatest.EitherValues
import uk.gov.hmrc.domain.Vrn

import java.time.LocalDate

object RegistrationData extends Generators with EitherValues {

  val iban: Iban = Iban("GB33BUKB20201555555555").value
  val bic: Bic = Bic("ABCDGB2A").get

  val registration: Registration =
    Registration(
      vrn = Vrn("123456789"),
      registeredCompanyName = "foo",
      tradingNames = List("single", "double"),
      vatDetails = VatDetails(
        registrationDate = LocalDate.now,
        address          = createUkAddress(),
        partOfVatGroup   = true,
        source           = VatDetailSource.Etmp
      ),
      euRegistrations = Seq(
        EuVatRegistration(Country("FR", "France"), "FR123456789"),
        RegistrationWithFixedEstablishment(
          Country("ES", "Spain"),
          EuTaxIdentifier(EuTaxIdentifierType.Vat, "ES123456789"),
          FixedEstablishment("Spanish trading name", InternationalAddress("Line 1", None, "Town", None, None, Country("ES", "Spain")))
        ),
        RegistrationWithFixedEstablishment(
          Country("DE", "Germany"),
          EuTaxIdentifier(EuTaxIdentifierType.Other, "DE123456789"),
          FixedEstablishment("German trading name", InternationalAddress("Line 1", None, "Town", None, None, Country("DE", "Germany")))
        ),
        RegistrationWithFixedEstablishment(
          Country("BE", "Belgium"),
          EuTaxIdentifier(EuTaxIdentifierType.Vat, "BE123456789"),
          FixedEstablishment("Belgium trading name", InternationalAddress("Line 1", None, "Town", None, None, Country("BE", "Belgium")))
        ),
        RegistrationWithFixedEstablishment(
          Country("PL", "Poland"),
          EuTaxIdentifier(EuTaxIdentifierType.Other, "PL123456789"),
          FixedEstablishment("Polish trading name", InternationalAddress("Line 1", None, "Town", None, None, Country("PL", "Poland")))
        ),
        RegistrationWithoutFixedEstablishment(
          Country("IT", "Italy"),
          EuTaxIdentifier(EuTaxIdentifierType.Vat, "IT123456789")
        ),
        RegistrationWithoutFixedEstablishment(
          Country("NL", "Netherlands"),
          EuTaxIdentifier(EuTaxIdentifierType.Other, "NL123456789")
        ),
        RegistrationWithoutTaxId(
          Country("IE", "Ireland")
        )
      ),
      contactDetails = createBusinessContactDetails(),
      websites = Seq("website1", "website2"),
      commencementDate = LocalDate.now(),
      previousRegistrations = Seq(
        PreviousRegistration(Country("DE", "Germany"), "DE123")
      ),
      bankDetails = BankDetails("Account name", Some(bic), iban),
      isOnlineMarketplace = false,
      niPresence = Some(PrincipalPlaceOfBusinessInNi),
      dateOfFirstSale = Some(LocalDate.now())
    )

  private def createUkAddress(): UkAddress =
    UkAddress(
      "123 Street",
      Some("Street"),
      "City",
      Some("county"),
      "AA12 1AB"
    )

  private def createBusinessContactDetails(): BusinessContactDetails =
    BusinessContactDetails(
      "Joe Bloggs",
      "01112223344",
      "email@email.com"
    )
}


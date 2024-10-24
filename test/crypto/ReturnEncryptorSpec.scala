package crypto

import base.SpecBase
import generators.Generators
import models.{SalesDetails, _}
import org.scalacheck.Arbitrary.arbitrary
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import java.time.{Instant, LocalDate}

class ReturnEncryptorSpec extends SpecBase with ScalaCheckPropertyChecks with Generators {

  private val cipher    = new AesGCMCrypto
  private val countryEncyrpter = new CountryEncryptor(cipher)
  private val encryptor = new ReturnEncryptor(countryEncyrpter, cipher)
  private val secretKey = "VqmXp7yigDFxbCUdDdNZVIvbW6RgPNJsliv6swQNCL8="

  "encrypt / decrypt SalesDetails" - {

    "must encrypt salesDetails and decrypt it" in {
      forAll(arbitrary[SalesDetails]) {
        salesDetails =>
          val e = encryptor.encryptSalesDetails(salesDetails, vrn, secretKey)
          val d = encryptor.decryptSalesDetails(e, vrn, secretKey)

          d mustEqual salesDetails
      }
    }
  }

  "encrypt / decrypt VatOnSales" - {

    "must encrypt vatOnSales and decrypt it" in {
      forAll(arbitrary[VatOnSales]) {
        vatOnSales =>
          val e = encryptor.encryptVatOnSales(vatOnSales, vrn, secretKey)
          val d = encryptor.decryptVatOnSales(e, vrn, secretKey)

          d mustEqual vatOnSales
      }
    }
  }

  "encrypt / decrypt VatRate" - {

    "must encrypt vatRate and decrypt it" in {
      forAll(arbitrary[VatRate]) {
        vatRate =>
          val e = encryptor.encryptVatRate(vatRate, vrn, secretKey)
          val d = encryptor.decryptVatRate(e, vrn, secretKey)

          d mustEqual vatRate
      }
    }
  }

  "encrypt / decrypt SalesToCountry" - {

    "must encrypt a SalesToCountry and decrypt them" in {
      forAll(arbitrary[SalesToCountry]) {
        salesToCountry =>
          val e = encryptor.encryptSalesToCountry(salesToCountry, vrn, secretKey)
          val d = encryptor.decryptSalesToCountry(e, vrn, secretKey)

          d mustEqual salesToCountry
      }
    }
  }

  "encrypt / decrypt EuTaxIdentifier" - {

    "must encrypt a EuTaxIdentifier and decrypt them" in {
      forAll(arbitrary[EuTaxIdentifier]) {
        euTaxIdentifier =>
          val e = encryptor.encryptEuTaxIdentifier(euTaxIdentifier, vrn, secretKey)
          val d = encryptor.decryptEuTaxIdentifier(e, vrn, secretKey)

          d mustEqual euTaxIdentifier
      }
    }
  }

  "encrypt / decrypt SalesFromEuCountry" - {

    "must encrypt a SalesFromEuCountry and decrypt them" in {
      forAll(arbitrary[SalesFromEuCountry]) {
        salesFromEuCountry =>
          val e = encryptor.encryptSalesFromEuCountry(salesFromEuCountry, vrn, secretKey)
          val d = encryptor.decryptSalesFromEuCountry(e, vrn, secretKey)

          d mustEqual salesFromEuCountry
      }
    }
  }

  "encrypt / decrypt return" - {

    "must encrypt a return with all options missing and decrypt it" in {
      val period = arbitrary[Period].sample.value
      val returnReference = ReturnReference(vrn, period)
      val paymentReference = PaymentReference(vrn, period)

      val vatReturn: VatReturn = VatReturn(
        vrn = vrn,
        period = period,
        reference = returnReference,
        paymentReference = paymentReference,
        startDate = None,
        endDate = None,
        salesFromNi = List.empty,
        salesFromEu = List.empty,
        submissionReceived = Instant.now(stubClock),
        lastUpdated = Instant.now(stubClock)
      )

      val e = encryptor.encryptReturn(vatReturn, vrn, secretKey)
      val d = encryptor.decryptReturn(e, vrn, secretKey)

      d mustEqual vatReturn
    }

    "must encrypt a return with all options present and decrypt it" in {

      val period = arbitrary[Period].sample.value
      val returnReference = ReturnReference(vrn, period)
      val paymentReference = PaymentReference(vrn, period)

      val belgium = Country("BE", "Belgium")
      val vatRate = VatRate(20, VatRateType.Standard)
      val salesDetails = SalesDetails(vatRate, 100, VatOnSales(VatOnSalesChoice.Standard, 20))
      val salesToCountry = SalesToCountry(belgium, List(salesDetails))

      val salesFromEuCountry = SalesFromEuCountry(
        belgium,
        Some(EuTaxIdentifier(EuTaxIdentifierType.Vat, "QWERTY")),
        List(salesToCountry)
      )

      val vatReturn: VatReturn = VatReturn(
        vrn = vrn,
        period = period,
        reference = returnReference,
        paymentReference = paymentReference,
        startDate = Some(LocalDate.now()),
        endDate = Some(LocalDate.now()),
        salesFromNi = List(salesToCountry),
        salesFromEu = List(salesFromEuCountry),
        submissionReceived = Instant.now(stubClock),
        lastUpdated = Instant.now(stubClock)
      )

      val e = encryptor.encryptReturn(vatReturn, vrn, secretKey)
      val d = encryptor.decryptReturn(e, vrn, secretKey)

      d mustEqual vatReturn
    }
  }
}

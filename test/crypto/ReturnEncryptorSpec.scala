package crypto

import base.SpecBase
import com.typesafe.config.Config
import config.AppConfig
import generators.Generators
import models.{SalesDetails, _}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.Configuration
import services.crypto.EncryptionService

import java.time.{Instant, LocalDate}

class ReturnEncryptorSpec extends SpecBase with ScalaCheckPropertyChecks with Generators {

  private val mockAppConfig: AppConfig = mock[AppConfig]
  private val mockConfiguration = mock[Configuration]
  private val mockConfig = mock[Config]
  private val mockSecureGCMCipher: AesGCMCrypto = mock[AesGCMCrypto]
  private val mockEncryptionService: EncryptionService = new EncryptionService(mockConfiguration)
  private val countryEncyrpter = new CountryEncryptor(mockEncryptionService)
  private val encryptor = new ReturnEncryptor(mockAppConfig, countryEncyrpter, mockSecureGCMCipher, mockEncryptionService)
  private val secretKey = "VqmXp7yigDFxbCUdDdNZVIvbW6RgPNJsliv6swQNCL8="

  when(mockConfiguration.underlying) thenReturn mockConfig
  when(mockConfig.getString(any())) thenReturn secretKey

  "encrypt / decrypt SalesDetails" - {

    "must encrypt salesDetails and decrypt it" in {
      forAll(arbitrary[SalesDetails]) {
        salesDetails =>
          val e = encryptor.encryptSalesDetails(salesDetails)
          val d = encryptor.decryptSalesDetails(e)

          d mustEqual salesDetails
      }
    }
  }

  "encrypt / decrypt VatOnSales" - {

    "must encrypt vatOnSales and decrypt it" in {
      forAll(arbitrary[VatOnSales]) {
        vatOnSales =>
          val e = encryptor.encryptVatOnSales(vatOnSales)
          val d = encryptor.decryptVatOnSales(e)

          d mustEqual vatOnSales
      }
    }
  }

  "encrypt / decrypt VatRate" - {

    "must encrypt vatRate and decrypt it" in {
      forAll(arbitrary[VatRate]) {
        vatRate =>
          val e = encryptor.encryptVatRate(vatRate)
          val d = encryptor.decryptVatRate(e)

          d mustEqual vatRate
      }
    }
  }

  "encrypt / decrypt SalesToCountry" - {

    "must encrypt a SalesToCountry and decrypt them" in {
      forAll(arbitrary[SalesToCountry]) {
        salesToCountry =>
          val e = encryptor.encryptSalesToCountry(salesToCountry)
          val d = encryptor.decryptSalesToCountry(e)

          d mustEqual salesToCountry
      }
    }
  }

  "encrypt / decrypt EuTaxIdentifier" - {

    "must encrypt a EuTaxIdentifier and decrypt them" in {
      forAll(arbitrary[EuTaxIdentifier]) {
        euTaxIdentifier =>
          val e = encryptor.encryptEuTaxIdentifier(euTaxIdentifier)
          val d = encryptor.decryptEuTaxIdentifier(e)

          d mustEqual euTaxIdentifier
      }
    }
  }

  "encrypt / decrypt SalesFromEuCountry" - {

    "must encrypt a SalesFromEuCountry and decrypt them" in {
      forAll(arbitrary[SalesFromEuCountry]) {
        salesFromEuCountry =>
          val e = encryptor.encryptSalesFromEuCountry(salesFromEuCountry)
          val d = encryptor.decryptSalesFromEuCountry(e)

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

      val e = encryptor.encryptReturn(vatReturn, vrn)
      val d = encryptor.decryptReturn(e, vrn)

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

      val e = encryptor.encryptReturn(vatReturn, vrn)
      val d = encryptor.decryptReturn(e, vrn)

      d mustEqual vatReturn
    }
  }
}

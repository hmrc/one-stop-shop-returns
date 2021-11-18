package crypto

import base.SpecBase
import generators.Generators
import models._
import models.corrections.{CorrectionPayload, CorrectionToCountry, PeriodWithCorrections}
import org.scalacheck.Arbitrary.arbitrary
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import java.time.Instant

class CorrectionEncryptorSpec extends SpecBase with ScalaCheckPropertyChecks with Generators {

  private val cipher = new SecureGCMCipher
  private val countryEncyrpter = new CountryEncryptor(cipher)
  private val encryptor = new CorrectionEncryptor(countryEncyrpter, cipher)
  private val secretKey = "VqmXp7yigDFxbCUdDdNZVIvbW6RgPNJsliv6swQNCL8="

  "encrypt / decrypt CorrectionToCountry" - {

    "must encrypt a CorrectionToCountry and decrypt them" in {
      forAll(arbitrary[CorrectionToCountry]) {
        correctionToCountry =>
          val e = encryptor.encryptCorrectionToCountry(correctionToCountry, vrn, secretKey)
          val d = encryptor.decryptCorrectionToCountry(e, vrn, secretKey)

          d mustEqual correctionToCountry
      }
    }
  }

  "encrypt / decrypt PeriodWithCorrections" - {

    "must encrypt a PeriodWithCorrections and decrypt them" in {
      forAll(arbitrary[PeriodWithCorrections]) {
        periodWithCorrections =>
          val e = encryptor.encryptPeriodWithCorrections(periodWithCorrections, vrn, secretKey)
          val d = encryptor.decryptPeriodWithCorrections(e, vrn, secretKey)

          d mustEqual periodWithCorrections
      }
    }
  }

  "encrypt / decrypt Correction payload" - {

    "must encrypt a correction payload with all options missing and decrypt it" in {
      val period = arbitrary[Period].sample.value

      val correctionPayload: CorrectionPayload = CorrectionPayload(
        vrn = vrn,
        period = period,
        corrections = List.empty,
        submissionReceived = Instant.now(stubClock),
        lastUpdated = Instant.now(stubClock)
      )

      val e = encryptor.encryptCorrectionPayload(correctionPayload, vrn, secretKey)
      val d = encryptor.decryptCorrectionPayload(e, vrn, secretKey)

      d mustEqual correctionPayload
    }

    "must encrypt a return with all options present and decrypt it" in {

      val period = arbitrary[Period].sample.value
      val correctionPeriod = arbitrary[Period].sample.value

      val belgium = Country("BE", "Belgium")
      val correctionsToCountry = CorrectionToCountry(belgium, BigDecimal(100))

      val periodWithCorrections = PeriodWithCorrections(
        correctionReturnPeriod = correctionPeriod,
        correctionsToCountry = List(correctionsToCountry)
      )

      val vatReturn: CorrectionPayload = CorrectionPayload(
        vrn = vrn,
        period = period,
        corrections = List(periodWithCorrections),
        submissionReceived = Instant.now(stubClock),
        lastUpdated = Instant.now(stubClock)
      )

      val e = encryptor.encryptCorrectionPayload(vatReturn, vrn, secretKey)
      val d = encryptor.decryptCorrectionPayload(e, vrn, secretKey)

      d mustEqual vatReturn
    }
  }
}

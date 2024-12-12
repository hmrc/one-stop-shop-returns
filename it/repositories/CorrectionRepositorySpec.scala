package repositories

import config.AppConfig
import crypto.{AesGCMCrypto, CorrectionEncryptor, CountryEncryptor}
import generators.Generators
import models.corrections.{CorrectionPayload, CorrectionToCountry, EncryptedCorrectionPayload, PeriodWithCorrections}
import models.{Country, Period, StandardPeriod}
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar.mock
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, PlayMongoRepositorySupport}
import utils.StringUtils

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class CorrectionRepositorySpec
  extends AnyFreeSpec
    with Matchers
    with PlayMongoRepositorySupport[EncryptedCorrectionPayload]
    with CleanMongoCollectionSupport
    with ScalaFutures
    with IntegrationPatience
    with OptionValues
    with Generators {

  private val cipher = new AesGCMCrypto
  private val countryEncryptor = new CountryEncryptor(cipher)
  private val correctionEncryptor = new CorrectionEncryptor(countryEncryptor, cipher)
  private val secretKey = "VqmXp7yigDFxbCUdDdNZVIvbW6RgPNJsliv6swQNCL8="
  private val appConfig = mock[AppConfig]

  when(appConfig.encryptionKey) `thenReturn` secretKey

  override protected val repository: CorrectionRepository =
    new CorrectionRepository(
      mongoComponent = mongoComponent,
      appConfig = appConfig,
      correctionEncryptor = correctionEncryptor
    )

  ".get many" - {
    "must return all records for the given VRN" in {

      val correctionPayload1 = arbitrary[CorrectionPayload].sample.value
      val correctionPayload2Period = correctionPayload1.period.asInstanceOf[StandardPeriod] copy (year = correctionPayload1.period.year + 1)
      val correctionPayload2 = correctionPayload1.copy(
        period = correctionPayload2Period
      )
      val vrn3 = Vrn(StringUtils.rotateDigitsInString(correctionPayload1.vrn.vrn).mkString)
      val correctionPayload3 = correctionPayload1.copy(
        vrn = vrn3
      )

      insert(correctionEncryptor.encryptCorrectionPayload(correctionPayload1, correctionPayload1.vrn, secretKey)).futureValue
      insert(correctionEncryptor.encryptCorrectionPayload(correctionPayload2, correctionPayload2.vrn, secretKey)).futureValue
      insert(correctionEncryptor.encryptCorrectionPayload(correctionPayload3, correctionPayload3.vrn, secretKey)).futureValue

      val returns = repository.get(correctionPayload1.vrn).futureValue

      returns must contain theSameElementsAs Seq(correctionPayload1, correctionPayload2)
    }

    "must return empty for the given VRN" in {

      val vrn = arbitrary[Vrn].sample.value

      val returns = repository.get(vrn).futureValue

      returns must contain theSameElementsAs Seq.empty
    }
  }

  ".get by periods" - {
    "must return all records for the given periods" in {

      val correctionPayload1 = arbitrary[CorrectionPayload].sample.value
      val correctionPayload2Period = correctionPayload1.period.asInstanceOf[StandardPeriod] copy (year = correctionPayload1.period.year + 1)
      val correctionPayload3Period = correctionPayload1.period.asInstanceOf[StandardPeriod] copy (year = correctionPayload1.period.year + 2)
      val correctionPayload2 = correctionPayload1.copy(
        period = correctionPayload2Period
      )
      val vrn3 = Vrn(StringUtils.rotateDigitsInString(correctionPayload1.vrn.vrn).mkString)
      val correctionPayload3 = correctionPayload1.copy(
        vrn = vrn3,
        period = correctionPayload3Period
      )

      insert(correctionEncryptor.encryptCorrectionPayload(correctionPayload1, correctionPayload1.vrn, secretKey)).futureValue
      insert(correctionEncryptor.encryptCorrectionPayload(correctionPayload2, correctionPayload2.vrn, secretKey)).futureValue
      insert(correctionEncryptor.encryptCorrectionPayload(correctionPayload3, correctionPayload3.vrn, secretKey)).futureValue

      val returns = repository.getByPeriods(Seq(correctionPayload1.period, correctionPayload2Period)).futureValue

      returns must contain theSameElementsAs Seq(correctionPayload1, correctionPayload2)
    }

    "must return empty for the given periods" in {

      val period1 = arbitraryPeriod.arbitrary.sample.value
      val period2 = arbitraryPeriod.arbitrary.retryUntil(_ != period1).sample.value

      val returns = repository.getByPeriods(Seq(period1, period2)).futureValue

      returns must contain theSameElementsAs Seq.empty
    }
  }

  ".get all" - {
    "must return all records" in {

      val correctionPayload1 = arbitrary[CorrectionPayload].sample.value
      val correctionPayload2Period = correctionPayload1.period.asInstanceOf[StandardPeriod] copy (year = correctionPayload1.period.year + 1)
      val correctionPayload2 = correctionPayload1.copy(
        period = correctionPayload2Period
      )
      val vrn3 = Vrn(StringUtils.rotateDigitsInString(correctionPayload1.vrn.vrn).mkString)
      val correctionPayload3 = correctionPayload1.copy(
        vrn = vrn3
      )

      insert(correctionEncryptor.encryptCorrectionPayload(correctionPayload1, correctionPayload1.vrn, secretKey)).futureValue
      insert(correctionEncryptor.encryptCorrectionPayload(correctionPayload2, correctionPayload2.vrn, secretKey)).futureValue
      insert(correctionEncryptor.encryptCorrectionPayload(correctionPayload3, correctionPayload3.vrn, secretKey)).futureValue

      val returns = repository.get().futureValue

      returns must contain theSameElementsAs Seq(correctionPayload1, correctionPayload2, correctionPayload3)
    }
  }

  ".get one" - {

    "must return a VAT return when one exists for this VRN and period" in {

      val correctionPayload = arbitrary[CorrectionPayload].sample.value

      insert(correctionEncryptor.encryptCorrectionPayload(correctionPayload, correctionPayload.vrn, secretKey)).futureValue

      val result = repository.get(correctionPayload.vrn, correctionPayload.period).futureValue

      result.value mustEqual correctionPayload
    }

    "must return none when nothing exists for this VRN and period" in {

      val vrn = arbitrary[Vrn].sample.value
      val period = arbitrary[Period].sample.value

      val result = repository.get(vrn, period).futureValue

      result mustBe None
    }
  }

  ".get corrections for period" - {

    "must return a sequence of correction payloads when one exists for this VRN and period" in {

      val correctionPayload = CorrectionPayload(
        vrn = arbitraryVrn.arbitrary.sample.value,
        period = arbitraryPeriod.arbitrary.sample.value,
        corrections = List(
          PeriodWithCorrections(correctionReturnPeriod = arbitraryPeriod.arbitrary.sample.value,
            correctionsToCountry = List(CorrectionToCountry(correctionCountry = Country("DE", "Germany"), countryVatCorrection = BigDecimal(10))))
        ),
        submissionReceived = Instant.now(),
        lastUpdated = Instant.now()
      )

      insert(correctionEncryptor.encryptCorrectionPayload(correctionPayload, correctionPayload.vrn, secretKey)).futureValue

      val result = repository.getByCorrectionPeriod(correctionPayload.vrn, correctionPayload.corrections.head.correctionReturnPeriod).futureValue

      result.nonEmpty mustBe(true)

      result mustEqual List(correctionPayload)
    }

    "must return none when nothing exists for this VRN and period" in {

      val vrn = arbitrary[Vrn].sample.value
      val period = arbitrary[Period].sample.value

      val result = repository.getByCorrectionPeriod(vrn, period).futureValue

      result mustBe List.empty
    }
  }


}

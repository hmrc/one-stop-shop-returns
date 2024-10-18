package repositories

import com.typesafe.config.Config
import config.AppConfig
import crypto.{AesGCMCrypto, CorrectionEncryptor, CountryEncryptor, ReturnEncryptor}
import generators.Generators
import models.{EncryptedVatReturn, LegacyEncryptedVatReturn, NewEncryptedVatReturn, Period, ReturnReference, StandardPeriod, VatReturn}
import models.corrections.CorrectionPayload
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.Configuration
import services.crypto.EncryptionService
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, PlayMongoRepositorySupport}
import utils.StringUtils

import scala.concurrent.ExecutionContext.Implicits.global

class VatReturnRepositorySpec
  extends AnyFreeSpec
    with Matchers
    with PlayMongoRepositorySupport[EncryptedVatReturn]
    with CleanMongoCollectionSupport
    with ScalaFutures
    with IntegrationPatience
    with OptionValues
    with Generators {

  private val mockAppConfig = mock[AppConfig]
  private val mockConfiguration = mock[Configuration]
  private val mockConfig = mock[Config]
  private val mockSecureGCMCipher: AesGCMCrypto = mock[AesGCMCrypto]
  private val mockEncryptionService: EncryptionService = new EncryptionService(mockConfiguration)
  private val countryEncryptor = new CountryEncryptor(mockEncryptionService)
  private val encryptor = new ReturnEncryptor(mockAppConfig ,countryEncryptor, mockSecureGCMCipher, mockEncryptionService)
  private val correctionEncryptor = new CorrectionEncryptor(countryEncryptor, mockEncryptionService)
  private val secretKey = "VqmXp7yigDFxbCUdDdNZVIvbW6RgPNJsliv6swQNCL8="

  when(mockConfiguration.underlying) thenReturn mockConfig
  when(mockConfig.getString(any())) thenReturn secretKey
  when(mockAppConfig.encryptionKey) thenReturn secretKey

  val correctionRepository = new CorrectionRepository(mongoComponent, correctionEncryptor)

  override protected val repository =
    new VatReturnRepository(
      mongoComponent = mongoComponent,
      returnEncryptor = encryptor,
      correctionEncryptor = correctionEncryptor,
      correctionRepository
    )

  ".insert vatReturn" - {

    "must insert returns for the same VRN but different periods" in {

      val vatReturn1    = arbitrary[VatReturn].sample.value
      val return2Period = vatReturn1.period.asInstanceOf[StandardPeriod] copy (year = vatReturn1.period.year + 1)
      val vatReturn2    = vatReturn1.copy (
        period    = return2Period,
        reference = ReturnReference(vatReturn1.vrn, return2Period)
      )

      val insertResult1 = repository.insert(vatReturn1).futureValue
      val insertReturn2 = repository.insert(vatReturn2).futureValue
      val databaseRecords = findAll().futureValue
      val decryptedDatabaseRecords =
        databaseRecords.map(e => determineEncryptionType(e))

      insertResult1 mustBe Some(vatReturn1)
      insertReturn2 mustBe Some(vatReturn2)
      decryptedDatabaseRecords must contain theSameElementsAs Seq(vatReturn1, vatReturn2)
    }

    "must insert returns for different VRNs in the same period" in {

      val vatReturn1 = arbitrary[VatReturn].sample.value
      val vrn2       = Vrn(StringUtils.rotateDigitsInString(vatReturn1.vrn.vrn).mkString)
      val vatReturn2 = vatReturn1.copy (
        vrn       = vrn2,
        reference = ReturnReference(vrn2, vatReturn1.period)
      )

      val insertResult1 = repository.insert(vatReturn1).futureValue
      val insertReturn2 = repository.insert(vatReturn2).futureValue
      val databaseRecords = findAll().futureValue
      val decryptedDatabaseRecords =
        databaseRecords.map(e => determineEncryptionType(e))

      insertResult1 mustBe Some(vatReturn1)
      insertReturn2 mustBe Some(vatReturn2)
      decryptedDatabaseRecords must contain theSameElementsAs Seq(vatReturn1, vatReturn2)
    }

    "must not insert a return with the same VRN and period" in {

      val vatReturn = arbitrary[VatReturn].sample.value

      val insertResult1 = repository.insert(vatReturn).futureValue
      val insertResult2 = repository.insert(vatReturn).futureValue

      insertResult1 mustBe Some(vatReturn)
      insertResult2 mustBe None

      val decryptedDatabaseRecords =
        findAll().futureValue.map(e => determineEncryptionType(e))

      decryptedDatabaseRecords must contain only vatReturn
    }
  }

  ".insert vatreturn and correction" - {

    "must insert returns and corrections for the same VRN but different periods" in {

      val vatReturn1    = arbitrary[VatReturn].sample.value
      val correction1    = arbitrary[CorrectionPayload].sample.value.copy(period = vatReturn1.period)
      val return2Period = vatReturn1.period.asInstanceOf[StandardPeriod] copy (year = vatReturn1.period.year + 1)
      val correction2Period = correction1.period.asInstanceOf[StandardPeriod] copy (year = correction1.period.year + 1)
      val vatReturn2    = vatReturn1.copy (
        period    = return2Period,
        reference = ReturnReference(vatReturn1.vrn, return2Period)
      )
      val correction2 = correction1.copy(
        period = correction2Period
      )

      val insertResult1 = repository.insert(vatReturn1, correction1).futureValue
      val insertReturn2 = repository.insert(vatReturn2, correction2).futureValue
      val databaseRecords = findAll().futureValue
      val decryptedDatabaseRecords =
        databaseRecords.map(e => determineEncryptionType(e))

      /* TODO some form of this should work for this test
      val insertedCorrection1 = correctionRepository.get(correction1.vrn)
      val insertedCorrection2 = correctionRepository.get(correction2.vrn)

      insertedCorrection1 mustBe Some(correction1)
      insertedCorrection2 mustBe Some(correction2)
      */

      insertResult1 mustBe Some((vatReturn1, correction1))
      insertReturn2 mustBe Some((vatReturn2, correction2))

      decryptedDatabaseRecords must contain theSameElementsAs Seq(vatReturn1, vatReturn2)
    }

    "must insert returns and corrections for different VRNs in the same period" in {

      val vatReturn1 = arbitrary[VatReturn].sample.value
      val correction1 = arbitrary[CorrectionPayload].sample.value.copy(period = vatReturn1.period)
      val vrn2       = Vrn(StringUtils.rotateDigitsInString(vatReturn1.vrn.vrn).mkString)
      val vatReturn2 = vatReturn1.copy (
        vrn       = vrn2,
        reference = ReturnReference(vrn2, vatReturn1.period)
      )
      val correction2 = correction1 copy (
        vrn       = vrn2
      )

      val insertResult1 = repository.insert(vatReturn1, correction1).futureValue
      val insertReturn2 = repository.insert(vatReturn2, correction2).futureValue
      val databaseRecords = findAll().futureValue
      val decryptedDatabaseRecords =
        databaseRecords.map(e => determineEncryptionType(e))

      insertResult1 mustBe Some((vatReturn1, correction1))
      insertReturn2 mustBe Some((vatReturn2, correction2))
      decryptedDatabaseRecords must contain theSameElementsAs Seq(vatReturn1, vatReturn2)
    }

    "must not insert a return and correction with the same VRN and period" in {

      val vatReturn = arbitrary[VatReturn].sample.value
      val correction = arbitrary[CorrectionPayload].sample.value.copy(period = vatReturn.period)

      val insertResult1 = repository.insert(vatReturn, correction).futureValue
      val insertResult2 = repository.insert(vatReturn, correction).futureValue

      insertResult1 mustBe Some((vatReturn, correction))
      insertResult2 mustBe None

      val decryptedDatabaseRecords =
        findAll().futureValue.map(e => determineEncryptionType(e))

      decryptedDatabaseRecords must contain only vatReturn
    }
  }

  ".get all" - {

    "must return all records" in {

      val vatReturn1    = arbitrary[VatReturn].sample.value
      val return2Period = vatReturn1.period.asInstanceOf[StandardPeriod] copy (year = vatReturn1.period.year + 1)
      val vatReturn2    = vatReturn1.copy (
        period    = return2Period,
        reference = ReturnReference(vatReturn1.vrn, return2Period)
      )
      val vrn3       = Vrn(StringUtils.rotateDigitsInString(vatReturn1.vrn.vrn).mkString)
      val vatReturn3 = vatReturn1.copy (
        vrn       = vrn3,
        reference = ReturnReference(vrn3, vatReturn1.period)
      )

      insert(encryptor.encryptReturn(vatReturn1, vatReturn1.vrn)).futureValue
      insert(encryptor.encryptReturn(vatReturn2, vatReturn2.vrn)).futureValue
      insert(encryptor.encryptReturn(vatReturn3, vatReturn3.vrn)).futureValue

      val returns = repository.get().futureValue

      returns must contain theSameElementsAs Seq(vatReturn1, vatReturn2, vatReturn3)
    }
  }

  ".get many" - {

    "must return all records for the given VRN" in {

      val vatReturn1    = arbitrary[VatReturn].sample.value
      val return2Period = vatReturn1.period.asInstanceOf[StandardPeriod] copy (year = vatReturn1.period.year + 1)
      val vatReturn2    = vatReturn1.copy (
        period    = return2Period,
        reference = ReturnReference(vatReturn1.vrn, return2Period)
      )
      val vrn3       = Vrn(StringUtils.rotateDigitsInString(vatReturn1.vrn.vrn).mkString)
      val vatReturn3 = vatReturn1.copy (
        vrn       = vrn3,
        reference = ReturnReference(vrn3, vatReturn1.period)
      )

      insert(encryptor.encryptReturn(vatReturn1, vatReturn1.vrn)).futureValue
      insert(encryptor.encryptReturn(vatReturn2, vatReturn2.vrn)).futureValue
      insert(encryptor.encryptReturn(vatReturn3, vatReturn3.vrn)).futureValue

      val returns = repository.get(vatReturn1.vrn).futureValue

      returns must contain theSameElementsAs Seq(vatReturn1, vatReturn2)
    }
  }

  ".get for periods" - {

    "must return all records for the given periods" in {

      val vatReturn1    = arbitrary[VatReturn].sample.value
      val return2Period = vatReturn1.period.asInstanceOf[StandardPeriod] copy (year = vatReturn1.period.year + 1)
      val vatReturn2    = vatReturn1.copy (
        period    = return2Period,
        reference = ReturnReference(vatReturn1.vrn, return2Period)
      )
      val vrn3       = Vrn(StringUtils.rotateDigitsInString(vatReturn1.vrn.vrn).mkString)
      val vatReturn3 = vatReturn1.copy (
        vrn       = vrn3,
        reference = ReturnReference(vrn3, vatReturn1.period)
      )

      insert(encryptor.encryptReturn(vatReturn1, vatReturn1.vrn)).futureValue
      insert(encryptor.encryptReturn(vatReturn2, vatReturn2.vrn)).futureValue
      insert(encryptor.encryptReturn(vatReturn3, vatReturn3.vrn)).futureValue

      val returns = repository.getByPeriods(Seq(vatReturn1.period, return2Period)).futureValue

      returns must contain theSameElementsAs Seq(vatReturn1, vatReturn2, vatReturn3)
    }
  }

  ".get one" - {

    "must return a VAT return when one exists for this VRN and period" in {

      val vatReturn = arbitrary[VatReturn].sample.value

      insert(encryptor.encryptReturn(vatReturn, vatReturn.vrn)).futureValue

      val result = repository.get(vatReturn.vrn, vatReturn.period).futureValue

      result.value mustEqual vatReturn
    }

    "must return None when a return does not exist for this VRN and period" in {

      val vrn = arbitrary[Vrn].sample.value
      val period = arbitrary[Period].sample.value

      val result = repository.get(vrn, period).futureValue

      result must not be defined
    }
  }

  private def determineEncryptionType(returns: EncryptedVatReturn): VatReturn = {
    returns match {
      case l: LegacyEncryptedVatReturn =>
        encryptor.decryptLegacyReturn(l, l.vrn)
      case n: NewEncryptedVatReturn =>
        encryptor.decryptReturn(n, n.vrn)
      case _ => throw new IllegalArgumentException("Not a valid EncryptedVatReturn type.")
    }
  }
}

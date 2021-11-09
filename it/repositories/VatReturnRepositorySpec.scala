package repositories

import config.AppConfig
import crypto.{ReturnEncrypter, SecureGCMCipher}
import generators.Generators
import models.{EncryptedVatReturn, Period, ReturnReference, VatReturn}
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar.mock
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, DefaultPlayMongoRepositorySupport}
import utils.StringUtils

import scala.concurrent.ExecutionContext.Implicits.global

class VatReturnRepositorySpec
  extends AnyFreeSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[EncryptedVatReturn]
    with CleanMongoCollectionSupport
    with ScalaFutures
    with IntegrationPatience
    with OptionValues
    with Generators {

  private val cipher    = new SecureGCMCipher
  private val encrypter = new ReturnEncrypter(cipher)
  private val appConfig = mock[AppConfig]
  private val secretKey = "VqmXp7yigDFxbCUdDdNZVIvbW6RgPNJsliv6swQNCL8="

  when(appConfig.encryptionKey) thenReturn secretKey

  override protected val repository =
    new VatReturnRepository(
      mongoComponent = mongoComponent,
      returnEncrypter = encrypter,
      appConfig = appConfig
    )


  ".insert" - {

    "must insert returns for the same VRN but different periods" in {

      val vatReturn1    = arbitrary[VatReturn].sample.value
      val return2Period = vatReturn1.period copy (year = vatReturn1.period.year + 1)
      val vatReturn2    = vatReturn1 copy (
        period    = return2Period,
        reference = ReturnReference(vatReturn1.vrn, return2Period)
      )

      val insertResult1 = repository.insert(vatReturn1).futureValue
      val insertReturn2 = repository.insert(vatReturn2).futureValue
      val databaseRecords = findAll().futureValue
      val decryptedDatabaseRecords =
        databaseRecords.map(e => encrypter.decryptReturn(e, e.vrn, secretKey))

      insertResult1 mustBe Some(vatReturn1)
      insertReturn2 mustBe Some(vatReturn2)
      decryptedDatabaseRecords must contain theSameElementsAs Seq(vatReturn1, vatReturn2)
    }

    "must insert returns for different VRNs in the same period" in {

      val vatReturn1 = arbitrary[VatReturn].sample.value
      val vrn2       = Vrn(StringUtils.rotateDigitsInString(vatReturn1.vrn.vrn).mkString)
      val vatReturn2 = vatReturn1 copy (
        vrn       = vrn2,
        reference = ReturnReference(vrn2, vatReturn1.period)
      )

      val insertResult1 = repository.insert(vatReturn1).futureValue
      val insertReturn2 = repository.insert(vatReturn2).futureValue
      val databaseRecords = findAll().futureValue
      val decryptedDatabaseRecords =
        databaseRecords.map(e => encrypter.decryptReturn(e, e.vrn, secretKey))

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
        findAll().futureValue.map(e => encrypter.decryptReturn(e, e.vrn, secretKey))

      decryptedDatabaseRecords must contain only vatReturn
    }
  }

  ".get many" - {

    "must return all records for the given VRN" in {

      val vatReturn1    = arbitrary[VatReturn].sample.value
      val return2Period = vatReturn1.period copy (year = vatReturn1.period.year + 1)
      val vatReturn2    = vatReturn1 copy (
        period    = return2Period,
        reference = ReturnReference(vatReturn1.vrn, return2Period)
      )
      val vrn3       = Vrn(StringUtils.rotateDigitsInString(vatReturn1.vrn.vrn).mkString)
      val vatReturn3 = vatReturn1 copy (
        vrn       = vrn3,
        reference = ReturnReference(vrn3, vatReturn1.period)
      )

      insert(encrypter.encryptReturn(vatReturn1, vatReturn1.vrn, secretKey)).futureValue
      insert(encrypter.encryptReturn(vatReturn2, vatReturn2.vrn, secretKey)).futureValue
      insert(encrypter.encryptReturn(vatReturn3, vatReturn3.vrn, secretKey)).futureValue

      val returns = repository.get(vatReturn1.vrn).futureValue

      returns must contain theSameElementsAs Seq(vatReturn1, vatReturn2)
    }
  }

  ".get one" - {

    "must return a VAT return when one exists for this VRN and period" in {

      val vatReturn = arbitrary[VatReturn].sample.value

      insert(encrypter.encryptReturn(vatReturn, vatReturn.vrn, secretKey)).futureValue

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
}

package repositories

import com.typesafe.config.Config
import config.AppConfig
import crypto.{AesGCMCrypto, SavedUserAnswersEncryptor}
import generators.Generators
import models.{EncryptedSavedUserAnswers, LegacyEncryptedSavedUserAnswers, NewEncryptedSavedUserAnswers, Period, SavedUserAnswers, StandardPeriod}
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
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, DefaultPlayMongoRepositorySupport}
import utils.StringUtils

import java.time.{Clock, Instant, ZoneId}
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext.Implicits.global

class SaveForLaterRepositorySpec
  extends AnyFreeSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[EncryptedSavedUserAnswers]
    with CleanMongoCollectionSupport
    with ScalaFutures
    with IntegrationPatience
    with OptionValues
    with Generators {

  private val mockAppConfig = mock[AppConfig]
  private val mockConfiguration = mock[Configuration]
  private val mockConfig = mock[Config]
  private val mockAesGCMCryptoCipher = mock[AesGCMCrypto]
  private val mockEncryptionService: EncryptionService = new EncryptionService(mockConfiguration)
  private val encryptor = new SavedUserAnswersEncryptor(mockAppConfig, mockAesGCMCryptoCipher, mockEncryptionService)
  private val secretKey: String = "VqmXp7yigDFxbCUdDdNZVIvbW6RgPNJsliv6swQNCL8="

  private val instant = Instant.now
  private val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)

  override protected val repository: SaveForLaterRepository =
    new SaveForLaterRepository(
      mongoComponent = mongoComponent,
      encryptor = encryptor,
      appConfig = mockAppConfig
    )

  when(mockConfiguration.underlying) `thenReturn` mockConfig
  when(mockConfig.getString(any())) `thenReturn` secretKey
  when(mockAppConfig.encryptionKey) `thenReturn` secretKey


  ".set savedAnswers" - {

    "must insert returns for the same VRN but different periods" in {

      val answers    = arbitrary[SavedUserAnswers].sample.value
      val answers1    = answers.copy(lastUpdated = Instant.now(stubClock).truncatedTo(ChronoUnit.MILLIS))
      val answers2Period = answers1.period.asInstanceOf[StandardPeriod].copy(year = answers1.period.year + 1)
      val answers2    = answers1.copy (
        period      = answers2Period,
        lastUpdated = Instant.now(stubClock).truncatedTo(ChronoUnit.MILLIS)
      )

      val insertResult1 = repository.set(answers1).futureValue
      val insertReturn2 = repository.set(answers2).futureValue
      val databaseRecords = findAll().futureValue
      val decryptedDatabaseRecords =
        databaseRecords.map(e => determineEncryptionType(e))

      insertResult1 mustBe answers1
      insertReturn2 mustBe answers2
      decryptedDatabaseRecords must contain theSameElementsAs Seq(answers1, answers2)
    }

    "must insert saved answers for different VRNs in the same period" in {
      val answers    = arbitrary[SavedUserAnswers].sample.value
      val answers1    = answers. copy(lastUpdated = Instant.now(stubClock).truncatedTo(ChronoUnit.MILLIS))
      val vrn2       = Vrn(StringUtils.rotateDigitsInString(answers1.vrn.vrn).mkString)
      val answers2    = answers1.copy (
        vrn         = vrn2,
        lastUpdated = Instant.now(stubClock).truncatedTo(ChronoUnit.MILLIS)
        )

      val insertResult1 = repository.set(answers1).futureValue
      val insertReturn2 = repository.set(answers2).futureValue
      val databaseRecords = findAll().futureValue
      val decryptedDatabaseRecords =
        databaseRecords.map(e => determineEncryptionType(e))

      insertResult1 mustBe answers1
      insertReturn2 mustBe answers2
      decryptedDatabaseRecords must contain theSameElementsAs Seq(answers1, answers2)
    }

    "must replace saved answers with the same VRN and period" in {

      val answers = arbitrary[SavedUserAnswers].sample.value
      val answers2 = answers.copy(lastUpdated = Instant.now(stubClock).truncatedTo(ChronoUnit.MILLIS))
      val insertResult1 = repository.set(answers).futureValue
      val insertResult2 = repository.set(answers2).futureValue

      insertResult1 mustBe answers
      insertResult2 mustBe answers2

      val decryptedDatabaseRecords =
        findAll().futureValue.map(e => determineEncryptionType(e))

      decryptedDatabaseRecords must contain only answers2
    }
  }
  
  ".get many" - {

    "must return all records for the given VRN" in {

      val answers    = arbitrary[SavedUserAnswers].sample.value
      val answers1 = answers.copy(lastUpdated = Instant.now(stubClock).truncatedTo(ChronoUnit.MILLIS))
      val answers2Period = answers1.period.asInstanceOf[StandardPeriod].copy(year = answers1.period.year + 1)
      val answers2    = answers1.copy (
        period      = answers2Period,
        lastUpdated = Instant.now(stubClock).truncatedTo(ChronoUnit.MILLIS)
        )

      val vrn3       = Vrn(StringUtils.rotateDigitsInString(answers1.vrn.vrn).mkString)
      val answers3 = answers1.copy(
        vrn         = vrn3
      )

      insert(encryptor.encryptAnswers(answers1, answers1.vrn)).futureValue
      insert(encryptor.encryptAnswers(answers2, answers2.vrn)).futureValue
      insert(encryptor.encryptAnswers(answers3, answers3.vrn)).futureValue

      val returns = repository.get(answers1.vrn).futureValue

      returns must contain theSameElementsAs Seq(answers1, answers2)
    }
  }

  ".get one" - {

    "must return Saved answers record when one exists for this VRN and period" in {

      val answers1 = arbitrary[SavedUserAnswers].sample.value

      val answers = answers1.copy(lastUpdated = Instant.now(stubClock).truncatedTo(ChronoUnit.MILLIS))

      insert(encryptor.encryptAnswers(answers, answers.vrn)).futureValue

      val result = repository.get(answers.vrn, answers.period).futureValue

      result.value mustEqual answers
    }

    "must return None when a return does not exist for this VRN and period" in {

      val vrn = arbitrary[Vrn].sample.value
      val period = arbitrary[Period].sample.value

      val result = repository.get(vrn, period).futureValue

      result must not be defined
    }
  }

  ".clear" - {

    "must return true when Saved Answers Record is deleted" in {

      val answers = arbitrary[SavedUserAnswers].sample.value

      insert(encryptor.encryptAnswers(answers, answers.vrn)).futureValue

      val result = repository.clear(answers.vrn, answers.period).futureValue

      result mustEqual true
    }
  }

  private def determineEncryptionType(answers: EncryptedSavedUserAnswers): SavedUserAnswers = {
    answers match {
      case l: LegacyEncryptedSavedUserAnswers =>
        encryptor.decryptLegacyAnswers(l, l.vrn)
      case n: NewEncryptedSavedUserAnswers =>
        encryptor.decryptAnswers(n, n.vrn)
      case _ => throw new IllegalArgumentException("Not a valid EncryptedSavedUserAnswers type.")
    }
  }
}

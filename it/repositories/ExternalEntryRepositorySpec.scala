package repositories

import config.AppConfig
import models.external.ExternalEntry
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar.mock
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, DefaultPlayMongoRepositorySupport}

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext.Implicits.global

class ExternalEntryRepositorySpec
  extends AnyFreeSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[ExternalEntry]
    with CleanMongoCollectionSupport
    with ScalaFutures
    with IntegrationPatience
    with OptionValues {

  private val appConfig = mock[AppConfig]

  implicit val arbitraryExternalEntry: Arbitrary[ExternalEntry] =
    Arbitrary {
      for {
        userId <- arbitrary[String]
        url <- arbitrary[String]
        now = Instant.now
      } yield ExternalEntry(
        userId,
        url,
        now)
    }

  override protected val repository =
    new ExternalEntryRepository(
      mongoComponent = mongoComponent,
      appConfig = appConfig
    )

  ".set external entry" - {

    "must insert entry for different user ids" in {
      val answers1 = arbitrary[ExternalEntry].sample.value
      val userId2 = arbitrary[String].sample.value
      val answers2 = answers1 copy (
        userId = userId2
      )

      val insertResult1 = repository.set(answers1).futureValue
      val insertReturn2 = repository.set(answers2).futureValue
      val databaseRecords = findAll().futureValue

      insertResult1 mustBe (answers1)
      insertReturn2 mustBe (answers2)

      val expectedAnswer1 = answers1.copy(lastUpdated = answers1.lastUpdated.truncatedTo(ChronoUnit.MILLIS))
      val expectedAnswer2 = answers2.copy(lastUpdated = answers2.lastUpdated.truncatedTo(ChronoUnit.MILLIS))
      databaseRecords must contain theSameElementsAs Seq(expectedAnswer1, expectedAnswer2)
    }

    "must replace saved answers with the same VRN" in {

      val answers = arbitrary[ExternalEntry].sample.value
      val answers2 = answers.copy(lastUpdated = Instant.now())
      val insertResult1 = repository.set(answers).futureValue
      val insertResult2 = repository.set(answers2).futureValue

      insertResult1 mustBe answers
      insertResult2 mustBe answers2

      val databaseRecords = findAll().futureValue

      val expectedAnswer2 = answers2.copy(lastUpdated = answers2.lastUpdated.truncatedTo(ChronoUnit.MILLIS))
      databaseRecords must contain only expectedAnswer2
    }
  }

  ".get one" - {

    "must return Saved answers record when one exists for this VRN" in {

      val answers = arbitrary[ExternalEntry].sample.value

      insert(answers).futureValue

      val result = repository.get(answers.userId).futureValue


      val expectedAnswer = answers.copy(lastUpdated = answers.lastUpdated.truncatedTo(ChronoUnit.MILLIS))
      result.value mustEqual expectedAnswer
    }

    "must return None when a return does not exist for this VRN" in {

      val userId = arbitrary[String].sample.value

      val result = repository.get(userId).futureValue

      result must not be defined
    }
  }

  ".clear" - {

    "must return true when Saved Answers Record is deleted" in {

      val answers = arbitrary[ExternalEntry].sample.value

      insert(answers).futureValue

      val result = repository.clear(answers.userId).futureValue

      result mustEqual true
    }
  }
}


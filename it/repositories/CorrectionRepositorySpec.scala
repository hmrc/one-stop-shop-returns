package repositories

import config.AppConfig
import generators.Generators
import models.corrections.CorrectionPayload
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

class CorrectionRepositorySpec
  extends AnyFreeSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[CorrectionPayload]
    with CleanMongoCollectionSupport
    with ScalaFutures
    with IntegrationPatience
    with OptionValues
    with Generators {

  override protected val repository =
    new CorrectionRepository(
      mongoComponent = mongoComponent,
      appConfig = appConfig
    )
  private val appConfig = mock[AppConfig]

  ".insert" - {

    "must insert correction" in {

      val correctionPayload = arbitrary[CorrectionPayload].sample.value

      val insertResult1 = repository.insert(correctionPayload).futureValue
      val databaseRecords = findAll().futureValue

      insertResult1 mustBe Some(correctionPayload)
      databaseRecords must contain theSameElementsAs Seq(correctionPayload)
    }

    "must insert multiple corrections for different VRNs in the same period" in {

      val correctionPayload1 = arbitrary[CorrectionPayload].sample.value
      val vrn2 = Vrn(StringUtils.rotateDigitsInString(correctionPayload1.vrn.vrn).mkString)
      val correctionPayload2 = correctionPayload1.copy(
        vrn = vrn2
      )

      val insertResult1 = repository.insert(correctionPayload1).futureValue
      val insertReturn2 = repository.insert(correctionPayload2).futureValue
      val databaseRecords = findAll().futureValue

      insertResult1 mustBe Some(correctionPayload1)
      insertReturn2 mustBe Some(correctionPayload2)
      databaseRecords must contain theSameElementsAs Seq(correctionPayload1, correctionPayload2)
    }

  }

  ".get" - {
    "must return all records for the given VRN" in {

      val correctionPayload1 = arbitrary[CorrectionPayload].sample.value
      val correctionPayload2Period = correctionPayload1.period copy (year = correctionPayload1.period.year + 1)
      val correctionPayload2 = correctionPayload1.copy(
        period = correctionPayload2Period
      )
      val vrn3 = Vrn(StringUtils.rotateDigitsInString(correctionPayload1.vrn.vrn).mkString)
      val correctionPayload3 = correctionPayload1.copy(
        vrn = vrn3
      )

      insert(correctionPayload1).futureValue
      insert(correctionPayload2).futureValue
      insert(correctionPayload3).futureValue

      val returns = repository.get(correctionPayload1.vrn).futureValue

      returns must contain theSameElementsAs Seq(correctionPayload1, correctionPayload2)
    }
  }

  ".get one" - {

    "must return a VAT return when one exists for this VRN and period" in {

      val correctionPayload = arbitrary[CorrectionPayload].sample.value

      insert(correctionPayload).futureValue

      val result = repository.get(correctionPayload.vrn, correctionPayload.period).futureValue

      result.value mustEqual correctionPayload
    }
  }


}

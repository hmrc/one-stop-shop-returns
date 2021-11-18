package repositories

import config.AppConfig
import generators.Generators
import models.corrections.{CorrectionPayload, CorrectionToCountry, PeriodWithCorrections}
import models.{Country, Period}
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar.mock
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, DefaultPlayMongoRepositorySupport}
import utils.StringUtils

import java.time.Instant
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

    "must return empty for the given VRN" in {

      val vrn = arbitrary[Vrn].sample.value

      val returns = repository.get(vrn).futureValue

      returns must contain theSameElementsAs Seq.empty
    }
  }

  ".get one" - {

    "must return a VAT return when one exists for this VRN and period" in {

      val correctionPayload = arbitrary[CorrectionPayload].sample.value

      insert(correctionPayload).futureValue

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

      insert(correctionPayload).futureValue

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

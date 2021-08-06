package repositories

import generators.Generators
import models.InsertResult.{AlreadyExists, InsertSucceeded}
import models.{Period, ReturnReference, VatReturn}
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, DefaultPlayMongoRepositorySupport}

import scala.concurrent.ExecutionContext.Implicits.global

class VatReturnRepositorySpec
  extends AnyFreeSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[VatReturn]
    with CleanMongoCollectionSupport
    with ScalaFutures
    with IntegrationPatience
    with OptionValues
    with Generators {

  override protected val repository =
    new VatReturnRepository(mongoComponent = mongoComponent)

  private def rotateDigitsInString(chars: String): String =
    chars.map {
      char =>
        (char.asDigit + 1) % 10
    }.mkString

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

      insertResult1 mustEqual InsertSucceeded
      insertReturn2 mustEqual InsertSucceeded
      databaseRecords must contain theSameElementsAs Seq(vatReturn1, vatReturn2)
    }

    "must insert returns for different VRNs in the same period" in {

      val vatReturn1 = arbitrary[VatReturn].sample.value
      val vrn2       = Vrn(rotateDigitsInString(vatReturn1.vrn.vrn).mkString)
      val vatReturn2 = vatReturn1 copy (
        vrn       = vrn2,
        reference = ReturnReference(vrn2, vatReturn1.period)
      )

      val insertResult1 = repository.insert(vatReturn1).futureValue
      val insertReturn2 = repository.insert(vatReturn2).futureValue
      val databaseRecords = findAll().futureValue

      insertResult1 mustEqual InsertSucceeded
      insertReturn2 mustEqual InsertSucceeded
      databaseRecords must contain theSameElementsAs Seq(vatReturn1, vatReturn2)
    }

    "must not insert a return with the same VRN and period" in {

      val vatReturn = arbitrary[VatReturn].sample.value

      val insertResult1 = repository.insert(vatReturn).futureValue
      val insertResult2 = repository.insert(vatReturn).futureValue

      insertResult1 mustEqual InsertSucceeded
      insertResult2 mustEqual AlreadyExists

      findAll().futureValue must contain only vatReturn
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
      val vrn3       = Vrn(rotateDigitsInString(vatReturn1.vrn.vrn).mkString)
      val vatReturn3 = vatReturn1 copy (
        vrn       = vrn3,
        reference = ReturnReference(vrn3, vatReturn1.period)
      )

      insert(vatReturn1).futureValue
      insert(vatReturn2).futureValue
      insert(vatReturn3).futureValue

      val returns = repository.get(vatReturn1.vrn).futureValue

      returns must contain theSameElementsAs Seq(vatReturn1, vatReturn2)
    }
  }

  ".get one" - {

    "must return a VAT return when one exists for this VRN and period" in {

      val vatReturn = arbitrary[VatReturn].sample.value

      insert(vatReturn).futureValue

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

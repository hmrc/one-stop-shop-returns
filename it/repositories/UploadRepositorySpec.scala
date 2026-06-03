package repositories

import config.AppConfig
import models.fileUpload.{FailureReason, UploadDocument}
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, DefaultPlayMongoRepositorySupport}

import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global

class UploadRepositorySpec
  extends AnyFreeSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[UploadDocument]
    with CleanMongoCollectionSupport
    with ScalaFutures
    with IntegrationPatience
    with OptionValues
    with MockitoSugar {

  private val appConfig = mock[AppConfig]
  private val fixedInstant = Instant.parse("2025-01-01T10:15:30.123Z")
  private val clock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

  override protected val repository: UploadRepository =
    new UploadRepository(
      mongoComponent = mongoComponent,
      appConfig = appConfig,
      clock = clock
    )

  ".insert" - {

    "must insert an INITIATED document for the reference" in {
      val ref = "ref-1"

      repository.insert(ref).futureValue

      val records = findAll().futureValue
      records must have size 1
      records.head._id mustBe ref
      records.head.status mustBe "INITIATED"
    }
  }

  ".markAsUploaded" - {

    "must upsert and set status/checksum/fileName/size and createdAt on insert" in {
      val ref = "ref-uploaded-1"
      val checksum = "abc123"
      val fileName = "test.csv"
      val size = 1234L
      val downloadUrl = "https://s3.test/download/123"

      repository.markAsUploaded(ref, checksum, fileName, size, downloadUrl).futureValue

      val saved = repository.getUpload(ref).futureValue.value

      saved._id mustBe ref
      saved.status mustBe "UPLOADED"
      saved.checksum.value mustBe checksum
      saved.fileName.value mustBe fileName
      saved.size.value mustBe size
    }

  }

  ".markAsFailed" - {

    "must upsert and set FAILED + failureReason and set fileName when provided" in {
      val ref = "ref-failed-1"

      repository.markAsFailed(ref, FailureReason.Rejected, fileName = Some("bad.csv")).futureValue

      val saved = repository.getUpload(ref).futureValue.value
      saved._id mustBe ref
      saved.status mustBe "FAILED"
      saved.failureReason.value mustBe FailureReason.Rejected
      saved.fileName.value mustBe "bad.csv"
    }

    "must unset fileName when not provided" in {
      val ref = "ref-failed-2"

      repository.markAsFailed(ref, FailureReason.NotCSV, fileName = Some("x.ods")).futureValue

      repository.markAsFailed(ref, FailureReason.NotCSV, fileName = None).futureValue

      val saved = repository.getUpload(ref).futureValue.value
      saved.fileName mustBe empty
      saved.failureReason.value mustBe FailureReason.NotCSV
    }

  }

  ".getUpload" - {

    "must return None when no document exists" in {
      repository.getUpload("does-not-exist").futureValue mustBe None
    }

    "must return the document when it exists" in {
      val ref = "ref-get-1"
      repository.insert(ref).futureValue

      val result = repository.getUpload(ref).futureValue
      result.value._id mustBe ref
      result.value.status mustBe "INITIATED"
    }
  }

}

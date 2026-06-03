package services.upscan

import base.SpecBase
import config.AppConfig
import models.fileUpload.*
import models.fileUpload.FailureReason.*
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import repositories.UploadRepository

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class UpscanCallbackServiceSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach {
  
  implicit val ec: ExecutionContext = ExecutionContext.global

  "UpscanCallbackService" - {

    "mark upload as uploaded on success callback" in {

      val mockRepo = mock[UploadRepository]
      val mockAppConfig = mock[AppConfig]
      when(mockAppConfig.maxFileSize).thenReturn(2L * 1024L * 1024L)
      when(mockRepo.markAsUploaded(any(), any(), any(), any(), any())).thenReturn(Future.successful(()))

      val service = new UpscanCallbackService(mockAppConfig, mockRepo)

      val callback = UpscanCallbackSuccess(
        reference = "123",
        fileStatus = "READY",
        uploadDetails = UploadDetails(
          fileName = "test.csv",
          fileMimeType = "text/csv",
          uploadTimestamp = Instant.now(),
          checksum = "abc123",
          size = 1024L
        ),
        downloadUrl = "https://s3.test/download/123"
      )

      whenReady(service.handleUpscanCallback(callback)) { _ =>
        verify(mockRepo).markAsUploaded("123", "abc123", "test.csv", 1024L, "https://s3.test/download/123")
      }
    }

    "mark upload as failed on failure callback" in {
      val mockRepo = mock[UploadRepository]
      val mockAppConfig = mock[AppConfig]
      when(mockRepo.markAsFailed(any(), any(), any()))
        .thenReturn(Future.successful(()))

      val service = new UpscanCallbackService(mockAppConfig, mockRepo)

      val callback = UpscanCallbackFailure(
        reference = "123",
        fileStatus = "FAILED",
        failureDetails = FailureDetails(FailureReason.Quarantine),
        uploadDetails = Some(UploadDetails(
          fileName = "test.csv",
          fileMimeType = "text/csv",
          uploadTimestamp = Instant.now(),
          checksum = "abc123",
          size = 1024
        ))
      )

      whenReady(service.handleUpscanCallback(callback)) { _ =>
        verify(mockRepo).markAsFailed("123", FailureReason.Quarantine, Some("test.csv"))
      }
    }

    "mark upload as failed when file is not a CSV" in {

      val mockRepo = mock[UploadRepository]
      val mockAppConfig = mock[AppConfig]
      when(mockRepo.markAsFailed(any(), any(), any())).thenReturn(Future.successful(()))

      val service = new UpscanCallbackService(mockAppConfig, mockRepo)

      val callback = UpscanCallbackSuccess(
        reference = "456",
        fileStatus = "READY",
        uploadDetails = UploadDetails(
          fileName = "test.pdf",
          fileMimeType = "text/pdf",
          uploadTimestamp = Instant.now(),
          checksum = "abc123",
          size = 1024
        ),
        downloadUrl = "https://s3.test/download/456"
      )

      whenReady(service.handleUpscanCallback(callback)) { _ =>
        verify(mockRepo).markAsFailed("456", FailureReason.NotCSV, Some("test.pdf"))
      }
    }

    "mark upload as uploaded when mime type is application/csv" in {

      val mockRepo = mock[UploadRepository]
      val mockAppConfig = mock[AppConfig]
      when(mockAppConfig.maxFileSize).thenReturn(2L * 1024L * 1024L)
      when(mockRepo.markAsUploaded(any(), any(), any(), any(), any())).thenReturn(Future.successful(()))

      val service = new UpscanCallbackService(mockAppConfig, mockRepo)

      val callback = UpscanCallbackSuccess(
        reference = "456",
        fileStatus = "READY",
        uploadDetails = UploadDetails(
          fileName = "test.csv",
          fileMimeType = "application/csv",
          uploadTimestamp = Instant.now(),
          checksum = "abc123",
          size = 1024
        ),
        downloadUrl = "https://s3.test/download/456"
      )

      whenReady(service.handleUpscanCallback(callback)) { _ =>
        verify(mockRepo).markAsUploaded("456", "abc123", "test.csv", 1024L, "https://s3.test/download/456")
      }

    }


    "mark upload as failed when file is an ods" in {

      val mockRepo = mock[UploadRepository]
      val mockAppConfig = mock[AppConfig]
      when(mockRepo.markAsFailed(any(), any(), any())).thenReturn(Future.successful(()))

      val service = new UpscanCallbackService(mockAppConfig, mockRepo)

      val callback = UpscanCallbackSuccess(
        reference = "ods-1",
        fileStatus = "READY",
        uploadDetails = UploadDetails(
          fileName = "test.ods",
          fileMimeType = "application/vnd.oasis.opendocument.spreadsheet",
          uploadTimestamp = Instant.now(),
          checksum = "abc123",
          size = 1024
        ),
        downloadUrl = "https://s3.test/download/ods-1"
      )

      whenReady(service.handleUpscanCallback(callback)) { _ =>
        verify(mockRepo).markAsFailed("ods-1", FailureReason.InvalidFileType, Some("test.ods"))
      }
    }

    "mark upload as failed on failure callback with Quarantine" in {

      val mockRepo = mock[UploadRepository]
      val mockAppConfig = mock[AppConfig]
      when(mockRepo.markAsFailed(any(), any(), any())).thenReturn(Future.successful(()))

      val service = new UpscanCallbackService(mockAppConfig, mockRepo)

      val callback = UpscanCallbackFailure(
        reference = "123",
        fileStatus = "FAILED",
        failureDetails = FailureDetails(Quarantine),
        uploadDetails = Some(UploadDetails(
          fileName = "test.csv",
          fileMimeType = "text/csv",
          uploadTimestamp = Instant.now(),
          checksum = "abc123",
          size = 1024
        ))
      )

      whenReady(service.handleUpscanCallback(callback)) { _ =>
        verify(mockRepo).markAsFailed("123", FailureReason.Quarantine, Some("test.csv"))
      }
    }

    "mark upload as failed on failure callback with Unknown" in {

      val mockRepo = mock[UploadRepository]
      val mockAppConfig = mock[AppConfig]
      when(mockRepo.markAsFailed(any(), any(), any())).thenReturn(Future.successful(()))

      val service = new UpscanCallbackService(mockAppConfig, mockRepo)

      val callback = UpscanCallbackFailure(
        reference = "unknown-1",
        fileStatus = "FAILED",
        failureDetails = FailureDetails(Unknown),
        uploadDetails = Some(UploadDetails(
          fileName = "test.csv",
          fileMimeType = "text/csv",
          uploadTimestamp = Instant.now(),
          checksum = "abc123",
          size = 1024
        ))
      )

      whenReady(service.handleUpscanCallback(callback)) { _ =>
        verify(mockRepo).markAsFailed("unknown-1", FailureReason.Unknown, Some("test.csv"))
      }
    }

    "mark upload as failed on failure callback with Rejected file is an ods" in {

      val mockRepo = mock[UploadRepository]
      val mockAppConfig = mock[AppConfig]
      when(mockRepo.markAsFailed(any(), any(), any())).thenReturn(Future.successful(()))

      val service = new UpscanCallbackService(mockAppConfig, mockRepo)

      val callback = UpscanCallbackFailure(
        reference = "rej-ods-1",
        fileStatus = "FAILED",
        failureDetails = FailureDetails(
          failureReason = Rejected,
          message = Some("file rejected")
        ),
        uploadDetails = Some(UploadDetails(
          fileName = "test.ods",
          fileMimeType = "application/vnd.oasis.opendocument.spreadsheet",
          uploadTimestamp = Instant.now(),
          checksum = "abc123",
          size = 1024
        ))
      )

      whenReady(service.handleUpscanCallback(callback)) { _ =>
        verify(mockRepo).markAsFailed("rej-ods-1", FailureReason.InvalidFileType, Some("test.ods"))
      }
    }

    "mark upload as failed on failure callback with Rejected file is not CSV" in {

      val mockRepo = mock[UploadRepository]
      val mockAppConfig = mock[AppConfig]
      when(mockRepo.markAsFailed(any(), any(), any())).thenReturn(Future.successful(()))

      val service = new UpscanCallbackService(mockAppConfig, mockRepo)

      val callback = UpscanCallbackFailure(
        reference = "rej-notCSV-1",
        fileStatus = "FAILED",
        failureDetails = FailureDetails(
          failureReason = Rejected,
          message = Some("mime type image/png is not allowed")
        ),
        uploadDetails = Some(UploadDetails(
          fileName = "image.png",
          fileMimeType = "image/png",
          uploadTimestamp = Instant.now(),
          checksum = "abc123",
          size = 1024
        ))
      )

      whenReady(service.handleUpscanCallback(callback)) { _ =>
        verify(mockRepo).markAsFailed("rej-notCSV-1", FailureReason.NotCSV, Some("image.png"))
      }
    }

    "mark upload as failed on failure callback with Rejected file no CSV mime types" in {

      val mockRepo = mock[UploadRepository]
      val mockAppConfig = mock[AppConfig]
      when(mockRepo.markAsFailed(any(), any(), any())).thenReturn(Future.successful(()))

      val service = new UpscanCallbackService(mockAppConfig, mockRepo)

      val callback = UpscanCallbackFailure(
        reference = "rej-notCSV-2",
        fileStatus = "FAILED",
        failureDetails = FailureDetails(
          failureReason = Rejected,
          message = Some("nvalid mime type")
        ),
        uploadDetails = None
      )

      whenReady(service.handleUpscanCallback(callback)) { _ =>
        verify(mockRepo).markAsFailed("rej-notCSV-2", FailureReason.NotCSV, None)
      }
    }

    "mark upload as failed with NotCSV when callback failure reason is NotCSV" in {

      val mockRepo = mock[UploadRepository]
      val mockAppConfig = mock[AppConfig]
      when(mockRepo.markAsFailed(any(), any(), any())).thenReturn(Future.successful(()))

      val service = new UpscanCallbackService(mockAppConfig, mockRepo)

      val callback = UpscanCallbackFailure(
        reference = "notCSV-1",
        fileStatus = "FAILED",
        failureDetails = FailureDetails(NotCSV),
        uploadDetails = Some(UploadDetails(
          fileName = "test.pdf",
          fileMimeType = "text/plain",
          uploadTimestamp = Instant.now(),
          checksum = "abc123",
          size = 1024
        ))
      )

      whenReady(service.handleUpscanCallback(callback)) { _ =>
        verify(mockRepo).markAsFailed("notCSV-1", FailureReason.NotCSV, Some("test.pdf"))
      }
    }

    "mark upload as failed with TooLarge when callback failure reason is TooLarge" in {

      val mockRepo = mock[UploadRepository]
      val mockAppConfig = mock[AppConfig]
      when(mockRepo.markAsFailed(any(), any(), any())).thenReturn(Future.successful(()))

      val service = new UpscanCallbackService(mockAppConfig, mockRepo)

      val callback = UpscanCallbackFailure(
        reference = "tooLarge-1",
        fileStatus = "FAILED",
        failureDetails = FailureDetails(TooLarge),
        uploadDetails = Some(UploadDetails(
          fileName = "big.csv",
          fileMimeType = "text/csv",
          uploadTimestamp = Instant.now(),
          checksum = "abc123",
          size = 9999999L
        ))
      )

      whenReady(service.handleUpscanCallback(callback)) { _ =>
        verify(mockRepo).markAsFailed("tooLarge-1", FailureReason.TooLarge, Some("big.csv"))
      }
    }

    "mark upload as failed with InvalidFileType when callback failure reason is InvalidFileType" in {

      val mockRepo = mock[UploadRepository]
      val mockAppConfig = mock[AppConfig]
      when(mockRepo.markAsFailed(any(), any(), any())).thenReturn(Future.successful(()))

      val service = new UpscanCallbackService(mockAppConfig, mockRepo)

      val callback = UpscanCallbackFailure(
        reference = "invalidFileType-1",
        fileStatus = "FAILED",
        failureDetails = FailureDetails(InvalidFileType),
        uploadDetails = Some(UploadDetails(
          fileName = "bad.ods",
          fileMimeType = "application/vnd.oasis.opendocument.spreadsheet",
          uploadTimestamp = Instant.now(),
          checksum = "abc123",
          size = 1024
        ))
      )

      whenReady(service.handleUpscanCallback(callback)) { _ =>
        verify(mockRepo).markAsFailed("invalidFileType-1", FailureReason.InvalidFileType, Some("bad.ods"))
      }
    }

    "mark upload as failed with InvalidArgument when callback failure reason is InvalidArgument" in {

      val mockRepo = mock[UploadRepository]
      val mockAppConfig = mock[AppConfig]
      when(mockRepo.markAsFailed(any(), any(), any())).thenReturn(Future.successful(()))

      val service = new UpscanCallbackService(mockAppConfig, mockRepo)

      val callback = UpscanCallbackFailure(
        reference = "invalidArg-1",
        fileStatus = "FAILED",
        failureDetails = FailureDetails(InvalidArgument),
        uploadDetails = Some(UploadDetails(
          fileName = "test.csv",
          fileMimeType = "text/pdf",
          uploadTimestamp = Instant.now(),
          checksum = "abc123",
          size = 1024
        ))
      )

      whenReady(service.handleUpscanCallback(callback)) { _ =>
        verify(mockRepo).markAsFailed("invalidArg-1", FailureReason.InvalidArgument, Some("test.csv"))
      }
    }
  }

}

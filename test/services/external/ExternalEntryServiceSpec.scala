package services.external

import base.SpecBase
import models.external._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import repositories.ExternalEntryRepository
import uk.gov.hmrc.play.bootstrap.backend.http.ErrorResponse

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ExternalEntryServiceSpec extends SpecBase {
  val userId = "1234"
  val externalRequest = ExternalRequest("BTA", "/bta")
  val currentPeriod = arbitraryPeriod.arbitrary.sample.value


  ".getExternalResponse" - {
    Seq(YourAccount, ReturnsHistory).foreach {
      entryPage =>
        s"when entry page in the request is ${entryPage}" - {

          "and no period is provided" - {
            "and language specified is Welsh" - {
              "must return correct response" in {
                val mockExternalEntryRepository = mock[ExternalEntryRepository]
                val service = new ExternalEntryService(mockExternalEntryRepository, stubClock)
                val externalEntry = ExternalEntry(userId, externalRequest.returnUrl, Instant.now(stubClock))

                when(mockExternalEntryRepository.set(any())) `thenReturn` Future.successful(externalEntry)
                val result = service.getExternalResponse(externalRequest, userId, entryPage.name, None, Some("cy"))

                result mustBe Right(
                  ExternalResponse(
                    NoMoreWelsh.url(entryPage.url)
                  )
                )
                verify(mockExternalEntryRepository, times(1)).set(externalEntry)
              }
            }

            "and language is not Welsh" - {
              "must return correct response" in {
                val mockExternalEntryRepository = mock[ExternalEntryRepository]
                val service = new ExternalEntryService(mockExternalEntryRepository, stubClock)
                val externalEntry = ExternalEntry(userId, externalRequest.returnUrl, Instant.now(stubClock))

                when(mockExternalEntryRepository.set(any())) `thenReturn` Future.successful(externalEntry)
                val result = service.getExternalResponse(externalRequest, userId, entryPage.name, None, None)

                result mustBe Right(
                  ExternalResponse(
                    entryPage.url
                  )
                )
                verify(mockExternalEntryRepository, times(1)).set(externalEntry)
              }
            }

            "and period is provided" - {
              "must return Left(NotFound) and not save url in session" in {
                val mockExternalEntryRepository = mock[ExternalEntryRepository]
                val service = new ExternalEntryService(mockExternalEntryRepository, stubClock)
                val externalEntry = ExternalEntry(userId, externalRequest.returnUrl, Instant.now(stubClock))

                when(mockExternalEntryRepository.set(any())) `thenReturn` Future.successful(externalEntry)
                val result = service.getExternalResponse(externalRequest, userId, entryPage.name, Some(period), None)

                result mustBe Left(ErrorResponse(500, s"Unknown external entry ${entryPage.name}"))
                verifyNoInteractions(mockExternalEntryRepository)
              }
            }
          }
        }
    }

    Seq(StartReturn, ContinueReturn).foreach {
      entryPage =>
        s"when entry page in the request is ${entryPage}" - {

          "and period is provided" - {
            "and language specified is Welsh" - {
              "must return correct response" in {
                val mockExternalEntryRepository = mock[ExternalEntryRepository]
                val service = new ExternalEntryService(mockExternalEntryRepository, stubClock)
                val externalEntry = ExternalEntry(userId, externalRequest.returnUrl, Instant.now(stubClock))

                when(mockExternalEntryRepository.set(any())) `thenReturn` Future.successful(externalEntry)
                val result = service.getExternalResponse(externalRequest, userId, entryPage.name, Some(currentPeriod), Some("cy"))

                result mustBe Right(
                  ExternalResponse(
                    NoMoreWelsh.url(entryPage.url(currentPeriod))
                  )
                )

                verify(mockExternalEntryRepository, times(1)).set(externalEntry)
              }
            }

            "and language is not Welsh" - {
              "must return correct response" in {
                val mockExternalEntryRepository = mock[ExternalEntryRepository]
                val service = new ExternalEntryService(mockExternalEntryRepository, stubClock)
                val externalEntry = ExternalEntry(userId, externalRequest.returnUrl, Instant.now(stubClock))

                when(mockExternalEntryRepository.set(any())) `thenReturn` Future.successful(externalEntry)
                val result = service.getExternalResponse(externalRequest, userId, entryPage.name, Some(currentPeriod), None)

                result mustBe Right(
                  ExternalResponse(
                    entryPage.url(currentPeriod)
                  )
                )
                verify(mockExternalEntryRepository, times(1)).set(externalEntry)
              }
            }
          }

          "and period is not provided" - {
            "must return Left(NotFound) and not save url in session" in {
              val mockExternalEntryRepository = mock[ExternalEntryRepository]
              val service = new ExternalEntryService(mockExternalEntryRepository, stubClock)
              val externalEntry = ExternalEntry(userId, externalRequest.returnUrl, Instant.now(stubClock))

              when(mockExternalEntryRepository.set(any())) `thenReturn` Future.successful(externalEntry)
              val result = service.getExternalResponse(externalRequest, userId, entryPage.name, None, None)

              result mustBe Left(ErrorResponse(500, s"Unknown external entry ${entryPage.name}"))
              verifyNoInteractions(mockExternalEntryRepository)
            }
          }
        }
    }

    "must return an External Response when sessionRepository fails due to exception" in {
      val mockExternalEntryRepository = mock[ExternalEntryRepository]
      val service = new ExternalEntryService(mockExternalEntryRepository, stubClock)

      when(mockExternalEntryRepository.set(any())) `thenReturn` Future.failed(new Exception("Error saving in session"))
      val result = service.getExternalResponse(externalRequest, userId, YourAccount.name, None, None)

      result mustBe Right(
        ExternalResponse(
          YourAccount.url
        )
      )
    }

  }
}

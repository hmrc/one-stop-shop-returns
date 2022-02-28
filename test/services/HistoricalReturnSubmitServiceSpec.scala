package services

import base.SpecBase
import config.AppConfig
import connectors.{CoreVatReturnConnector, RegistrationConnector}
import models.{Period, VatReturn}
import models.Quarter.{Q1, Q2, Q4}
import models.core.{CoreErrorResponse, CorePeriod, CoreVatReturn, EisErrorResponse}
import models.corrections.CorrectionPayload
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchersSugar.eqTo
import org.mockito.Mockito
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.BeforeAndAfterEach
import testutils.RegistrationData
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

class HistoricalReturnSubmitServiceSpec extends SpecBase with BeforeAndAfterEach {

  private val vatReturnService = mock[VatReturnService]
  private val correctionService = mock[CorrectionService]
  private val coreVatReturnService = mock[CoreVatReturnService]
  private val coreVatReturnConnector = mock[CoreVatReturnConnector]
  private val registrationConnector = mock[RegistrationConnector]
  private val appConfig = mock[AppConfig]
  implicit private lazy val hc: HeaderCarrier = HeaderCarrier()

  override def beforeEach(): Unit = {
    Mockito.reset(coreVatReturnService, coreVatReturnConnector)
  }

  "HistoricalReturnSubmitService#transfer" - {

    "when core vat return feature is enabled" - {

      when(vatReturnService.get()) thenReturn Future.successful(List.empty)
      when(appConfig.coreVatReturnsEnabled) thenReturn true

      val service = new HistoricalReturnSubmitServiceImpl(vatReturnService, correctionService, coreVatReturnService, coreVatReturnConnector, registrationConnector, appConfig, stubClock)

      "successfully transfer all data" in {

        when(vatReturnService.get()) thenReturn Future.successful(List(completeVatReturn))
        when(correctionService.get()) thenReturn Future.successful(List(emptyCorrectionPayload))
        when(coreVatReturnService.toCore(any(), any(), any())) thenReturn Future.successful(coreVatReturn)
        when(coreVatReturnConnector.submit(any())) thenReturn Future.successful(Right())
        when(registrationConnector.getRegistration(any())) thenReturn Future.successful(Some(RegistrationData.registration))

        service.transfer().futureValue mustBe Success()
      }

      "fail when vat return cannot be converted to core format" in {

        val completeVatReturn2 = completeVatReturn.copy(vrn = Vrn("987654321"))
        val completeVatReturn3 = completeVatReturn.copy(vrn = Vrn("987654322"))
        val genericException = new Exception("Conversion error")

        when(vatReturnService.get()) thenReturn Future.successful(List(completeVatReturn, completeVatReturn2, completeVatReturn3))
        when(correctionService.get()) thenReturn Future.successful(List(emptyCorrectionPayload))
        when(coreVatReturnService.toCore(eqTo(completeVatReturn), any(), any())) thenReturn Future.successful(coreVatReturn)
        when(coreVatReturnService.toCore(eqTo(completeVatReturn2), any(), any())) thenReturn Future.failed(genericException)
        when(coreVatReturnService.toCore(eqTo(completeVatReturn3), any(), any())) thenReturn Future.successful(coreVatReturn)
        when(coreVatReturnConnector.submit(any())) thenReturn Future.successful(Right())
        when(registrationConnector.getRegistration(any())) thenReturn Future.successful(Some(RegistrationData.registration))

        service.transfer().futureValue mustBe Failure(genericException)

        verify(coreVatReturnConnector, times(0)).submit(any())
      }

      def getCoreVatReturn(vatReturn: VatReturn) = {
        coreVatReturn.copy(
          vatReturnReferenceNumber = vatReturn.vrn.value,
          period = CorePeriod(vatReturn.period.year, vatReturn.period.quarter.toString.tail.toInt))
      }

      "submit vat returns in order" in {

        val completeVatReturn2 = completeVatReturn.copy(vrn = Vrn("987654321"), period = Period(2086, Q4))
        val completeVatReturn2a = completeVatReturn.copy(vrn = Vrn("987654322"), period = Period(2086, Q4))
        val completeVatReturn3 = completeVatReturn.copy(vrn = Vrn("987654322"), period = Period(2087, Q1))

        val coreVatReturn1 = getCoreVatReturn(completeVatReturn)
        val coreVatReturn2 = getCoreVatReturn(completeVatReturn2)
        val coreVatReturn2a = getCoreVatReturn(completeVatReturn2a)
        val coreVatReturn3 = getCoreVatReturn(completeVatReturn3)

        val emptyCorrectionPayload2: CorrectionPayload =
          CorrectionPayload(
            Vrn("063407423"),
            Period("2086", "Q3").get,
            List.empty,
            Instant.ofEpochSecond(1630670836),
            Instant.ofEpochSecond(1630670836)
          )

        // retrieve vat returns out of order
        when(vatReturnService.get()) thenReturn Future.successful(List(completeVatReturn2,  completeVatReturn, completeVatReturn2a,  completeVatReturn3))
        when(correctionService.get()) thenReturn Future.successful(List(emptyCorrectionPayload2))

        when(coreVatReturnService.toCore(eqTo(completeVatReturn), any(), any())) thenReturn Future.successful(coreVatReturn1)
        when(coreVatReturnService.toCore(eqTo(completeVatReturn2), any(), any())) thenReturn Future.successful(coreVatReturn2)
        when(coreVatReturnService.toCore(eqTo(completeVatReturn2a), any(), any())) thenReturn Future.successful(coreVatReturn2a)
        when(coreVatReturnService.toCore(eqTo(completeVatReturn3), any(), any())) thenReturn Future.successful(coreVatReturn3)

        when(coreVatReturnConnector.submit(any())) thenReturn Future.successful(Right())

        when(registrationConnector.getRegistration(any())) thenReturn Future.successful(Some(RegistrationData.registration))

        service.transfer().futureValue mustBe Success()

        verify(coreVatReturnConnector, times(4)).submit(any())

        // submit vat returns in order
        val inOrder = Mockito.inOrder(coreVatReturnConnector)
        inOrder.verify(coreVatReturnConnector).submit(coreVatReturn1)
        inOrder.verify(coreVatReturnConnector).submit(coreVatReturn2)
        inOrder.verify(coreVatReturnConnector).submit(coreVatReturn2a)
        inOrder.verify(coreVatReturnConnector).submit(coreVatReturn3)

      }

      "stop all submissions on submissions failure" in {

        val completeVatReturn2 = completeVatReturn.copy(vrn = Vrn("987654321"))
        val completeVatReturn3 = completeVatReturn.copy(vrn = Vrn("987654322"))

        val coreVatReturnFail = coreVatReturn.copy(vatReturnReferenceNumber = "987654323")

        val coreErrorResponse = EisErrorResponse(CoreErrorResponse(Instant.now(), None, "ERROR", "Submission error"))

        when(vatReturnService.get()) thenReturn Future.successful(List(completeVatReturn, completeVatReturn2, completeVatReturn3))
        when(correctionService.get()) thenReturn Future.successful(List(emptyCorrectionPayload))
        when(coreVatReturnService.toCore(eqTo(completeVatReturn), any(), any())) thenReturn Future.successful(coreVatReturn)
        when(coreVatReturnService.toCore(eqTo(completeVatReturn2), any(), any())) thenReturn Future.successful(coreVatReturnFail)
        when(coreVatReturnService.toCore(eqTo(completeVatReturn3), any(), any())) thenReturn Future.successful(coreVatReturn)
        when(coreVatReturnConnector.submit(any())) thenReturn Future.successful(Right())
        when(coreVatReturnConnector.submit(eqTo(coreVatReturnFail))) thenReturn Future.successful(Left(coreErrorResponse))
        when(registrationConnector.getRegistration(any())) thenReturn Future.successful(Some(RegistrationData.registration))

        service.transfer().futureValue mustBe Failure(coreErrorResponse.errorDetail.asException)

        verify(coreVatReturnConnector, times(2)).submit(any())
      }

      "fail gracefully when toCore fails" in {
        val conversionException = new Exception("Could not convert to core format")

        when(vatReturnService.get()) thenReturn Future.successful(List(completeVatReturn))
        when(correctionService.get()) thenReturn Future.successful(List(emptyCorrectionPayload))
        when(coreVatReturnService.toCore(eqTo(completeVatReturn), any(), any())) thenReturn Future.failed(conversionException)

        service.transfer().futureValue mustBe Failure(conversionException)
      }

      "fail gracefully when vat return service fails" in {
        val vatReturnsException = new Exception("Could not retrieve returns")

        when(vatReturnService.get()) thenReturn Future.failed(vatReturnsException)

        service.transfer().futureValue mustBe Failure(vatReturnsException)
      }

      "fail gracefully when correction return service fails" in {
        val correctionsException = new Exception("Could not retrieve corrections")

        when(vatReturnService.get()) thenReturn Future.successful(List(completeVatReturn))
        when(correctionService.get()) thenReturn Future.failed(correctionsException)

        service.transfer().futureValue mustBe Failure(correctionsException)
      }
    }

    "when core vat return feature is disabled" - {

      val service = new HistoricalReturnSubmitServiceImpl(vatReturnService, correctionService, coreVatReturnService, coreVatReturnConnector, registrationConnector, appConfig, stubClock)

      "return empty future unit and do nothing" in {
        when(appConfig.coreVatReturnsEnabled) thenReturn false
        service.transfer().futureValue mustBe ()
      }
    }
  }
}

package services

import base.SpecBase
import config.AppConfig
import connectors.{CoreVatReturnConnector, RegistrationConnector}
import models.Quarter.{Q1, Q3, Q4}
import models.core.{CoreErrorResponse, CorePeriod, EisErrorResponse}
import models.corrections.CorrectionPayload
import models.{Period, ReturnReference, StandardPeriod, VatReturn}
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.Mockito
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.BeforeAndAfterEach
import testutils.RegistrationData
import uk.gov.hmrc.domain.Vrn

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

  override def beforeEach(): Unit = {
    Mockito.reset(coreVatReturnService, coreVatReturnConnector)
  }

  val historicalPeriods = Seq(StandardPeriod(2021, Q3), StandardPeriod(2021, Q4))
  "HistoricalReturnSubmitService#transfer" - {

    "when core vat return feature is enabled" - {

      when(vatReturnService.getByPeriods(any()))`thenReturn` Future.successful(List.empty)
      when(appConfig.coreVatReturnsEnabled)`thenReturn` true
      when(appConfig.historicCoreVatReturnsEnabled)`thenReturn` true
      when(appConfig.historicPeriodsToSubmit)`thenReturn` historicalPeriods

      val service = new HistoricalReturnSubmitServiceImpl(vatReturnService, correctionService, coreVatReturnService, coreVatReturnConnector, registrationConnector, appConfig, stubClock)

      "successfully transfer all data" in {

        when(vatReturnService.getByPeriods(any()))`thenReturn` Future.successful(List(completeVatReturn))
        when(correctionService.getByPeriods(any()))`thenReturn` Future.successful(List(emptyCorrectionPayload))
        when(coreVatReturnService.toCore(any(), any(), any()))`thenReturn` Future.successful(coreVatReturn)
        when(coreVatReturnConnector.submit(any()))`thenReturn` Future.successful(Right(()))
        when(registrationConnector.getRegistration(any())(any()))`thenReturn` Future.successful(Some(RegistrationData.registration))

        service.transfer().futureValue mustBe Success(())
      }

      "fail when vat return cannot be converted to core format" in {

        val completeVatReturn2 = completeVatReturn.copy(vrn = Vrn("987654321"))
        val completeVatReturn3 = completeVatReturn.copy(vrn = Vrn("987654322"))
        val genericException = new Exception("Conversion error")

        when(vatReturnService.getByPeriods(any()))`thenReturn` Future.successful(List(completeVatReturn, completeVatReturn2, completeVatReturn3))
        when(correctionService.getByPeriods(any()))`thenReturn` Future.successful(List(emptyCorrectionPayload))
        when(coreVatReturnService.toCore(eqTo(completeVatReturn), any(), any()))`thenReturn` Future.successful(coreVatReturn)
        when(coreVatReturnService.toCore(eqTo(completeVatReturn2), any(), any()))`thenReturn` Future.failed(genericException)
        when(coreVatReturnService.toCore(eqTo(completeVatReturn3), any(), any()))`thenReturn` Future.successful(coreVatReturn)
        when(coreVatReturnConnector.submit(any()))`thenReturn` Future.successful(Right(()))
        when(registrationConnector.getRegistration(any())(any()))`thenReturn` Future.successful(Some(RegistrationData.registration))

        service.transfer().futureValue mustBe Failure(genericException)

        verify(coreVatReturnConnector, times(0)).submit(any())
      }

      def getCoreVatReturn(vatReturn: VatReturn) = {
        coreVatReturn.copy(
          vatReturnReferenceNumber = ReturnReference(vatReturn.vrn, vatReturn.period).value,
          period = CorePeriod(vatReturn.period.year, vatReturn.period.quarter.toString.tail.toInt),
          submissionDateTime = vatReturn.submissionReceived
        )
      }

      "submit vat returns in order" in {

        val completeVatReturn2 = completeVatReturn.copy(vrn = Vrn("987654321"), period = StandardPeriod(2086, Q4), submissionReceived = completeVatReturn.submissionReceived.plus(java.time.Period.ofDays(1)))
        val completeVatReturn2a = completeVatReturn.copy(vrn = Vrn("987654322"), period = StandardPeriod(2086, Q4))
        val completeVatReturn3 = completeVatReturn.copy(vrn = Vrn("987654322"), period = StandardPeriod(2087, Q1))

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
        when(vatReturnService.getByPeriods(any()))`thenReturn` Future.successful(List(completeVatReturn2, completeVatReturn, completeVatReturn2a, completeVatReturn3))
        when(correctionService.getByPeriods(any()))`thenReturn` Future.successful(List(emptyCorrectionPayload2))

        when(coreVatReturnService.toCore(eqTo(completeVatReturn), any(), any()))`thenReturn` Future.successful(coreVatReturn1)
        when(coreVatReturnService.toCore(eqTo(completeVatReturn2), any(), any()))`thenReturn` Future.successful(coreVatReturn2)
        when(coreVatReturnService.toCore(eqTo(completeVatReturn2a), any(), any()))`thenReturn` Future.successful(coreVatReturn2a)
        when(coreVatReturnService.toCore(eqTo(completeVatReturn3), any(), any()))`thenReturn` Future.successful(coreVatReturn3)

        when(coreVatReturnConnector.submit(any()))`thenReturn` Future.successful(Right(()))

        when(registrationConnector.getRegistration(any())(any()))`thenReturn` Future.successful(Some(RegistrationData.registration))

        service.transfer().futureValue mustBe Success(())

        verify(coreVatReturnConnector, times(4)).submit(any())

        // submit vat returns in order
        val inOrder = Mockito.inOrder(coreVatReturnConnector)
        inOrder.verify(coreVatReturnConnector).submit(coreVatReturn1)
        inOrder.verify(coreVatReturnConnector).submit(coreVatReturn2a)
        inOrder.verify(coreVatReturnConnector).submit(coreVatReturn2)
        inOrder.verify(coreVatReturnConnector).submit(coreVatReturn3)

      }

      "submit vat returns with indexes in given range when index filtering is toggled on and start and end indexes are provided" in {

        val completeVatReturn2 = completeVatReturn.copy(vrn = Vrn("987654321"), period = StandardPeriod(2086, Q4), submissionReceived = completeVatReturn.submissionReceived.plus(java.time.Period.ofDays(1)))
        val completeVatReturn2a = completeVatReturn.copy(vrn = Vrn("987654322"), period = StandardPeriod(2086, Q4))
        val completeVatReturn3 = completeVatReturn.copy(vrn = Vrn("987654322"), period = StandardPeriod(2087, Q1))

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
        when(appConfig.historicCoreVatReturnIndexFilteringEnabled)`thenReturn` true
        when(appConfig.historicCoreVatReturnStartIdx)`thenReturn` 0
        when(appConfig.historicCoreVatReturnEndIdx)`thenReturn` 1
        when(vatReturnService.getByPeriods(any()))`thenReturn` Future.successful(List(completeVatReturn2, completeVatReturn, completeVatReturn2a, completeVatReturn3))
        when(correctionService.getByPeriods(any()))`thenReturn` Future.successful(List(emptyCorrectionPayload2))

        when(coreVatReturnService.toCore(eqTo(completeVatReturn), any(), any()))`thenReturn` Future.successful(coreVatReturn1)
        when(coreVatReturnService.toCore(eqTo(completeVatReturn2), any(), any()))`thenReturn` Future.successful(coreVatReturn2)
        when(coreVatReturnService.toCore(eqTo(completeVatReturn2a), any(), any()))`thenReturn` Future.successful(coreVatReturn2a)
        when(coreVatReturnService.toCore(eqTo(completeVatReturn3), any(), any()))`thenReturn` Future.successful(coreVatReturn3)

        when(coreVatReturnConnector.submit(any()))`thenReturn` Future.successful(Right(()))

        when(registrationConnector.getRegistration(any())(any()))`thenReturn` Future.successful(Some(RegistrationData.registration))
        when(appConfig.historicCoreVatReturnIndexesToInclude)`thenReturn` Seq.empty
        when(appConfig.historicCoreVatReturnIndexesToExclude)`thenReturn` Seq.empty

        service.transfer().futureValue mustBe Success(())

        verify(coreVatReturnConnector, times(2)).submit(any())

        // submit vat returns in order
        val inOrder = Mockito.inOrder(coreVatReturnConnector)
        inOrder.verify(coreVatReturnConnector).submit(coreVatReturn1)
        inOrder.verify(coreVatReturnConnector).submit(coreVatReturn2a)

      }

      "submit vat returns with specific indexes when index filtering is toggled on and a list of indexes to include is provided" in {

        val completeVatReturn2 = completeVatReturn.copy(vrn = Vrn("987654321"), period = StandardPeriod(2086, Q4), submissionReceived = completeVatReturn.submissionReceived.plus(java.time.Period.ofDays(1)))
        val completeVatReturn2a = completeVatReturn.copy(vrn = Vrn("987654322"), period = StandardPeriod(2086, Q4))
        val completeVatReturn3 = completeVatReturn.copy(vrn = Vrn("987654322"), period = StandardPeriod(2087, Q1))

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
        when(appConfig.historicCoreVatReturnIndexFilteringEnabled)`thenReturn` true
        when(appConfig.historicCoreVatReturnStartIdx)`thenReturn` 0
        when(appConfig.historicCoreVatReturnEndIdx)`thenReturn` 3
        when(vatReturnService.getByPeriods(any()))`thenReturn` Future.successful(List(completeVatReturn2, completeVatReturn, completeVatReturn2a, completeVatReturn3))
        when(correctionService.getByPeriods(any()))`thenReturn` Future.successful(List(emptyCorrectionPayload2))

        when(coreVatReturnService.toCore(eqTo(completeVatReturn), any(), any()))`thenReturn` Future.successful(coreVatReturn1)
        when(coreVatReturnService.toCore(eqTo(completeVatReturn2), any(), any()))`thenReturn` Future.successful(coreVatReturn2)
        when(coreVatReturnService.toCore(eqTo(completeVatReturn2a), any(), any()))`thenReturn` Future.successful(coreVatReturn2a)
        when(coreVatReturnService.toCore(eqTo(completeVatReturn3), any(), any()))`thenReturn` Future.successful(coreVatReturn3)

        when(coreVatReturnConnector.submit(any()))`thenReturn` Future.successful(Right(()))

        when(registrationConnector.getRegistration(any())(any()))`thenReturn` Future.successful(Some(RegistrationData.registration))
        when(appConfig.historicCoreVatReturnIndexesToInclude)`thenReturn` Seq(0, 2)
        when(appConfig.historicCoreVatReturnIndexesToExclude)`thenReturn` Seq.empty

        service.transfer().futureValue mustBe Success(())

        verify(coreVatReturnConnector, times(2)).submit(any())

        // submit vat returns in order
        val inOrder = Mockito.inOrder(coreVatReturnConnector)
        inOrder.verify(coreVatReturnConnector).submit(coreVatReturn1)
        inOrder.verify(coreVatReturnConnector).submit(coreVatReturn2)

      }

      "submit vat returns with specific indexes when index filtering is toggled on and a list of indexes to exclude is provided" in {

        val completeVatReturn2 = completeVatReturn.copy(vrn = Vrn("987654321"), period = StandardPeriod(2086, Q4), submissionReceived = completeVatReturn.submissionReceived.plus(java.time.Period.ofDays(1)))
        val completeVatReturn2a = completeVatReturn.copy(vrn = Vrn("987654322"), period = StandardPeriod(2086, Q4))
        val completeVatReturn3 = completeVatReturn.copy(vrn = Vrn("987654322"), period = StandardPeriod(2087, Q1))

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
        when(appConfig.historicCoreVatReturnIndexFilteringEnabled)`thenReturn` true
        when(appConfig.historicCoreVatReturnStartIdx)`thenReturn` 0
        when(appConfig.historicCoreVatReturnEndIdx)`thenReturn` 3
        when(vatReturnService.getByPeriods(any()))`thenReturn` Future.successful(List(completeVatReturn2, completeVatReturn, completeVatReturn2a, completeVatReturn3))
        when(correctionService.getByPeriods(any()))`thenReturn` Future.successful(List(emptyCorrectionPayload2))

        when(coreVatReturnService.toCore(eqTo(completeVatReturn), any(), any()))`thenReturn` Future.successful(coreVatReturn1)
        when(coreVatReturnService.toCore(eqTo(completeVatReturn2), any(), any()))`thenReturn` Future.successful(coreVatReturn2)
        when(coreVatReturnService.toCore(eqTo(completeVatReturn2a), any(), any()))`thenReturn` Future.successful(coreVatReturn2a)
        when(coreVatReturnService.toCore(eqTo(completeVatReturn3), any(), any()))`thenReturn` Future.successful(coreVatReturn3)

        when(coreVatReturnConnector.submit(any()))`thenReturn` Future.successful(Right(()))

        when(registrationConnector.getRegistration(any())(any()))`thenReturn` Future.successful(Some(RegistrationData.registration))
        when(appConfig.historicCoreVatReturnIndexesToInclude)`thenReturn` Seq.empty
        when(appConfig.historicCoreVatReturnIndexesToExclude)`thenReturn` Seq(0, 1)

        service.transfer().futureValue mustBe Success(())

        verify(coreVatReturnConnector, times(2)).submit(any())

        // submit vat returns in order
        val inOrder = Mockito.inOrder(coreVatReturnConnector)
        inOrder.verify(coreVatReturnConnector).submit(coreVatReturn2)
        inOrder.verify(coreVatReturnConnector).submit(coreVatReturn3)

      }

      "submit vat returns with specific references when reference filtering is toggled on" in {

        val completeVatReturn2 = completeVatReturn.copy(vrn = Vrn("987654321"), period = StandardPeriod(2086, Q4), submissionReceived = completeVatReturn.submissionReceived.plus(java.time.Period.ofDays(1)), reference = ReturnReference(Vrn("987654321"), StandardPeriod(2086, Q4)))
        val completeVatReturn2a = completeVatReturn.copy(vrn = Vrn("987654322"), period = StandardPeriod(2086, Q4), reference = ReturnReference(Vrn("987654322"), StandardPeriod(2086, Q4)))
        val completeVatReturn3 = completeVatReturn.copy(vrn = Vrn("987654322"), period = StandardPeriod(2087, Q1), reference = ReturnReference(Vrn("987654322"), StandardPeriod(2087, Q1)))

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
        when(appConfig.historicCoreVatReturnIndexFilteringEnabled)`thenReturn` false
        when(appConfig.historicCoreVatReturnReferencesEnabled)`thenReturn` true
        when(appConfig.historicCoreVatReturnReferences)`thenReturn` Seq("XI/XI987654322/Q4.2086", "XI/XI987654322/Q1.2087")
        when(vatReturnService.getByPeriods(any()))`thenReturn` Future.successful(List(completeVatReturn2, completeVatReturn, completeVatReturn2a, completeVatReturn3))
        when(correctionService.getByPeriods(any()))`thenReturn` Future.successful(List(emptyCorrectionPayload2))

        when(coreVatReturnService.toCore(eqTo(completeVatReturn), any(), any()))`thenReturn` Future.successful(coreVatReturn1)
        when(coreVatReturnService.toCore(eqTo(completeVatReturn2), any(), any()))`thenReturn` Future.successful(coreVatReturn2)
        when(coreVatReturnService.toCore(eqTo(completeVatReturn2a), any(), any()))`thenReturn` Future.successful(coreVatReturn2a)
        when(coreVatReturnService.toCore(eqTo(completeVatReturn3), any(), any()))`thenReturn` Future.successful(coreVatReturn3)

        when(coreVatReturnConnector.submit(any()))`thenReturn` Future.successful(Right(()))

        when(registrationConnector.getRegistration(any())(any()))`thenReturn` Future.successful(Some(RegistrationData.registration))
        when(appConfig.historicCoreVatReturnIndexesToInclude)`thenReturn` Seq.empty
        when(appConfig.historicCoreVatReturnIndexesToExclude)`thenReturn` Seq.empty

        service.transfer().futureValue mustBe Success(())

        verify(coreVatReturnConnector, times(2)).submit(any())

        // submit vat returns in order
        val inOrder = Mockito.inOrder(coreVatReturnConnector)
        inOrder.verify(coreVatReturnConnector).submit(coreVatReturn2a)
        inOrder.verify(coreVatReturnConnector).submit(coreVatReturn3)

      }

      "stop all submissions on submissions failure" in {

        val completeVatReturn2 = completeVatReturn.copy(vrn = Vrn("987654321"))
        val completeVatReturn3 = completeVatReturn.copy(vrn = Vrn("987654322"))

        val coreVatReturnFail = coreVatReturn.copy(vatReturnReferenceNumber = "987654323")

        val coreErrorResponse = EisErrorResponse(CoreErrorResponse(Instant.now(), None, "ERROR", "Submission error"))

        when(appConfig.historicCoreVatReturnReferencesEnabled)`thenReturn` false
        when(vatReturnService.getByPeriods(any()))`thenReturn` Future.successful(List(completeVatReturn, completeVatReturn2, completeVatReturn3))
        when(correctionService.getByPeriods(any()))`thenReturn` Future.successful(List(emptyCorrectionPayload))
        when(coreVatReturnService.toCore(eqTo(completeVatReturn), any(), any()))`thenReturn` Future.successful(coreVatReturn)
        when(coreVatReturnService.toCore(eqTo(completeVatReturn2), any(), any()))`thenReturn` Future.successful(coreVatReturnFail)
        when(coreVatReturnService.toCore(eqTo(completeVatReturn3), any(), any()))`thenReturn` Future.successful(coreVatReturn)
        when(coreVatReturnConnector.submit(any()))`thenReturn` Future.successful(Right(()))
        when(coreVatReturnConnector.submit(eqTo(coreVatReturnFail)))`thenReturn` Future.successful(Left(coreErrorResponse))
        when(registrationConnector.getRegistration(any())(any()))`thenReturn` Future.successful(Some(RegistrationData.registration))
        when(appConfig.historicCoreVatReturnIndexesToInclude)`thenReturn` Seq.empty
        when(appConfig.historicCoreVatReturnIndexesToExclude)`thenReturn` Seq.empty

        service.transfer().futureValue mustBe Failure(coreErrorResponse.errorDetail.asException)

        verify(coreVatReturnConnector, times(2)).submit(any())
      }

      "fail gracefully when toCore fails" in {
        val conversionException = new Exception("Could not convert to core format")

        when(vatReturnService.getByPeriods(any()))`thenReturn` Future.successful(List(completeVatReturn))
        when(correctionService.getByPeriods(any()))`thenReturn` Future.successful(List(emptyCorrectionPayload))
        when(coreVatReturnService.toCore(eqTo(completeVatReturn), any(), any()))`thenReturn` Future.failed(conversionException)

        service.transfer().futureValue mustBe Failure(conversionException)
      }

      "fail gracefully when vat return service fails" in {
        val vatReturnsException = new Exception("Could not retrieve returns")

        when(vatReturnService.getByPeriods(any()))`thenReturn` Future.failed(vatReturnsException)

        service.transfer().futureValue mustBe Failure(vatReturnsException)
      }

      "fail gracefully when correction return service fails" in {
        val correctionsException = new Exception("Could not retrieve corrections")

        when(vatReturnService.getByPeriods(any()))`thenReturn` Future.successful(List(completeVatReturn))
        when(correctionService.getByPeriods(any()))`thenReturn` Future.failed(correctionsException)

        service.transfer().futureValue mustBe Failure(correctionsException)
      }
    }

    "when historic core vat return feature is disabled" - {

      val service = new HistoricalReturnSubmitServiceImpl(vatReturnService, correctionService, coreVatReturnService, coreVatReturnConnector, registrationConnector, appConfig, stubClock)

      "return empty future unit and do nothing" in {
        when(appConfig.historicCoreVatReturnsEnabled)`thenReturn` false
        service.transfer().futureValue.mustBe(())
      }
    }
  }
}

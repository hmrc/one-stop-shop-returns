package services

import base.SpecBase
import config.AppConfig
import connectors.{CoreVatReturnConnector, RegistrationConnector}
import models._
import models.core._
import models.corrections.{CorrectionPayload, CorrectionToCountry, PeriodWithCorrections}
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchersSugar.eqTo
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.BeforeAndAfterEach
import testutils.RegistrationData
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.math.BigDecimal.RoundingMode
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

    when(vatReturnService.get()) thenReturn Future.successful(List.empty)
    when(appConfig.coreVatReturnsEnabled) thenReturn true

    val service = new HistoricalReturnSubmitServiceImpl(vatReturnService, correctionService, coreVatReturnService, coreVatReturnConnector, registrationConnector, appConfig, stubClock)

    "successfully transfer all data" in {
      when(vatReturnService.get()) thenReturn Future.successful(List(completeVatReturn))
      when(correctionService.get()) thenReturn Future.successful(List(emptyCorrectionPayload))
      when(coreVatReturnService.toCore(any(), any(), any())) thenReturn Future.successful(coreVatReturn)
      when(coreVatReturnConnector.submit(any())) thenReturn Future.successful(Right())
      when(registrationConnector.getRegistration(any())) thenReturn Future.successful(Some(RegistrationData.registration))

      service.transfer().futureValue mustBe Seq(Success(Right()))
    }

    "don't fail future when partly fails" in {
      val completeVatReturn2 = completeVatReturn.copy(vrn = Vrn("987654321"))
      val completeVatReturn3 = completeVatReturn.copy(vrn = Vrn("987654322"))
      val genericException = new Exception("Error")

      when(vatReturnService.get()) thenReturn Future.successful(List(completeVatReturn, completeVatReturn2, completeVatReturn3))
      when(correctionService.get()) thenReturn Future.successful(List(emptyCorrectionPayload))
      when(coreVatReturnService.toCore(eqTo(completeVatReturn), any(), any())) thenReturn Future.successful(coreVatReturn)
      when(coreVatReturnService.toCore(eqTo(completeVatReturn2), any(), any())) thenReturn Future.failed(genericException)
      when(coreVatReturnService.toCore(eqTo(completeVatReturn3), any(), any())) thenReturn Future.successful(coreVatReturn)
      when(coreVatReturnConnector.submit(any())) thenReturn Future.successful(Right())
      when(registrationConnector.getRegistration(any())) thenReturn Future.successful(Some(RegistrationData.registration))

      service.transfer().futureValue mustBe Seq(Success(Right()), Failure(genericException), Success(Right()))
    }

  }

}

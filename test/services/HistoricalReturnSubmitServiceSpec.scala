package services

import base.SpecBase
import connectors.{CoreVatReturnConnector, RegistrationConnector}
import models._
import models.core._
import models.corrections.{CorrectionPayload, CorrectionToCountry, PeriodWithCorrections}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.BeforeAndAfterEach
import testutils.RegistrationData
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.math.BigDecimal.RoundingMode

class HistoricalReturnSubmitServiceSpec extends SpecBase with BeforeAndAfterEach {

  private val vatReturnService = mock[VatReturnService]
  private val correctionService = mock[CorrectionService]
  private val coreVatReturnService = mock[CoreVatReturnService]
  private val coreVatReturnConnector = mock[CoreVatReturnConnector]
  private val service = new HistoricalReturnSubmitService(vatReturnService, correctionService, coreVatReturnService, coreVatReturnConnector, stubClock)
  implicit private lazy val hc: HeaderCarrier = HeaderCarrier()

  override def beforeEach(): Unit = {
    Mockito.reset(coreVatReturnService, coreVatReturnConnector)
  }

  "HistoricalReturnSubmitService#transfer" - {

    "successfully transfer all data" in {
      when(vatReturnService.get()) thenReturn Future.successful(List(completeVatReturn))
      when(correctionService.get()) thenReturn Future.successful(List(emptyCorrectionPayload))
      when(coreVatReturnService.toCore(any(), any())(any())) thenReturn Future.successful(coreVatReturn)
      when(coreVatReturnConnector.submit(any())) thenReturn Future.successful(Right())

      service.transfer().futureValue mustBe List(Right())
    }

  }

}

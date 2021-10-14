package services

import connectors.FinancialDataConnector
import generators.Generators
import models.Period
import models.financialdata.{FinancialDataQueryParameters, FinancialDataResponse, FinancialTransaction, Item}
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.mockito.ArgumentMatchers.{any, eq => equalTo}
import org.mockito.Mockito.when
import uk.gov.hmrc.domain.Vrn

import java.time.{LocalDate, ZonedDateTime}
import scala.concurrent.Future

class FinancialDataServiceSpec extends AnyFreeSpec
  with Matchers
  with MockitoSugar
  with ScalaCheckPropertyChecks
  with Generators
  with OptionValues
  with ScalaFutures {

  "getFinancialData" - {

    val items = Seq(
      Item(
        amount = None,
        clearingReason = None,
        paymentReference = None,
        paymentAmount = None,
        paymentMethod = None
      )
    )

    val financialTransactions = Seq(
      FinancialTransaction(
        chargeType = None,
        mainType = None,
        taxPeriodFrom = Some(LocalDate.of(2021, 7, 1)),
        taxPeriodTo = Some(LocalDate.of(2021, 9, 30)),
        originalAmount = None,
        outstandingAmount = None,
        clearedAmount = None,
        items = Some(items)
      )
    )

    "when commencement date is in Q3 2021" in {
      val commencementDate = LocalDate.of(2021, 9, 1)
      val connector = mock[FinancialDataConnector]
      val service = new FinancialDataService(connector)
      val queryParameters = FinancialDataQueryParameters(fromDate = Some(commencementDate), toDate = Some(LocalDate.now()))

      when(connector.getFinancialData(any(), equalTo(queryParameters))) thenReturn(
        Future.successful(Some(FinancialDataResponse(Some("VRN"), Some("123456789"), Some("?"), ZonedDateTime.now(), Option(financialTransactions)))))

      val response = service.getFinancialData(Vrn("123456789"), commencementDate).futureValue

      response.isDefined mustBe true
      val financialData = response.get
      financialData.idType mustBe Some("VRN")
    }
  }
}

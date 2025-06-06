package base

import controllers.actions.{AuthAction, FakeAuthAction}
import generators.Generators
import models.VatOnSalesChoice.Standard
import models._
import models.core._
import models.corrections.CorrectionPayload
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.Vrn

import java.time.{Clock, Instant, LocalDate, LocalDateTime, ZoneId}

trait SpecBase
  extends AnyFreeSpec
    with Matchers
    with TryValues
    with OptionValues
    with ScalaFutures
    with IntegrationPatience
    with MockitoSugar
    with Generators {

  protected val vrn: Vrn = Vrn("123456789")

  def period: Period = StandardPeriod(2021, Quarter.Q3)

  val userAnswersId: String = "12345-credId"
  val testCredentials: Credentials = Credentials(userAnswersId, "GGW")

  val commencementDate: LocalDate = LocalDate.of(2021, 7, 1)

  val completeVatReturn: VatReturn =
    VatReturn(
      Vrn("063407423"),
      Period("2086", "Q3").get,
      ReturnReference(Vrn("063407423"), StandardPeriod(2086, Quarter.Q3)),
      PaymentReference(Vrn("063407423"), StandardPeriod(2086, Quarter.Q3)),
      None,
      None,
      List(SalesToCountry(Country("LT",
        "Lithuania"),
        List(SalesDetails(VatRate(45.54,
          VatRateType.Reduced),
          306338.71,
          VatOnSales(Standard, 230899.32)),
          SalesDetails(VatRate(98.54,
            VatRateType.Reduced),
            295985.50,
            VatOnSales(Standard, 319051.84)))),
        SalesToCountry(Country("MT",
          "Malta"),
          List(SalesDetails(VatRate(80.28,
            VatRateType.Standard),
            357873.00,
            VatOnSales(Standard, 191855.64))))),
      List(SalesFromEuCountry(Country("DE", "Germany"),
        Some(EuTaxIdentifier(EuTaxIdentifierType.Vat, "-1")),
        List(SalesToCountry(Country("FI",
          "Finland"),
          List(SalesDetails(VatRate(56.02,
            VatRateType.Standard),
            543742.51,
            VatOnSales(Standard, 801143.05)))))),
        SalesFromEuCountry(Country("IE",
          "Ireland"),
          Some(EuTaxIdentifier(EuTaxIdentifierType.Other, "-2147483648")),
          List(SalesToCountry(Country("CY",
            "Cyprus"),
            List(SalesDetails(VatRate(98.97,
              VatRateType.Reduced),
              356270.07,
              VatOnSales(Standard, 24080.60)),
              SalesDetails(VatRate(98.92,
                VatRateType.Reduced),
                122792.32,
                VatOnSales(Standard, 554583.78))))))),
      Instant.ofEpochSecond(1630670836),
      Instant.ofEpochSecond(1630670836))

  val emptyVatReturn: VatReturn =
    VatReturn(
      Vrn("063407423"),
      Period("2086", "Q3").get,
      ReturnReference(Vrn("063407423"), StandardPeriod(2086, Quarter.Q3)),
      PaymentReference(Vrn("063407423"), StandardPeriod(2086, Quarter.Q3)),
      None,
      None,
      List.empty,
      List.empty,
      Instant.ofEpochSecond(1630670836),
      Instant.ofEpochSecond(1630670836)
    )

  val emptyCorrectionPayload: CorrectionPayload =
    CorrectionPayload(
      Vrn("063407423"),
      Period("2086", "Q3").get,
      List.empty,
      Instant.ofEpochSecond(1630670836),
      Instant.ofEpochSecond(1630670836)
    )

  val stubClock: Clock = Clock.fixed(LocalDate.now.atStartOfDay(ZoneId.systemDefault).toInstant, ZoneId.systemDefault)

  val coreVatReturn: CoreVatReturn = CoreVatReturn(
    vatReturnReferenceNumber = completeVatReturn.reference.value,
    version = completeVatReturn.lastUpdated,
    traderId = CoreTraderId(vrn.vrn, "XI"),
    period = CorePeriod(2021, 3),
    startDate = LocalDate.now(stubClock),
    endDate = LocalDate.now(stubClock),
    submissionDateTime = Instant.now(stubClock),
    totalAmountVatDueGBP = BigDecimal(10),
    msconSupplies = List(CoreMsconSupply(
      "DE",
      BigDecimal(10),
      BigDecimal(10),
      BigDecimal(10),
      BigDecimal(-10),
      List(CoreSupply(
        "GOODS",
        BigDecimal(10),
        "STANDARD",
        BigDecimal(10),
        BigDecimal(10)
      )),
      List(CoreMsestSupply(
        Some("FR"),
        None,
        List(CoreSupply(
          "GOODS",
          BigDecimal(10),
          "STANDARD",
          BigDecimal(10),
          BigDecimal(10)
        ))
      )),
      List(CoreCorrection(
        CorePeriod(2021, 2),
        BigDecimal(-10)
      ))
    )),
    changeDate = Some(LocalDateTime.now(stubClock))
  )

  protected def applicationBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .overrides(
        bind[AuthAction].to[FakeAuthAction]
      )
}

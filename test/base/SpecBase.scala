package base

import controllers.actions.{AuthAction, CheckCorrectionsToggleFilterProvider, FakeAuthAction, FakeCheckCorrectionsToggleFilterProvider}
import org.scalatest.{OptionValues, TryValues}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.domain.Vrn

import java.time.{Clock, LocalDate, ZoneId}

trait SpecBase
  extends AnyFreeSpec
    with Matchers
    with TryValues
    with OptionValues
    with ScalaFutures
    with IntegrationPatience
    with MockitoSugar {

  protected val vrn: Vrn = Vrn("123456789")

  val stubClock: Clock = Clock.fixed(LocalDate.now.atStartOfDay(ZoneId.systemDefault).toInstant, ZoneId.systemDefault)

  protected def applicationBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .overrides(
        bind[AuthAction].to[FakeAuthAction],
        bind[CheckCorrectionsToggleFilterProvider].toInstance(new FakeCheckCorrectionsToggleFilterProvider())
      )
}

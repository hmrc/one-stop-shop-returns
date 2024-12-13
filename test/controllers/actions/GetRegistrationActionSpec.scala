package controllers.actions

import base.SpecBase
import connectors.RegistrationConnector
import models.requests.RegistrationRequest
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.Mockito.when
import org.scalatest.EitherValues
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.Result
import play.api.mvc.Results._
import play.api.test.FakeRequest
import testutils.RegistrationData
import uk.gov.hmrc.domain.Vrn

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class GetRegistrationActionSpec extends SpecBase with MockitoSugar with EitherValues {

  class Harness(
                 vrn: Option[String],
                 connector: RegistrationConnector
               ) extends GetRegistrationAction(vrn, connector) {
    def callRefine[A](request: AuthorisedRequest[A]): Future[Either[Result, RegistrationRequest[A]]] =
      refine(request)
  }

  "Get Registration Action" - {

    "when there is a registration returned" - {

      "and when we pass a vrn as a parameter" - {

        "must return Right" in {

          val request = FakeRequest()
          val connector = mock[RegistrationConnector]
          when(connector.getRegistration(eqTo(vrn))(any())) `thenReturn` Future.successful(Some(RegistrationData.registration))
          val action = new Harness(Some(vrn.vrn), connector)

          val result = action.callRefine(AuthorisedRequest(request, userAnswersId, vrn)).futureValue

          result.isRight mustEqual true
        }
      }

      "and when we don't pass a vrn as a parameter" - {

        "must return Right" in {

          val request = FakeRequest()
          val connector = mock[RegistrationConnector]
          when(connector.getRegistration(eqTo(vrn))(any())) `thenReturn` Future.successful(Some(RegistrationData.registration))
          val action = new Harness(None, connector)

          val result = action.callRefine(AuthorisedRequest(request, userAnswersId, vrn)).futureValue

          result.isRight mustEqual true
        }
      }

      "must return Left Unauthorised" in {

        val request = FakeRequest()
        val connector  = mock[RegistrationConnector]
        when(connector.getRegistration(eqTo(vrn))(any())) `thenReturn` Future.successful(Some(RegistrationData.registration))

        val action = new Harness(Some(vrn.vrn), connector)

        val result = action.callRefine(AuthorisedRequest(request, userAnswersId, Vrn("987654321"))).futureValue

        result.left.value mustEqual Unauthorized("VRNs do not match")
      }
    }

    "when there is no registration returned" - {

      "and when we pass a vrn as a parameter" - {

        "must return Right" in {

          val request = FakeRequest()
          val connector = mock[RegistrationConnector]
          when(connector.getRegistration(eqTo(vrn))(any())) `thenReturn` Future.successful(None)
          val action = new Harness(Some(vrn.vrn), connector)

          val result = action.callRefine(AuthorisedRequest(request, userAnswersId, vrn)).futureValue

          result.left.value mustEqual NotFound("Not found registration")
        }
      }

      "and when we don't pass a vrn as a parameter" - {

        "must return Right" in {

          val request = FakeRequest()
          val connector = mock[RegistrationConnector]
          when(connector.getRegistration(eqTo(vrn))(any())) `thenReturn` Future.successful(None)
          val action = new Harness(None, connector)

          val result = action.callRefine(AuthorisedRequest(request, userAnswersId, vrn)).futureValue

          result.left.value mustEqual NotFound("Not found registration")
        }
      }

    }
  }
}
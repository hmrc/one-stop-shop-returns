package controllers.actions

import base.SpecBase
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.{Action, AnyContent, BodyParsers, Results}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, EnrolmentIdentifier, Enrolments, MissingBearerToken}
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, ~}
import TestAuthRetrievals._
import com.google.inject.Inject
import config.AppConfig
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class AuthActionSpec extends SpecBase with BeforeAndAfterEach {

private type RetrievalsType = Option[String] ~ Enrolments

  private val vatEnrolment = Enrolments(Set(Enrolment("HMRC-MTD-VAT", Seq(EnrolmentIdentifier("VRN", "123456789")), "Activated")))
  private val vatAndOssEnrolment = Enrolments(Set(Enrolment("HMRC-MTD-VAT", Seq(EnrolmentIdentifier("VRN", "123456789")), "Activated"), Enrolment("HMRC-OSS-ORG", Seq(EnrolmentIdentifier("VRN", "123456789")), "Activated")))
  private val ossEnrolment = Enrolments(Set(Enrolment("HMRC-OSS-ORG", Seq(EnrolmentIdentifier("VRN", "123456789")), "Activated")))

  class Harness(authAction: AuthAction) {
    def onPageLoad(): Action[AnyContent] = authAction { _ => Results.Ok }
  }

  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  override def beforeEach(): Unit = {
    Mockito.reset(mockAuthConnector)
  }

  "Auth action" - {

    "when enrolments is enabled" - {
      "when the user is logged in and has a VAT enrolment and OSS enrolment" - {

        "must succeed" in {

          val application = applicationBuilder
            .configure(
              "features.oss-enrolment" -> true,
              "oss-enrolment" -> "HMRC-OSS-ORG"
            )
            .build()

          running(application) {
            val bodyParsers = application.injector.instanceOf[BodyParsers.Default]

            when(mockAuthConnector.authorise[RetrievalsType](any(), any())(any(), any()))
              .thenReturn(Future.successful(Some("id") ~ vatAndOssEnrolment))

            val action = new AuthActionImpl(mockAuthConnector, bodyParsers, application.injector.instanceOf[AppConfig])
            val controller = new Harness(action)
            val result = controller.onPageLoad()(FakeRequest())

            status(result) mustEqual OK
          }
        }
      }

      "when the user is logged in without a VAT enrolment nor OSS enrolment" - {

        "must return Unauthorized" in {

          val application = applicationBuilder
            .configure(
            "features.oss-enrolment" -> true,
            "oss-enrolment" -> "HMRC-OSS-ORG"
          ).build()

          running(application) {
            val bodyParsers = application.injector.instanceOf[BodyParsers.Default]

            when(mockAuthConnector.authorise[RetrievalsType](any(), any())(any(), any()))
              .thenReturn(Future.successful(Some("id") ~ Enrolments(Set.empty)))

            val action = new AuthActionImpl(mockAuthConnector, bodyParsers, application.injector.instanceOf[AppConfig])
            val controller = new Harness(action)
            val result = controller.onPageLoad()(FakeRequest())

            status(result) mustEqual UNAUTHORIZED
          }
        }
      }

      "when the user is logged in without a VAT enrolment with OSS enrolment" - {

        "must return Unauthorized" in {

          val application = applicationBuilder
            .configure(
            "features.oss-enrolment" -> true,
            "oss-enrolment" -> "HMRC-OSS-ORG"
          ).build()

          running(application) {
            val bodyParsers = application.injector.instanceOf[BodyParsers.Default]

            when(mockAuthConnector.authorise[RetrievalsType](any(), any())(any(), any()))
              .thenReturn(Future.successful(Some("id") ~ ossEnrolment))

            val action = new AuthActionImpl(mockAuthConnector, bodyParsers, application.injector.instanceOf[AppConfig])
            val controller = new Harness(action)
            val result = controller.onPageLoad()(FakeRequest())

            status(result) mustEqual UNAUTHORIZED
          }
        }
      }

      "when the user is logged in without a OSS enrolment with VAT enrolment" - {

        "must return Unauthorized" in {

          val application = applicationBuilder
            .configure(
            "features.oss-enrolment" -> true,
            "oss-enrolment" -> "HMRC-OSS-ORG"
          ).build()

          running(application) {
            val bodyParsers = application.injector.instanceOf[BodyParsers.Default]

            when(mockAuthConnector.authorise[RetrievalsType](any(), any())(any(), any()))
              .thenReturn(Future.successful(Some("id") ~ vatEnrolment))

            val action = new AuthActionImpl(mockAuthConnector, bodyParsers, application.injector.instanceOf[AppConfig])
            val controller = new Harness(action)
            val result = controller.onPageLoad()(FakeRequest())

            status(result) mustEqual UNAUTHORIZED
          }
        }
      }

      "when the user is not logged in" - {

        "must return Unauthorized" in {

          val application = applicationBuilder
            .configure(
            "features.oss-enrolment" -> true,
            "oss-enrolment" -> "HMRC-OSS-ORG"
          ).build()

          running(application) {
            val bodyParsers = application.injector.instanceOf[BodyParsers.Default]

            val authAction = new AuthActionImpl(new FakeFailingAuthConnector(new MissingBearerToken), bodyParsers, application.injector.instanceOf[AppConfig])
            val controller = new Harness(authAction)
            val result = controller.onPageLoad()(FakeRequest())

            status(result) mustBe UNAUTHORIZED
          }
        }
      }
    }

    "when enrolments is not enabled" - {
      "when the user is logged in and has a VAT enrolment" - {

        "must succeed" in {

          val application = applicationBuilder
            .configure(
            "features.oss-enrolment" -> false,
            "oss-enrolment" -> "HMRC-OSS-ORG"
          ).build()

          running(application) {
            val bodyParsers = application.injector.instanceOf[BodyParsers.Default]

            when(mockAuthConnector.authorise[RetrievalsType](any(), any())(any(), any()))
              .thenReturn(Future.successful(Some("id") ~ vatEnrolment))

            val action = new AuthActionImpl(mockAuthConnector, bodyParsers, application.injector.instanceOf[AppConfig])
            val controller = new Harness(action)
            val result = controller.onPageLoad()(FakeRequest())

            status(result) mustEqual OK
          }
        }
      }

      "when the user is logged in without a VAT enrolment" - {

        "must return Unauthorized" in {

          val application = applicationBuilder.configure(
            "features.oss-enrolment" -> false,
            "oss-enrolment" -> "HMRC-OSS-ORG"
          ).build()

          running(application) {
            val bodyParsers = application.injector.instanceOf[BodyParsers.Default]

            when(mockAuthConnector.authorise[RetrievalsType](any(), any())(any(), any()))
              .thenReturn(Future.successful(Some("id") ~ Enrolments(Set.empty)))

            val action = new AuthActionImpl(mockAuthConnector, bodyParsers, application.injector.instanceOf[AppConfig])
            val controller = new Harness(action)
            val result = controller.onPageLoad()(FakeRequest())

            status(result) mustEqual UNAUTHORIZED
          }
        }
      }

      "when the user is not logged in" - {

        "must return Unauthorized" in {

          val application = applicationBuilder.configure(
            "features.oss-enrolment" -> false,
            "oss-enrolment" -> "HMRC-OSS-ORG"
          ).build()

          running(application) {
            val bodyParsers = application.injector.instanceOf[BodyParsers.Default]

            val authAction = new AuthActionImpl(new FakeFailingAuthConnector(new MissingBearerToken), bodyParsers, application.injector.instanceOf[AppConfig])
            val controller = new Harness(authAction)
            val result = controller.onPageLoad()(FakeRequest())

            status(result) mustBe UNAUTHORIZED
          }
        }
      }
    }


  }
}

class FakeFailingAuthConnector @Inject()(exceptionToReturn: Throwable) extends AuthConnector {
  val serviceUrl: String = ""

  override def authorise[A](predicate: Predicate, retrieval: Retrieval[A])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] =
    Future.failed(exceptionToReturn)
}

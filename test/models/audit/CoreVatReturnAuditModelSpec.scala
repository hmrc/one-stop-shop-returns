package models.audit

import base.SpecBase
import controllers.actions.AuthorisedRequest
import generators.Generators
import models.core.CoreErrorResponse
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.Json
import play.api.mvc.AnyContent
import play.api.test.FakeRequest
import uk.gov.hmrc.domain.Vrn

import java.time.Instant
import java.util.UUID

class CoreVatReturnAuditModelSpec extends SpecBase with Matchers with ScalaCheckPropertyChecks with Generators {

  implicit private lazy val request: AuthorisedRequest[AnyContent] = AuthorisedRequest(FakeRequest(), userAnswersId, Vrn("123456789"))

  "CoreVatReturnAuditModel" - {

    "must create correct json object" - {

      "when result is success" in {

        val coreVatReturnAuditModel = CoreVatReturnAuditModel.build(coreVatReturn, SubmissionResult.Success, None)

        val expectedJson = Json.obj(
          "userId" -> request.userId,
          "browserUserAgent" -> "",
          "requestersVrn" -> request.vrn.vrn,
          "coreVatReturn" -> coreVatReturn,
          "submissionResult" -> SubmissionResult.Success.toString
        )
        coreVatReturnAuditModel.detail mustEqual expectedJson
      }

      "when result is failure" in {

        val coreErrorResponse = CoreErrorResponse(Instant.now(), Some(UUID.randomUUID()), "OSS_009", "Registration Not Found")

        val coreVatReturnAuditModel = CoreVatReturnAuditModel.build(coreVatReturn, SubmissionResult.Failure, Some(coreErrorResponse))

        val expectedJson = Json.obj(
          "userId" -> request.userId,
          "browserUserAgent" -> "",
          "requestersVrn" -> request.vrn.vrn,
          "coreVatReturn" -> coreVatReturn,
          "submissionResult" -> SubmissionResult.Failure.toString,
          "coreErrorResponse" -> coreErrorResponse
        )
        coreVatReturnAuditModel.detail mustEqual expectedJson
      }
    }
  }
}

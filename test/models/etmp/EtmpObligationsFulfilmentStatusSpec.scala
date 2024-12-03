package models.etmp

import base.SpecBase
import org.scalacheck.{Arbitrary, Gen}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.{JsError, JsString, Json}

class EtmpObligationsFulfilmentStatusSpec extends SpecBase with ScalaCheckPropertyChecks {

  "EtmpObligationsFulfilmentStatus" - {

    "must deserialise valid values" in {

      val gen = Gen.oneOf(EtmpObligationsFulfilmentStatus.values)

      forAll(gen) {
        obligationFulfilmentStatus =>

          JsString(obligationFulfilmentStatus.toString)
            .validate[EtmpObligationsFulfilmentStatus].asOpt.value mustBe obligationFulfilmentStatus
      }
    }

    "must fail to deserialise invalid values" in {

      val gen = Arbitrary.arbitrary[String].suchThat(!EtmpObligationsFulfilmentStatus.values.map(_.toString).contains(_))

      forAll(gen) {
        invalidValues =>

          JsString(invalidValues).validate[EtmpObligationsFulfilmentStatus] mustBe JsError("error.invalid")
      }
    }

    "must serialise" in {

      val gen = Gen.oneOf(EtmpObligationsFulfilmentStatus.values)

      forAll(gen) {
        obligationFulfilmentStatus =>

          Json.toJson(obligationFulfilmentStatus) mustBe JsString(obligationFulfilmentStatus.toString)
      }
    }
  }

}

/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers.actions

import base.SpecBase
import config.AppConfig
import controllers.routes
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.Result
import play.api.mvc.Results.{NotFound, Redirect}
import play.api.test.FakeRequest
import play.api.test.Helpers.running

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CheckCorrectionsToggleFilterImplSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach {

  val mockAppConfig: AppConfig = mock[AppConfig]

  class Harness() extends CheckCorrectionsToggleFilterImpl(mockAppConfig) {
    def callFilter(request: AuthorisedRequest[_]): Future[Option[Result]] = filter(request)
  }

  override def beforeEach(): Unit = {
    Mockito.reset(mockAppConfig)
  }

  ".filter" - {

    "must return None when toggle on" in {

      val app = applicationBuilder
        .build()

      when(mockAppConfig.correctionToggle).thenReturn(true)

      running(app) {
        val request = AuthorisedRequest(FakeRequest(), "id", vrn)
        val controller = new Harness()
        val result = controller.callFilter(request).futureValue
        result must not be defined
      }
    }

    "must return not found when toggle off" in {

      val app = applicationBuilder
        .build()

      when(mockAppConfig.correctionToggle).thenReturn(false)

      running(app) {
        val request = AuthorisedRequest(FakeRequest(), "id", vrn)
        val controller = new Harness()
        val result = controller.callFilter(request).futureValue
        result.value mustEqual NotFound
      }
    }
  }
}

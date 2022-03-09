/*
 * Copyright 2022 HM Revenue & Customs
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

package controllers.test
import org.mongodb.scala.model.Filters
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repositories.{CorrectionRepository, VatReturnRepository}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class TestOnlyController @Inject()(
                               cc: ControllerComponents,
                               correctionRepository: CorrectionRepository,
                               vatReturnRepository: VatReturnRepository)(implicit ec: ExecutionContext)
  extends BackendController(cc) {

  def deleteAccounts(): Action[AnyContent] = Action.async {

    val vrnPattern = "^1110".r

    for {
      res1 <- correctionRepository.collection.deleteMany(Filters.regex("vrn", vrnPattern)).toFutureOption()
      res2 <- vatReturnRepository.collection.deleteMany(Filters.regex("vrn", vrnPattern)).toFutureOption()
    } yield {
      Ok("Deleted Perf Tests Accounts MongoDB")
    }

  }
}
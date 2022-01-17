package controllers.test
import org.mongodb.scala.model.Filters
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repositories.{CorrectionRepository, VatReturnRepository}
import uk.gov.hmrc.mongo.play.json.Codecs.toBson
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.ExecutionContext

@Singleton
class TestOnlyController @Inject()(
                               cc: ControllerComponents,
                               correctionRepository: CorrectionRepository,
                               vatReturnRepository: VatReturnRepository)(implicit ec: ExecutionContext)
  extends BackendController(cc) {

  def deleteAccounts(): Action[AnyContent] = Action.async {
    for {
      res1 <- correctionRepository.collection.deleteMany(Filters.regex("vrn", "/^1110")).toFutureOption()
      res2 <- vatReturnRepository.collection.deleteMany(Filters.regex("vrn", "/^1110")).toFutureOption()
    } yield {
      Ok("Deleted Perf Tests Accounts MongoDB")
    }

  }

}
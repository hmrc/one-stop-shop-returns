package controllers.actions

import play.api.mvc.{AnyContent, BodyParser, PlayBodyParsers, Request, Result}
import uk.gov.hmrc.domain.Vrn

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class FakeAuthAction @Inject()(bodyParsers: PlayBodyParsers) extends AuthAction {

  override def invokeBlock[A](request: Request[A], block: AuthorisedRequest[A] => Future[Result]): Future[Result] =
    block(AuthorisedRequest(request, "id", Vrn("123456789")))

  override def parser: BodyParser[AnyContent] =
    bodyParsers.default

  override protected def executionContext: ExecutionContext =
    scala.concurrent.ExecutionContext.Implicits.global
}

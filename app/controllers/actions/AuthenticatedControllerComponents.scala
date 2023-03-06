/*
 * Copyright 2023 HM Revenue & Customs
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

import models.requests.RegistrationRequest
import play.api.http.FileMimeTypes
import play.api.i18n.{Langs, MessagesApi}
import play.api.mvc._

import javax.inject.Inject
import scala.concurrent.ExecutionContext

trait AuthenticatedControllerComponents extends ControllerComponents {

  def actionBuilder: DefaultActionBuilder
  def identify: AuthAction
  def getRegistration: GetRegistrationActionProvider

  def auth: ActionBuilder[AuthorisedRequest, AnyContent] =
    actionBuilder andThen identify

  def authAndGetRegistration(vrn: String): ActionBuilder[RegistrationRequest, AnyContent] =
    auth andThen getRegistration(vrn)

  def authAndGetRegistration(): ActionBuilder[RegistrationRequest, AnyContent] =
    auth andThen getRegistration()
}

case class DefaultAuthenticatedControllerComponents @Inject()(
                                                               actionBuilder: DefaultActionBuilder,
                                                               parsers: PlayBodyParsers,
                                                               messagesApi: MessagesApi,
                                                               langs: Langs,
                                                               fileMimeTypes: FileMimeTypes,
                                                               executionContext: ExecutionContext,
                                                               identify: AuthAction,
                                                               getRegistration: GetRegistrationActionProvider
                                                             ) extends AuthenticatedControllerComponents
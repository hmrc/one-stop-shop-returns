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

package config

import com.google.inject.AbstractModule
import controllers.actions.{AuthAction, AuthActionImpl, AuthenticatedControllerComponents, DefaultAuthenticatedControllerComponents}

import java.time.{Clock, Instant, ZoneId, ZoneOffset}

class Module extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[AuthAction]).to(classOf[AuthActionImpl]).asEagerSingleton()
    bind(classOf[Clock]).toInstance(
      Clock.fixed(
        Instant.parse("2022-10-07T12:00:00.00Z"),
        ZoneId.of("Australia/Melbourne")
      )
    )
    bind(classOf[AuthenticatedControllerComponents]).to(classOf[DefaultAuthenticatedControllerComponents]).asEagerSingleton()
  }
}

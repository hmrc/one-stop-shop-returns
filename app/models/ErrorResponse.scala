/*
 * Copyright 2024 HM Revenue & Customs
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

package models

sealed trait ErrorResponse {
  val body: String
}

case object NotFound extends ErrorResponse {
  override val body = "Not found"
}

case object ServerError extends ErrorResponse {
  override val body = "Internal server error"
}

case object ServiceUnavailable extends ErrorResponse {
  override val body: String = "Service unavailable"
}

case object GatewayTimeout extends ErrorResponse {
  override val body: String = "Gateway timeout"
}

case object InvalidJson extends ErrorResponse {
  override val body: String = "Invalid Response"
}

final case class UnexpectedResponseStatus(status: Int, body: String) extends ErrorResponse

case class EtmpDisplayReturnError(code: String, body: String) extends ErrorResponse

case class EtmpListObligationsError(code: String, body: String) extends ErrorResponse

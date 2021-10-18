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

package models.des

sealed trait DesErrorResponse {
  val body: String
}

case object InvalidVrn extends DesErrorResponse {
  override val body: String = "Invalid VRN"
}

case object InvalidJson extends DesErrorResponse {
  override val body: String = "Invalid Response"
}

case object NotFound extends DesErrorResponse {
  override val body = "Not found"
}

case object ServerError extends DesErrorResponse {
  override val body = "Internal server error"
}

case object ServiceUnavailable extends DesErrorResponse {
  override val body: String = "Service unavailable"
}

case class UnexpectedResponseStatus(status: Int, body: String) extends DesErrorResponse

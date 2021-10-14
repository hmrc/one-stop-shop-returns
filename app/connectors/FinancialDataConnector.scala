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

package connectors
import config.DesConfig
import models.financialdata.{FinancialDataQueryParameters, FinancialDataResponse}
import play.api.Logging
import uk.gov.hmrc.domain.Vrn

import javax.inject.Inject
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

class FinancialDataConnector @Inject() (
                                         http:      HttpClient,
                                         desConfig: DesConfig
                                       )(implicit ec: ExecutionContext) extends Logging {

  // external services require explicitly passed headers
  private implicit val emptyHc: HeaderCarrier = HeaderCarrier()
  private val headers: Seq[(String, String)] = desConfig.desHeaders

  private def financialDataUrl(vrn: Vrn) =
    s"${desConfig.baseUrl}/enterprise/financial-data/${vrn.name.toUpperCase()}/${vrn.value}/ECOM"

  def getFinancialData(vrn: Vrn, queryParameters: FinancialDataQueryParameters): Future[Option[FinancialDataResponse]] = {
    val url = financialDataUrl(vrn)
    http.GET[Option[FinancialDataResponse]](
      url,
      queryParameters.toSeqQueryParams,
      headers = headers
    )
  }
}
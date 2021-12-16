package connectors

import config.DesConfig
import connectors.CoreVatReturnHttpParser._
import logging.Logging
import models.core.CoreVatReturn
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class CoreVatReturnConnector @Inject()(
                                        httpClient: HttpClient,
                                        desConfig: DesConfig
                                      )(implicit ec: ExecutionContext) extends Logging {

  private implicit val emptyHc: HeaderCarrier = HeaderCarrier()
  private val headers: Seq[(String, String)] = desConfig.desHeaders

  private def url = s"${desConfig.baseUrl}oss/returns/v1/return"

  def submit(coreVatReturn: CoreVatReturn): Future[CoreVatReturnResponse] = {
    httpClient.POST[CoreVatReturn, CoreVatReturnResponse](
      url,
      coreVatReturn,
      headers = headers
    )
  }

}

package utils

import uk.gov.hmrc.domain.Vrn

object ObfuscationUtils {

  def obfuscateVrn(vrn: Vrn): String = vrn.vrn.take(5) + "****"

}

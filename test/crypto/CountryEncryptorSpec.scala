package crypto

import base.SpecBase
import com.typesafe.config.Config
import generators.Generators
import models._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.Configuration
import services.crypto.EncryptionService

class CountryEncryptorSpec extends SpecBase with ScalaCheckPropertyChecks with Generators {

  private val mockConfiguration = mock[Configuration]
  private val mockConfig = mock[Config]
  private val mockEncryptionService: EncryptionService = new EncryptionService(mockConfiguration)
  private val encryptor = new CountryEncryptor(mockEncryptionService)
  private val secretKey = "VqmXp7yigDFxbCUdDdNZVIvbW6RgPNJsliv6swQNCL8="

  when(mockConfiguration.underlying) thenReturn mockConfig
  when(mockConfig.getString(any())) thenReturn secretKey

  "encrypt / decrypt country" - {

    "must encrypt a country and decrypt it" in {
      forAll(arbitrary[Country]) {
        country =>
          val e = encryptor.encryptCountry(country)
          val d = encryptor.decryptCountry(e)

          d mustEqual country
      }
    }
  }

}

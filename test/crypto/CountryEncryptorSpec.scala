package crypto

import base.SpecBase
import generators.Generators
import models._
import org.scalacheck.Arbitrary.arbitrary
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class CountryEncryptorSpec extends SpecBase with ScalaCheckPropertyChecks with Generators {

  private val cipher    = new SecureGCMCipher
  private val encryptor = new CountryEncryptor(cipher)
  private val secretKey = "VqmXp7yigDFxbCUdDdNZVIvbW6RgPNJsliv6swQNCL8="

  "encrypt / decrypt country" - {

    "must encrypt a country and decrypt it" in {
      forAll(arbitrary[Country]) {
        country =>
          val e = encryptor.encryptCountry(country, vrn, secretKey)
          val d = encryptor.decryptCountry(e, vrn, secretKey)

          d mustEqual country
      }
    }
  }

}

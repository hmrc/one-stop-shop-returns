package crypto

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import java.util.Base64

class AesGCMCryptoSpec extends AnyFreeSpec with Matchers {

  private val encryptor      = new AesGCMCrypto
  private val secretKey      = "VqmXp7yigDFxbCUdDdNZVIvbW6RgPNJsliv6swQNCL8="
  private val secretKey2     = "cXo7u0HuJK8B/52xLwW7eQ=="
  private val textToEncrypt  = "textNotEncrypted"
  private val associatedText = "associatedText"
  private val encryptedText  = EncryptedValue("jOrmajkEqb7Jbo1GvK4Mhc3E7UiOfKS3RCy3O/F6myQ=",
    "WM1yMH4KBGdXe65vl8Gzd37Ob2Bf1bFUSaMqXk78sNeorPFOSWwwhOj0Lcebm5nWRhjNgL4K2SV3GWEXyyqeIhWQ4fJIVQRHM9VjWCTyf7/1/f/ckAaMHqkF1XC8bnW9")


  "decrypt" - {

    "must decrypt text when the same associatedText, nonce and secretKey were used to encrypt it" in {
      val decryptedText = encryptor.decrypt(encryptedText, associatedText, secretKey)
      decryptedText mustEqual textToEncrypt
    }

    "must return an EncryptionDecryptionException if the encrypted value is different" in {
      val invalidText = Base64.getEncoder.encodeToString("invalid value".getBytes)
      val invalidEncryptedValue = EncryptedValue(invalidText, encryptedText.nonce)

      val decryptAttempt = intercept[EncryptionDecryptionException](
        encryptor.decrypt(invalidEncryptedValue, associatedText, secretKey)
      )

      decryptAttempt.failureReason must include("Error occurred due to padding scheme")
    }

    "must return an EncryptionDecryptionException if the nonce is different" in {
      val invalidNonce = Base64.getEncoder.encodeToString("invalid value".getBytes)
      val invalidEncryptedValue = EncryptedValue(encryptedText.value, invalidNonce)

      val decryptAttempt = intercept[EncryptionDecryptionException](
        encryptor.decrypt(invalidEncryptedValue, associatedText, secretKey)
      )

      decryptAttempt.failureReason must include("Error occurred due to padding scheme")
    }

    "must return an EncryptionDecryptionException if the associated text is different" in {
      val decryptAttempt = intercept[EncryptionDecryptionException](
        encryptor.decrypt(encryptedText, "invalid associated text", secretKey)
      )

      decryptAttempt.failureReason must include("Error occurred due to padding scheme")
    }

    "must return an EncryptionDecryptionException if the secret key is different" in {
      val decryptAttempt = intercept[EncryptionDecryptionException](
        encryptor.decrypt(encryptedText, associatedText, secretKey2)
      )

      decryptAttempt.failureReason must include("Error occurred due to padding scheme")
    }

    "must return an EncryptionDecryptionException if the associated text is empty" in {
      val decryptAttempt = intercept[EncryptionDecryptionException](
        encryptor.decrypt(encryptedText, "", secretKey)
      )

      decryptAttempt.failureReason must include("associated text must not be null")
    }

    "must return an EncryptionDecryptionException if the key is empty" in {
      val decryptAttempt = intercept[EncryptionDecryptionException](
        encryptor.decrypt(encryptedText, associatedText, "")
      )

      decryptAttempt.failureReason must include("The key provided is invalid")
    }

    "must return an EncryptionDecryptionException if the key is invalid" in {
      val decryptAttempt = intercept[EncryptionDecryptionException](
        encryptor.decrypt(encryptedText, associatedText, "invalidKey")
      )

      decryptAttempt.failureReason must include("Key being used is not valid." +
        " It could be due to invalid encoding, wrong length or uninitialized")
    }
  }
}

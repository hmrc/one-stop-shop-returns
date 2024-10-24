package crypto

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import java.security.InvalidAlgorithmParameterException
import java.util.Base64
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.{Cipher, IllegalBlockSizeException, KeyGenerator, NoSuchPaddingException}

class AesGCMCryptoSpec extends AnyFreeSpec with Matchers {

  private val encryptor      = new AesGCMCrypto
  private val secretKey      = "VqmXp7yigDFxbCUdDdNZVIvbW6RgPNJsliv6swQNCL8="
  private val secretKey2     = "cXo7u0HuJK8B/52xLwW7eQ=="
  private val textToEncrypt  = "textNotEncrypted"
  private val associatedText = "associatedText"
  private val encryptedText  = EncryptedValue("jOrmajkEqb7Jbo1GvK4Mhc3E7UiOfKS3RCy3O/F6myQ=",
    "WM1yMH4KBGdXe65vl8Gzd37Ob2Bf1bFUSaMqXk78sNeorPFOSWwwhOj0Lcebm5nWRhjNgL4K2SV3GWEXyyqeIhWQ4fJIVQRHM9VjWCTyf7/1/f/ckAaMHqkF1XC8bnW9")

  "encrypt" - {

    "must encrypt some text" in {
      val encryptedValue = encryptor.encrypt(textToEncrypt, associatedText, secretKey)
      encryptedValue mustBe an[EncryptedValue]
    }
  }

  "decrypt" - {

    "must decrypt text when the same associatedText, nonce and secretKey were used to encrypt it" in {
      val decryptedText  = encryptor.decrypt(encryptedText, associatedText, secretKey)
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


    "return an EncryptionDecryptionError if the secret key is an invalid type" in {

      val keyGen = KeyGenerator.getInstance("DES")
      val key = keyGen.generateKey()
      val secureGCMEncryter = new AesGCMCrypto {
        override val ALGORITHM_KEY: String = "DES"
      }
      val encryptedAttempt = intercept[EncryptionDecryptionException](
        secureGCMEncryter.generateCipherText(textToEncrypt, associatedText.getBytes,
          new GCMParameterSpec(96, "hjdfbhvbhvbvjvjfvb".getBytes), key)
      )

      encryptedAttempt.failureReason must include("Key being used is not valid." +
        " It could be due to invalid encoding, wrong length or uninitialized")
    }

    "return an EncryptionDecryptionError if the algorithm is invalid" in {
      val secureGCMEncryter = new AesGCMCrypto {
        override val ALGORITHM_TO_TRANSFORM_STRING: String = "invalid"
      }
      val encryptedAttempt = intercept[EncryptionDecryptionException](
        secureGCMEncryter.encrypt(textToEncrypt, associatedText, secretKey)
      )

      encryptedAttempt.failureReason must include("Algorithm being requested is not available in this environment")
    }


    "return an EncryptionDecryptionError if the padding is invalid" in {
      val secureGCMEncryter = new AesGCMCrypto {
        override def getCipherInstance: Cipher = throw new NoSuchPaddingException()
      }
      val encryptedAttempt = intercept[EncryptionDecryptionException](
        secureGCMEncryter.encrypt(textToEncrypt, associatedText, secretKey)
      )

      encryptedAttempt.failureReason must include("Padding Scheme being requested is not available this environment")
    }

    "return an EncryptionDecryptionError if an InvalidAlgorithmParameterException is thrown" in {
      val secureGCMEncryter = new AesGCMCrypto {
        override def getCipherInstance: Cipher = throw new InvalidAlgorithmParameterException()
      }
      val encryptedAttempt = intercept[EncryptionDecryptionException](
        secureGCMEncryter.encrypt(textToEncrypt, associatedText, secretKey)
      )

      encryptedAttempt.failureReason must include("Algorithm parameters being specified are not valid")
    }

    "return an EncryptionDecryptionError if a IllegalStateException is thrown" in {
      val secureGCMEncryter = new AesGCMCrypto {
        override def getCipherInstance: Cipher = throw new IllegalStateException()
      }
      val encryptedAttempt = intercept[EncryptionDecryptionException](
        secureGCMEncryter.encrypt(textToEncrypt, associatedText, secretKey)
      )

      encryptedAttempt.failureReason must include("Cipher is in an illegal state")
    }

    "return an EncryptionDecryptionError if a UnsupportedOperationException is thrown" in {
      val secureGCMEncryter = new AesGCMCrypto {
        override def getCipherInstance: Cipher = throw new UnsupportedOperationException()
      }
      val encryptedAttempt = intercept[EncryptionDecryptionException](
        secureGCMEncryter.encrypt(textToEncrypt, associatedText, secretKey)
      )

      encryptedAttempt.failureReason must include("Provider might not be supporting this method")
    }

    "return an EncryptionDecryptionError if a IllegalBlockSizeException is thrown" in {
      val secureGCMEncryter = new AesGCMCrypto{
        override def getCipherInstance: Cipher = throw new IllegalBlockSizeException()
      }
      val encryptedAttempt = intercept[EncryptionDecryptionException](
        secureGCMEncryter.encrypt(textToEncrypt, associatedText, secretKey)
      )

      encryptedAttempt.failureReason must include("Error occurred due to block size")
    }

    "return an EncryptionDecryptionError if a RuntimeException is thrown" in {
      val secureGCMEncryter = new AesGCMCrypto {
        override def getCipherInstance: Cipher = throw new RuntimeException()
      }
      val encryptedAttempt = intercept[EncryptionDecryptionException](
        secureGCMEncryter.encrypt(textToEncrypt, associatedText, secretKey)
      )

      encryptedAttempt.failureReason must include("Unexpected exception")
    }
  }
}

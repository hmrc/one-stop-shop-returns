package crypto

import base.SpecBase
import config.AppConfig
import models.{LegacyEncryptedSavedUserAnswers, NewEncryptedSavedUserAnswers, SavedUserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.libs.json.Json
import services.crypto.EncryptionService

class SavedUserAnswersEncryptorSpec extends SpecBase {

  private val mockAppConfig: AppConfig = mock[AppConfig]
  private val mockEncryptionService: EncryptionService = mock[EncryptionService]
  private val mockSecureGCMCipher: AesGCMCrypto = mock[AesGCMCrypto]

  private val savedUserAnswers: SavedUserAnswers = arbitrarySavedUserAnswers.arbitrary.sample.value
  private val encryptedValue: String = "encryptedValue"

  "SavedUserAnswersEncryptor" - {

    "must encrypt and return a EncryptedSavedUserAnswers" in {

      when(mockEncryptionService.encryptField(any())) `thenReturn` encryptedValue

      val service = new SavedUserAnswersEncryptor(mockAppConfig, mockSecureGCMCipher, mockEncryptionService)

      val expectedResult = NewEncryptedSavedUserAnswers(savedUserAnswers.vrn, savedUserAnswers.period, encryptedValue, savedUserAnswers.lastUpdated)
      val result = service.encryptAnswers(savedUserAnswers, savedUserAnswers.vrn)

      result mustBe expectedResult
    }

    "must decrypt and return a SavedUserAnswers" in {

      when(mockEncryptionService.decryptField(any())) `thenReturn` Json.parse(savedUserAnswers.data.toString()).toString()

      val service = new SavedUserAnswersEncryptor(mockAppConfig, mockSecureGCMCipher, mockEncryptionService)

      val encryptedSavedUserAnswers = NewEncryptedSavedUserAnswers(savedUserAnswers.vrn, savedUserAnswers.period, encryptedValue, savedUserAnswers.lastUpdated)

      val expectedResult = savedUserAnswers
      val result = service.decryptAnswers(encryptedSavedUserAnswers, encryptedSavedUserAnswers.vrn)

      result mustBe expectedResult
    }

    "must decrypt a LegacyEncryptedSavedUserAnswers and return a SavedUserAnswers" in {

      when(mockSecureGCMCipher.decrypt(any(), any(), any())) `thenReturn` Json.parse(savedUserAnswers.data.toString()).toString()

      val service = new SavedUserAnswersEncryptor(mockAppConfig, mockSecureGCMCipher, mockEncryptionService)

      val encryptedValue: EncryptedValue = EncryptedValue(value = "testValue", nonce = "testNonce")

      val encryptedSavedUserAnswers = LegacyEncryptedSavedUserAnswers(savedUserAnswers.vrn, savedUserAnswers.period, encryptedValue, savedUserAnswers.lastUpdated)

      val expectedResult = savedUserAnswers
      val result = service.decryptLegacyAnswers(encryptedSavedUserAnswers, encryptedSavedUserAnswers.vrn)

      result mustBe expectedResult
    }
  }
}


package com.github.meeplemeet.model.account

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.model.AccountNotFoundException
import com.github.meeplemeet.ui.theme.ThemeMode
import com.github.meeplemeet.utils.FirestoreTests
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountRepositoryIntegrationTest : FirestoreTests() {
  @Before
  fun setup() {
    runBlocking { auth.signInAnonymously().await() }
  }

  @Test
  fun createAccount_createsValidAccount() = runBlocking {
    val testHandle = "test_user_${System.currentTimeMillis()}"
    val name = "Test User"
    val email = "test@example.com"
    val photoUrl = "https://example.com/photo.jpg"

    val account = accountRepository.createAccount(testHandle, name, email, photoUrl)

    assertEquals(testHandle, account.uid)
    assertEquals(testHandle, account.handle)
    assertEquals(name, account.name)
    assertEquals(email, account.email)
    assertEquals(photoUrl, account.photoUrl)

    // Cleanup
    accountRepository.deleteAccount(testHandle)
  }

  @Test
  fun getAccount_retrievesExistingAccount() = runBlocking {
    val testHandle = "test_user_get_${System.currentTimeMillis()}"
    val name = "Get Test"
    val email = "get@test.com"

    accountRepository.createAccount(testHandle, name, email, null)

    val retrievedAccount = accountRepository.getAccount(testHandle, getAllData = true)

    assertNotNull(retrievedAccount)
    assertEquals(testHandle, retrievedAccount.uid)
    assertEquals(name, retrievedAccount.name)

    // Cleanup
    accountRepository.deleteAccount(testHandle)
  }

  @Test
  fun getAccount_throwsException_whenAccountNotFound() = runBlocking {
    val nonExistentId = "nonexistent_${System.currentTimeMillis()}"

    try {
      accountRepository.getAccount(nonExistentId)
      throw AssertionError("Expected AccountNotFoundException")
    } catch (e: AccountNotFoundException) {
      // Expected
    }
  }

  @Test
  fun getAccountSafe_returnsNull_whenAccountNotFound() = runBlocking {
    val nonExistentId = "nonexistent_safe_${System.currentTimeMillis()}"

    val result = accountRepository.getAccountSafe(nonExistentId)

    assertNull(result)
  }

  @Test
  fun setAccountName_updatesName() = runBlocking {
    val testHandle = "test_name_${System.currentTimeMillis()}"
    accountRepository.createAccount(testHandle, "Original Name", "name@test.com", null)

    val newName = "Updated Name"
    accountRepository.setAccountName(testHandle, newName)

    val updated = accountRepository.getAccount(testHandle, getAllData = false)
    assertEquals(newName, updated.name)

    // Cleanup
    accountRepository.deleteAccount(testHandle)
  }

  @Test
  fun setAccountTheme_updatesTheme() = runBlocking {
    val testHandle = "test_theme_${System.currentTimeMillis()}"
    accountRepository.createAccount(testHandle, "Theme Test", "theme@test.com", null)

    accountRepository.setAccountTheme(testHandle, ThemeMode.DARK)

    val updated = accountRepository.getAccount(testHandle, getAllData = false)
    assertEquals(ThemeMode.DARK, updated.themeMode)

    // Cleanup
    accountRepository.deleteAccount(testHandle)
  }

  @Test
  fun setAccountRole_updatesBothRoles() = runBlocking {
    val testHandle = "test_role_${System.currentTimeMillis()}"
    accountRepository.createAccount(testHandle, "Role Test", "role@test.com", null)

    accountRepository.setAccountRole(testHandle, isShopOwner = true, isSpaceRenter = true)

    val updated = accountRepository.getAccount(testHandle, getAllData = false)
    assertTrue(updated.shopOwner)
    assertTrue(updated.spaceRenter)

    // Cleanup
    accountRepository.deleteAccount(testHandle)
  }

  @Test
  fun setAccountDescription_updatesDescription() = runBlocking {
    val testHandle = "test_desc_${System.currentTimeMillis()}"
    accountRepository.createAccount(testHandle, "Desc Test", "desc@test.com", null)

    val newDescription = "Test description"
    accountRepository.setAccountDescription(testHandle, newDescription)

    val updated = accountRepository.getAccount(testHandle, getAllData = false)
    assertEquals(newDescription, updated.description)

    // Cleanup
    accountRepository.deleteAccount(testHandle)
  }

  @Test
  fun setAccountPhotoUrl_updatesPhotoUrl() = runBlocking {
    val testHandle = "test_photo_${System.currentTimeMillis()}"
    accountRepository.createAccount(testHandle, "Photo Test", "photo@test.com", null)

    val newPhotoUrl = "https://new.com/photo.jpg"
    accountRepository.setAccountPhotoUrl(testHandle, newPhotoUrl)

    val updated = accountRepository.getAccount(testHandle, getAllData = false)
    assertEquals(newPhotoUrl, updated.photoUrl)

    // Cleanup
    accountRepository.deleteAccount(testHandle)
  }

  @Test
  fun setAccountEmail_updatesEmail() = runBlocking {
    val testHandle = "test_email_${System.currentTimeMillis()}"
    accountRepository.createAccount(testHandle, "Email Test", "old@test.com", null)

    val newEmail = "new@test.com"
    accountRepository.setAccountEmail(testHandle, newEmail)

    val updated = accountRepository.getAccount(testHandle, getAllData = false)
    assertEquals(newEmail, updated.email)

    // Cleanup
    accountRepository.deleteAccount(testHandle)
  }

  @Test
  fun isEmailInUse_returnsFalse_whenEmailNotUsed() = runBlocking {
    val testHandle = "test_email_check_${System.currentTimeMillis()}"
    val unusedEmail = "unused_${System.currentTimeMillis()}@test.com"

    val result = accountRepository.isEmailInUse(unusedEmail, testHandle)

    assertFalse(result)
  }

  @Test
  fun updateFcmToken_updatesToken() = runBlocking {
    val testHandle = "test_fcm_${System.currentTimeMillis()}"
    accountRepository.createAccount(testHandle, "FCM Test", "fcm@test.com", null)

    val token = "test_fcm_token_123"
    accountRepository.updateFcmToken(testHandle, token)

    val updated = accountRepository.getAccount(testHandle, getAllData = false)
    assertEquals(token, updated.fcmToken)

    // Cleanup
    accountRepository.deleteAccount(testHandle)
  }

  @Test
  fun setAccountNotificationSettings_updatesSettings() = runBlocking {
    val testHandle = "test_notif_${System.currentTimeMillis()}"
    accountRepository.createAccount(testHandle, "Notif Test", "notif@test.com", null)

    accountRepository.setAccountNotificationSettings(testHandle, NotificationSettings.FRIENDS_ONLY)

    val updated = accountRepository.getAccount(testHandle, getAllData = false)
    assertEquals(NotificationSettings.FRIENDS_ONLY, updated.notificationSettings)

    // Cleanup
    accountRepository.deleteAccount(testHandle)
  }

  @Test
  fun deleteAccount_removesAccount() = runBlocking {
    val testHandle = "test_delete_${System.currentTimeMillis()}"
    accountRepository.createAccount(testHandle, "Delete Test", "delete@test.com", null)

    accountRepository.deleteAccount(testHandle)

    try {
      accountRepository.getAccount(testHandle)
      throw AssertionError("Expected AccountNotFoundException")
    } catch (e: AccountNotFoundException) {
      // Expected
    }
  }
}

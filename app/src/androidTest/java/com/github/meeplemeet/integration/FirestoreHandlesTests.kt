package com.github.meeplemeet.integration

import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.model.HandleAlreadyTakenException
import com.github.meeplemeet.model.repositories.FirestoreHandlesRepository
import com.github.meeplemeet.model.repositories.FirestoreRepository
import com.github.meeplemeet.model.repositories.HANDLES_COLLECTION_PATH
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.viewmodels.FirestoreHandlesViewModel
import com.github.meeplemeet.utils.FirestoreTests
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.Test

class FirestoreHandlesTests : FirestoreTests() {
  private lateinit var handlesRepo: FirestoreHandlesRepository
  private lateinit var accountRepo: FirestoreRepository
  private lateinit var handlesVM: FirestoreHandlesViewModel
  private lateinit var testAccount: Account
  private lateinit var testAccount2: Account

  @Before
  fun setup() {
    handlesRepo = FirestoreHandlesRepository(Firebase.firestore)
    handlesVM = FirestoreHandlesViewModel(handlesRepo)
    accountRepo = FirestoreRepository(Firebase.firestore)
    runBlocking {
      testAccount =
          accountRepo.createAccount(
              "Alice", "", email = "alice_handles_test@example.com", photoUrl = null)
      testAccount2 =
          accountRepo.createAccount(
              "Bob", "", email = "bob_handles_test@example.com", photoUrl = null)
    }
  }

  // ==================== handleForAccountExists Tests ====================

  @Test
  fun handleForAccountExists_whenHandleExistsForAccount_clearsError() = runBlocking {
    val handle = "existing_handle_123"

    // Create a handle for the account
    handlesVM.createAccountHandle(testAccount, handle)
    delay(1000)

    // Update account with the handle
    val accountWithHandle = testAccount.copy(handle = handle)

    // Check if handle exists for this account
    handlesVM.handleForAccountExists(accountWithHandle)
    delay(1000)

    // Should clear error since handle exists for this account
    assertEquals("", handlesVM.errorMessage.value)
  }

  @Test
  fun handleForAccountExists_whenHandleDoesNotExistForAccount_setsError() = runBlocking {
    val handle = "non_existent_handle_456"

    // Don't create a handle, just use an account with a handle value that doesn't exist in DB
    val accountWithFakeHandle = testAccount.copy(handle = handle)

    // Check if handle exists for this account
    handlesVM.handleForAccountExists(accountWithFakeHandle)
    delay(1000)

    // Should set error since handle doesn't exist for this account
    assertEquals("No handle associated to this account", handlesVM.errorMessage.value)
  }

  @Test
  fun handleForAccountExists_whenAccountHasEmptyHandle_setsError() = runBlocking {
    // Account with empty handle
    val accountWithEmptyHandle = testAccount.copy(handle = "")

    // Check if handle exists for this account
    handlesVM.handleForAccountExists(accountWithEmptyHandle)
    delay(1000)

    // Should set error since account has no handle
    assertEquals("No handle associated to this account", handlesVM.errorMessage.value)
  }

  @Test
  fun handleForAccountExists_whenHandleExistsButForDifferentAccount_setsError() = runBlocking {
    val handle = "taken_handle_789"

    // Create handle for testAccount
    handlesVM.createAccountHandle(testAccount, handle)
    delay(1000)

    // Try to check if the same handle exists for testAccount2 (it doesn't)
    val account2WithSameHandle = testAccount2.copy(handle = handle)

    handlesVM.handleForAccountExists(account2WithSameHandle)
    delay(1000)

    // Should set error because the handle exists but not for this account
    assertEquals("No handle associated to this account", handlesVM.errorMessage.value)
  }

  @Test
  fun handleForAccountExists_afterDeletingHandle_setsError() = runBlocking {
    val handle = "deleted_handle_999"

    // Create handle
    handlesVM.createAccountHandle(testAccount, handle)
    delay(1000)

    val accountWithHandle = testAccount.copy(handle = handle)

    // Verify it exists
    handlesVM.handleForAccountExists(accountWithHandle)
    delay(1000)
    assertEquals("", handlesVM.errorMessage.value)

    // Delete the handle
    handlesVM.deleteAccountHandle(accountWithHandle)
    delay(1000)

    // Check again after deletion
    handlesVM.handleForAccountExists(accountWithHandle)
    delay(1000)

    // Should now set error since handle was deleted
    assertEquals("No handle associated to this account", handlesVM.errorMessage.value)
  }

  @Test
  fun handleForAccountExists_afterChangingHandle_checksNewHandle() = runBlocking {
    val oldHandle = "old_handle_abc"
    val newHandle = "new_handle_xyz"

    // Create initial handle
    handlesVM.createAccountHandle(testAccount, oldHandle)
    delay(1000)

    // Change the handle
    val accountWithOldHandle = testAccount.copy(handle = oldHandle)
    handlesVM.setAccountHandle(accountWithOldHandle, newHandle)
    delay(1000)

    // Check with new handle
    val accountWithNewHandle = testAccount.copy(handle = newHandle)
    handlesVM.handleForAccountExists(accountWithNewHandle)
    delay(1000)

    // Should clear error since new handle exists for this account
    assertEquals("", handlesVM.errorMessage.value)

    // Check with old handle (should now error)
    handlesVM.handleForAccountExists(accountWithOldHandle)
    delay(1000)

    // Should set error since old handle no longer exists for this account
    assertEquals("No handle associated to this account", handlesVM.errorMessage.value)
  }

  // ==================== checkHandleAvailable Tests ====================

  @Test
  fun checkHandleExistsClearsErrorForNewHandle() = runBlocking {
    val handle = "non_existent_handle_123"

    handlesVM.checkHandleAvailable(handle)
    delay(1000) // Wait for ViewModel coroutine to complete

    assertEquals("", handlesVM.errorMessage.value)
    assertFalse(handlesRepo.checkHandleAvailable(handle))
  }

  @Test
  fun checkHandleExistsSetsErrorForExistingHandle() = runBlocking {
    val handle = "existing_handle_456"
    handlesVM.createAccountHandle(testAccount, handle)
    delay(1000) // Wait for creation to complete

    handlesVM.checkHandleAvailable(handle)
    delay(1000) // Wait for check to complete

    assertEquals(HandleAlreadyTakenException.DEFAULT_MESSAGE, handlesVM.errorMessage.value)
    assertTrue(handlesRepo.checkHandleAvailable(handle))
  }

  @Test
  fun checkHandleExistsWithEmptyString() = runBlocking {
    handlesVM.checkHandleAvailable("")
    delay(100) // Short delay for synchronous operation

    assertEquals("Handle can not be blank", handlesVM.errorMessage.value)
  }

  // ==================== createAccountHandle Tests ====================

  @Test
  fun canCreateNewHandle() = runBlocking {
    val handle = "alice_unique_handle"

    handlesVM.createAccountHandle(testAccount, handle)
    delay(1000) // Wait for ViewModel operation

    // Verify no error occurred
    assertEquals("", handlesVM.errorMessage.value)

    // Verify the handle document exists in Firestore
    val handleDoc =
        FirebaseProvider.db.collection(HANDLES_COLLECTION_PATH).document(handle).get().await()
    assertTrue(handleDoc.exists())

    // Verify the handle document points to the correct account
    val accountId = handleDoc.getString("accountId")
    assertNotNull(accountId)
    assertEquals(testAccount.uid, accountId)

    // Verify the account in Firestore was updated
    val fetchedAccount = accountRepo.getAccount(testAccount.uid)
    assertEquals(handle, fetchedAccount.handle)
  }

  @Test
  fun createAccountHandleSetsErrorWhenHandleAlreadyExists() = runBlocking {
    val handle = "duplicate_handle"

    // Create handle for first account
    handlesVM.createAccountHandle(testAccount, handle)
    delay(1000)
    assertEquals("", handlesVM.errorMessage.value)

    // Try to create same handle for second account - should set error
    handlesVM.createAccountHandle(testAccount2, handle)
    delay(1000)

    assertEquals(HandleAlreadyTakenException.DEFAULT_MESSAGE, handlesVM.errorMessage.value)
  }

  @Test
  fun createAccountHandleSetsErrorWhenAccountDoesNotExist() = runBlocking {
    val handle = "orphan_handle"
    val nonExistentAccount =
        Account(
            uid = "non_existent_account_id",
            handle = "",
            name = "Ghost",
            email = "ghost@example.com")

    handlesVM.createAccountHandle(nonExistentAccount, handle)
    delay(1000)

    assertEquals(HandleAlreadyTakenException.DEFAULT_MESSAGE, handlesVM.errorMessage.value)
  }

  @Test
  fun createAccountHandleWithSpecialCharacters() = runBlocking {
    val handle = "test_user-123"

    handlesVM.createAccountHandle(testAccount, handle)
    delay(1000)

    assertEquals("", handlesVM.errorMessage.value)
    assertTrue(handlesRepo.checkHandleAvailable(handle))
  }

  @Test
  fun createAccountHandleUpdatesAccountInFirestore() = runBlocking {
    val handle = "persistent_handle"

    handlesVM.createAccountHandle(testAccount, handle)
    delay(1000)

    // Verify the account document was updated in Firestore
    val fetchedAccount = accountRepo.getAccount(testAccount.uid)
    assertEquals(handle, fetchedAccount.handle)
  }

  // ==================== setAccountHandle Tests ====================

  @Test
  fun canSetAccountHandle() = runBlocking {
    val oldHandle = "old_handle_789"
    val newHandle = "new_handle_789"

    // Create initial handle
    handlesVM.createAccountHandle(testAccount, oldHandle)
    delay(1000)

    // Update testAccount with the old handle
    val accountWithOldHandle = testAccount.copy(handle = oldHandle)

    // Update to new handle
    handlesVM.setAccountHandle(accountWithOldHandle, newHandle)
    delay(1000)

    // Verify no error occurred
    assertEquals("", handlesVM.errorMessage.value)

    // Verify old handle document was deleted
    val oldHandleDoc =
        FirebaseProvider.db.collection(HANDLES_COLLECTION_PATH).document(oldHandle).get().await()
    assertFalse(oldHandleDoc.exists())

    // Verify new handle document exists
    val newHandleDoc =
        FirebaseProvider.db.collection(HANDLES_COLLECTION_PATH).document(newHandle).get().await()
    assertTrue(newHandleDoc.exists())
    assertEquals(testAccount.uid, newHandleDoc.getString("accountId"))

    // Verify the account in Firestore was updated
    val fetchedAccount = accountRepo.getAccount(testAccount.uid)
    assertEquals(newHandle, fetchedAccount.handle)
  }

  @Test
  fun setAccountHandleSetsErrorWhenNewHandleAlreadyExists() = runBlocking {
    val handle1 = "handle_account1"
    val handle2 = "handle_account2"

    // Create handles for both accounts
    handlesVM.createAccountHandle(testAccount, handle1)
    delay(1000)
    handlesVM.createAccountHandle(testAccount2, handle2)
    delay(1000)

    val accountWithHandle1 = testAccount.copy(handle = handle1)

    // Try to change account1's handle to account2's handle - should set error
    handlesVM.setAccountHandle(accountWithHandle1, handle2)
    delay(1000)

    assertEquals(HandleAlreadyTakenException.DEFAULT_MESSAGE, handlesVM.errorMessage.value)
  }

  @Test
  fun setAccountHandleSetsErrorWhenAccountDoesNotExist() = runBlocking {
    val oldHandle = "old_handle"
    val newHandle = "new_handle"
    val nonExistentAccount =
        Account(
            uid = "non_existent_account_id",
            handle = oldHandle,
            name = "Ghost",
            email = "ghost@example.com")

    handlesVM.setAccountHandle(nonExistentAccount, newHandle)
    delay(1000)

    assertEquals(HandleAlreadyTakenException.DEFAULT_MESSAGE, handlesVM.errorMessage.value)
  }

  @Test
  fun setAccountHandleToSameHandle() = runBlocking {
    val handle = "same_handle"

    // Create initial handle
    handlesVM.createAccountHandle(testAccount, handle)
    delay(1000)

    val accountWithHandle = testAccount.copy(handle = handle)

    // Set to same handle - documents behavior
    handlesVM.setAccountHandle(accountWithHandle, handle)
    delay(1000)

    // May or may not set error depending on implementation
    // Just verify handle still exists
    assertTrue(handlesRepo.checkHandleAvailable(handle))
  }

  @Test
  fun setAccountHandleRemovesOldHandleDocument() = runBlocking {
    val oldHandle = "to_be_removed"
    val newHandle = "replacement_handle"

    handlesVM.createAccountHandle(testAccount, oldHandle)
    delay(1000)

    val accountWithOldHandle = testAccount.copy(handle = oldHandle)
    handlesVM.setAccountHandle(accountWithOldHandle, newHandle)
    delay(1000)

    // Verify old handle is gone
    assertFalse(handlesRepo.checkHandleAvailable(oldHandle))
    // Verify new handle exists
    assertTrue(handlesRepo.checkHandleAvailable(newHandle))
  }

  // ==================== deleteAccountHandle Tests ====================

  @Test
  fun canDeleteAccountHandle() = runBlocking {
    val handle = "handle_to_delete"

    // Create handle
    handlesVM.createAccountHandle(testAccount, handle)
    delay(1000)
    assertTrue(handlesRepo.checkHandleAvailable(handle))

    val accountWithHandle = testAccount.copy(handle = handle)

    // Delete handle
    handlesVM.deleteAccountHandle(accountWithHandle)
    delay(1000)

    // Verify handle document was deleted
    assertFalse(handlesRepo.checkHandleAvailable(handle))

    // Verify the document is actually gone from Firestore
    val handleDoc =
        FirebaseProvider.db.collection(HANDLES_COLLECTION_PATH).document(handle).get().await()
    assertFalse(handleDoc.exists())
  }

  @Test
  fun deleteAccountHandleWithNonExistentHandle() = runBlocking {
    val handle = "never_created_handle"
    val accountWithFakeHandle = testAccount.copy(handle = handle)

    // Delete should not throw even if handle doesn't exist
    handlesVM.deleteAccountHandle(accountWithFakeHandle)
    delay(1000)

    // Verify it still doesn't exist
    assertFalse(handlesRepo.checkHandleAvailable(handle))
  }

  @Test
  fun deleteAccountHandleMultipleTimes() = runBlocking {
    val handle = "delete_multiple_times"

    handlesVM.createAccountHandle(testAccount, handle)
    delay(1000)

    val accountWithHandle = testAccount.copy(handle = handle)
    handlesVM.deleteAccountHandle(accountWithHandle)
    delay(1000)

    // Delete again - should not throw
    handlesVM.deleteAccountHandle(accountWithHandle)
    delay(1000)

    assertFalse(handlesRepo.checkHandleAvailable(handle))
  }

  // ==================== Integration Scenario Tests ====================

  @Test
  fun completeHandleLifecycle() = runBlocking {
    val handle1 = "lifecycle_handle_1"
    val handle2 = "lifecycle_handle_2"

    // Create handle
    handlesVM.createAccountHandle(testAccount, handle1)
    delay(1000)
    assertEquals("", handlesVM.errorMessage.value)
    assertTrue(handlesRepo.checkHandleAvailable(handle1))

    // Update handle
    val accountWithHandle1 = testAccount.copy(handle = handle1)
    handlesVM.setAccountHandle(accountWithHandle1, handle2)
    delay(1000)
    assertEquals("", handlesVM.errorMessage.value)
    assertFalse(handlesRepo.checkHandleAvailable(handle1))
    assertTrue(handlesRepo.checkHandleAvailable(handle2))

    // Delete handle
    val accountWithHandle2 = testAccount.copy(handle = handle2)
    handlesVM.deleteAccountHandle(accountWithHandle2)
    delay(1000)
    assertFalse(handlesRepo.checkHandleAvailable(handle2))
  }

  @Test
  fun multipleAccountsCanHaveDifferentHandles() = runBlocking {
    val handle1 = "alice_handle"
    val handle2 = "bob_handle"

    handlesVM.createAccountHandle(testAccount, handle1)
    delay(1000)
    handlesVM.createAccountHandle(testAccount2, handle2)
    delay(1000)

    assertEquals("", handlesVM.errorMessage.value)
    assertTrue(handlesRepo.checkHandleAvailable(handle1))
    assertTrue(handlesRepo.checkHandleAvailable(handle2))

    // Verify accounts in Firestore
    val account1 = accountRepo.getAccount(testAccount.uid)
    val account2 = accountRepo.getAccount(testAccount2.uid)
    assertEquals(handle1, account1.handle)
    assertEquals(handle2, account2.handle)
  }

  @Test
  fun canReuseHandleAfterDeletion() = runBlocking {
    val handle = "reusable_handle"

    // Create handle for first account
    handlesVM.createAccountHandle(testAccount, handle)
    delay(1000)

    // Delete the handle
    val accountWithHandle = testAccount.copy(handle = handle)
    handlesVM.deleteAccountHandle(accountWithHandle)
    delay(1000)

    // Create same handle for second account - should succeed
    handlesVM.createAccountHandle(testAccount2, handle)
    delay(1000)
    assertEquals("", handlesVM.errorMessage.value)
    assertTrue(handlesRepo.checkHandleAvailable(handle))

    // Verify it points to second account
    val handleDoc =
        FirebaseProvider.db.collection(HANDLES_COLLECTION_PATH).document(handle).get().await()
    assertEquals(testAccount2.uid, handleDoc.getString("accountId"))
  }
}

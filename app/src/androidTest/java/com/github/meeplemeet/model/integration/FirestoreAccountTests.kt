package com.github.meeplemeet.model.integration

import com.github.meeplemeet.model.AccountNotFoundException
import com.github.meeplemeet.model.utils.FirestoreTests
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FirestoreAccountTests : FirestoreTests() {
  @Test
  fun canCreateAccount() = runTest {
    val name = "Antoine"
    val account = viewModels[0].createAccount(name)
    val getAccount = viewModels[1].getAccount(account.uid)

    assertEquals(account.uid, getAccount.uid)
    assertEquals(name, getAccount.name)
    assertTrue(getAccount.previews.isEmpty())
  }

  @Test(expected = AccountNotFoundException::class)
  fun canDeleteAccount() = runTest {
    val name = "Antoine"
    val account = viewModels[0].createAccount(name)
    viewModels[0].deleteAccount(account)
    viewModels[1].getAccount(account.uid)
  }

  @Test
  fun canSetAccountName() = runTest {
    val newName = "Danny"
    val account = viewModels[0].createAccount("Antoine")
    viewModels[1].setAccountName(account, newName)
    val updated = viewModels[2].getAccount(account.uid)

    assertEquals(account.uid, updated.uid)
    assertEquals(newName, updated.name)
    assertTrue(updated.previews.isEmpty())
  }
}

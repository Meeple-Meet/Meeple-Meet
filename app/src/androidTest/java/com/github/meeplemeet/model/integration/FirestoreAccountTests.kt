package com.github.meeplemeet.model.integration

import com.github.meeplemeet.model.utils.FirestoreTests
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreAccountTests : FirestoreTests() {

  /*private val repository = mockk<FirestoreRepository>()
  private lateinit var viewModel: FirestoreViewModel

  @Before
  fun setup() {
    Dispatchers.setMain(StandardTestDispatcher())
    viewModel = FirestoreViewModel(repository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test(expected = AccountNotFoundException::class)
  fun canDeleteAccount() = runTest {
    val name = "Antoine"
    val acc = Account(uid = "a2", name = name)

    coEvery { repository.createAccount(name) } returns acc
    coEvery { repository.deleteAccount(acc.uid) } returns Unit
    coEvery { repository.getAccount(acc.uid) } throws AccountNotFoundException("")

    viewModel.createAccount(name)
    advanceUntilIdle()

    viewModel.deleteAccount(acc)
    advanceUntilIdle()

    viewModel.getAccount(acc.uid)
    advanceUntilIdle()
  }

  @Test
  fun canSetAccountName() = runTest {
    val original = Account(uid = "a3", name = "Antoine")
    val newName = "Danny"
    val updated = original.copy(name = newName)

    coEvery { repository.createAccount(original.name) } returns original
    coEvery { repository.setAccountName(original.uid, newName) } returns updated
    coEvery { repository.getAccount(original.uid) } returns updated

    viewModel.createAccount(original.name)
    advanceUntilIdle()

    viewModel.setAccountName(original, newName)
    advanceUntilIdle()

    viewModel.getAccount(original.uid)
    advanceUntilIdle()
    val fetched = viewModel.account.value!!

    assertEquals(original.uid, fetched.uid)
    assertEquals(newName, fetched.name)
    assertTrue(fetched.previews.isEmpty())
  }*/
}

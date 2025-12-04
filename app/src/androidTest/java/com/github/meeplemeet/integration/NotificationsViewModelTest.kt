package com.github.meeplemeet.integration

import androidx.test.platform.app.InstrumentationRegistry
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.NotificationsViewModel
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.utils.FirestoreTests
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NotificationsViewModelTest : FirestoreTests() {

  private lateinit var viewModel: NotificationsViewModel
  private lateinit var testAccount: Account

  @Before
  fun setUp() = runBlocking {
      viewModel =
          NotificationsViewModel(
              accountRepository = accountRepository,
              handlesRepository = handlesRepository,
              imageRepository = imageRepository,
              discussionRepository = discussionRepository,
              gameRepository = gameRepository
          )

      // Sign in anonymously for Firebase access
      auth.signInAnonymously().await()

      // Create a test account
      testAccount =
          accountRepository.createAccount("testUser", "Test User", "test@example.com", null)
  }

  @Test
  fun getDiscussion_validId_callsOnResult() = runBlocking {
      // Create a discussion
      val discussion =
          discussionRepository.createDiscussion(
              "Test Discussion", "Description", testAccount.uid, emptyList()
          )

      val latch = CountDownLatch(1)
      var resultDiscussion: Discussion? = null
      var deletedCalled = false

      InstrumentationRegistry.getInstrumentation().runOnMainSync {
          viewModel.getDiscussion(
              discussionId = discussion.uid,
              onResult = {
                  resultDiscussion = it
                  latch.countDown()
              },
              onDeleted = {
                  deletedCalled = true
                  latch.countDown()
              })
      }

      latch.await(2, TimeUnit.SECONDS)
      Assert.assertNotNull("Discussion should be found", resultDiscussion)
      Assert.assertEquals(discussion.uid, resultDiscussion?.uid)
      Assert.assertEquals(false, deletedCalled)
  }

  @Test
  fun getDiscussion_invalidId_callsOnDeleted() = runBlocking {
      val latch = CountDownLatch(1)
      var resultDiscussion: Discussion? = null
      var deletedCalled = false

      InstrumentationRegistry.getInstrumentation().runOnMainSync {
          viewModel.getDiscussion(
              discussionId = "non_existent_id",
              onResult = {
                  resultDiscussion = it
                  latch.countDown()
              },
              onDeleted = {
                  deletedCalled = true
                  latch.countDown()
              })
      }

      latch.await(2, TimeUnit.SECONDS)
      Assert.assertTrue("onDeleted should be called", deletedCalled)
      Assert.assertEquals(null, resultDiscussion)
  }

  @Test
  fun getOtherAccountData_validId_callsOnResult() = runBlocking {
      val latch = CountDownLatch(1)
      var resultAccount: Account? = null
      var deletedCalled = false

      InstrumentationRegistry.getInstrumentation().runOnMainSync {
          viewModel.getOtherAccountData(
              id = testAccount.uid,
              onResult = {
                  resultAccount = it
                  latch.countDown()
              },
              onDeleted = {
                  deletedCalled = true
                  latch.countDown()
              })
      }

      latch.await(2, TimeUnit.SECONDS)
      Assert.assertNotNull("Account should be found", resultAccount)
      Assert.assertEquals(testAccount.uid, resultAccount?.uid)
      Assert.assertEquals(false, deletedCalled)
  }

  @Test
  fun getOtherAccountData_invalidId_callsOnDeleted() = runBlocking {
      val latch = CountDownLatch(1)
      var resultAccount: Account? = null
      var deletedCalled = false

      InstrumentationRegistry.getInstrumentation().runOnMainSync {
          viewModel.getOtherAccountData(
              id = "non_existent_account_id",
              onResult = {
                  resultAccount = it
                  latch.countDown()
              },
              onDeleted = {
                  deletedCalled = true
                  latch.countDown()
              })
      }

      latch.await(2, TimeUnit.SECONDS)
      Assert.assertTrue("onDeleted should be called", deletedCalled)
      Assert.assertEquals(null, resultAccount)
  }
}
package com.github.meeplemeet.Authentication

import android.app.Activity
import android.content.Context
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.github.meeplemeet.R
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.auth.AuthUIState
import com.github.meeplemeet.model.auth.AuthenticationRepository
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
  private lateinit var viewModel: AuthViewModel
  private val repo: AuthenticationRepository = mockk(relaxed = true)
  private val testDispatcher = StandardTestDispatcher()
  private val testScope = TestScope(testDispatcher)

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    viewModel = AuthViewModel(repo)
  }

  @Test
  fun `initial state is default`() =
      testScope.runTest {
        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals(null, state.account)
        assertEquals(null, state.errorMsg)
        assertEquals(false, state.signedOut)
      }

  @Test
  fun `clearErrorMsg sets errorMsg to null`() =
      testScope.runTest {
        // Set errorMsg manually
        viewModel.clearErrorMsg()
        assertEquals(null, viewModel.uiState.value.errorMsg)
      }

  @Test
  fun `registerWithEmail success updates account`() =
      testScope.runTest {
        val fakeAccount = mockk<Account>()
        coEvery { repo.registerWithEmail(any(), any()) } returns Result.success(fakeAccount)
        viewModel.registerWithEmail("test@example.com", "password")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(fakeAccount, viewModel.uiState.value.account)
        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(null, viewModel.uiState.value.errorMsg)
      }

  @Test
  fun `registerWithEmail failure updates errorMsg`() =
      testScope.runTest {
        val error = Exception("Registration failed")
        coEvery { repo.registerWithEmail(any(), any()) } returns Result.failure(error)
        viewModel.registerWithEmail("test@example.com", "password")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.account)
        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals("Registration failed", viewModel.uiState.value.errorMsg)
      }

  @Test
  fun `loginWithEmail success updates account`() =
      testScope.runTest {
        val fakeAccount = mockk<Account>()
        coEvery { repo.loginWithEmail(any(), any()) } returns Result.success(fakeAccount)
        viewModel.loginWithEmail("test@example.com", "password")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(fakeAccount, viewModel.uiState.value.account)
        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(null, viewModel.uiState.value.errorMsg)
      }

  @Test
  fun `loginWithEmail failure updates errorMsg`() =
      testScope.runTest {
        val error = Exception("Login failed")
        coEvery { repo.loginWithEmail(any(), any()) } returns Result.failure(error)
        viewModel.loginWithEmail("test@example.com", "password")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.account)
        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals("Login failed", viewModel.uiState.value.errorMsg)
      }

  @Test
  fun `isLoading is true during registerWithEmail and false after`() =
      testScope.runTest {
        val fakeAccount = mockk<Account>()
        coEvery { repo.registerWithEmail(any(), any()) } coAnswers
            {
              // Check isLoading is true during suspend
              assertEquals(true, viewModel.uiState.value.isLoading)
              Result.success(fakeAccount)
            }
        viewModel.registerWithEmail("test@example.com", "password")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.isLoading)
      }

  @Test
  fun `registerWithEmail does not proceed if already loading`() =
      testScope.runTest {
        // Set isLoading to true using reflection
        val field = viewModel.javaClass.getDeclaredField("_uiState")
        field.isAccessible = true
        val stateFlow = field.get(viewModel) as MutableStateFlow<AuthUIState>
        stateFlow.value = viewModel.uiState.value.copy(isLoading = true)

        // Should not call repo
        coEvery { repo.registerWithEmail(any(), any()) } throws
            AssertionError("Should not be called")
        viewModel.registerWithEmail("test@example.com", "password")
        // No exception means pass
      }

  @Test
  fun `loginWithEmail does not proceed if already loading`() =
      testScope.runTest {
        // Set isLoading to true using reflection
        val field = viewModel.javaClass.getDeclaredField("_uiState")
        field.isAccessible = true
        val stateFlow = field.get(viewModel) as MutableStateFlow<AuthUIState>
        stateFlow.value = viewModel.uiState.value.copy(isLoading = true)

        // Should not call repo
        coEvery { repo.registerWithEmail(any(), any()) } throws
            AssertionError("Should not be called")
        viewModel.loginWithEmail("test@example.com", "password")
        // No exception means pass
      }

  @Test
  fun `googleSignIn does not proceed if already loading`() =
      testScope.runTest {
        val context = mockk<Context>(relaxed = true)
        val credentialManager = mockk<CredentialManager>(relaxed = true)
        val viewModel = AuthViewModel(repo)
        val field = viewModel.javaClass.getDeclaredField("_uiState")
        field.isAccessible = true
        val stateFlow = field.get(viewModel) as MutableStateFlow<AuthUIState>
        stateFlow.value = viewModel.uiState.value.copy(isLoading = true)
        viewModel.googleSignIn(context, credentialManager)
        // No exception means pass, and state does not change
        assertEquals(true, viewModel.uiState.value.isLoading)
      }

  @Test
  fun `clearErrorMsg only clears errorMsg`() =
      testScope.runTest {
        // Simulate error state
        val errorState =
            viewModel.uiState.value.copy(
                errorMsg = "Some error", isLoading = true, signedOut = true)
        // Manually set state (not typical, but for test)
        val field = viewModel.javaClass.getDeclaredField("_uiState")
        field.isAccessible = true
        val stateFlow = field.get(viewModel) as MutableStateFlow<AuthUIState>
        stateFlow.value = errorState
        viewModel.clearErrorMsg()
        val newState = viewModel.uiState.value
        assertEquals(null, newState.errorMsg)
        assertEquals(true, newState.isLoading)
        assertEquals(true, newState.signedOut)
      }

  @Test
  fun `clearErrorMsg after error clears only errorMsg`() =
      testScope.runTest {
        val error = Exception("Login failed")
        coEvery { repo.loginWithEmail(any(), any()) } returns Result.failure(error)
        viewModel.loginWithEmail("test@example.com", "password")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Login failed", viewModel.uiState.value.errorMsg)
        viewModel.clearErrorMsg()
        assertEquals(null, viewModel.uiState.value.errorMsg)
      }

  @Test
  fun `logout success updates signedOut`() =
      testScope.runTest {
        coEvery { repo.logout() } returns Result.success(Unit)
        viewModel.signOut()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.signedOut)
        assertEquals(null, viewModel.uiState.value.account)
        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(null, viewModel.uiState.value.errorMsg)
      }

  @Test
  fun `logout failure updates errorMsg`() =
      testScope.runTest {
        val error = Exception("Logout failed")
        coEvery { repo.logout() } returns Result.failure(error)
        viewModel.signOut()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.signedOut)
        assertEquals("Logout failed", viewModel.uiState.value.errorMsg)
        assertEquals(false, viewModel.uiState.value.isLoading)
      }

  @Test
  fun `registerWithEmail sets isLoading true then false`() =
      testScope.runTest {
        val fakeAccount = mockk<Account>()
        coEvery { repo.registerWithEmail(any(), any()) } coAnswers
            {
              assertEquals(true, viewModel.uiState.value.isLoading)
              Result.success(fakeAccount)
            }
        viewModel.registerWithEmail("test@example.com", "password")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.isLoading)
      }

  @Test
  fun `loginWithEmail sets isLoading true then false`() =
      testScope.runTest {
        val fakeAccount = mockk<Account>()
        coEvery { repo.loginWithEmail(any(), any()) } coAnswers
            {
              assertEquals(true, viewModel.uiState.value.isLoading)
              Result.success(fakeAccount)
            }
        viewModel.loginWithEmail("test@example.com", "password")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.isLoading)
      }

  // ==================== ADDITIONAL COMPREHENSIVE TESTS ====================

  // Google Sign-In Tests
  @Test
  fun `googleSignIn success updates account state correctly`() =
      testScope.runTest {
        val fakeAccount = mockk<Account>()
        val mockActivity = mockk<Activity>(relaxed = true)
        val mockCredentialManager = mockk<CredentialManager>(relaxed = true)
        val mockCredential = mockk<Credential>(relaxed = true)
        val mockResponse = mockk<GetCredentialResponse>(relaxed = true)
        val mockAvailability = mockk<GoogleApiAvailability>(relaxed = true)

        // Mock direct string resource access for default_web_client_id
        every { mockActivity.getString(R.string.default_web_client_id) } returns "test_client_id"

        // Mock Google Play Services availability
        mockkStatic(GoogleApiAvailability::class)
        every { GoogleApiAvailability.getInstance() } returns mockAvailability
        every { mockAvailability.isGooglePlayServicesAvailable(mockActivity) } returns
            ConnectionResult.SUCCESS

        // Mock credential manager
        every { mockResponse.credential } returns mockCredential
        coEvery {
          mockCredentialManager.getCredential(any<Context>(), any<GetCredentialRequest>())
        } returns mockResponse

        // Mock repository success
        coEvery { repo.loginWithGoogle(mockCredential) } returns Result.success(fakeAccount)

        viewModel.googleSignIn(mockActivity, mockCredentialManager)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(fakeAccount, state.account)
        assertEquals(false, state.isLoading)
        assertEquals(null, state.errorMsg)
        assertEquals(false, state.signedOut)
      }

  @Test
  fun `googleSignIn with non-Activity context shows error`() =
      testScope.runTest {
        val mockContext = mockk<Context>(relaxed = true)
        val mockCredentialManager = mockk<CredentialManager>(relaxed = true)

        viewModel.googleSignIn(mockContext, mockCredentialManager)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Google sign-in requires an Activity context.", state.errorMsg)
        assertEquals(false, state.isLoading)
        assertEquals(true, state.signedOut)
        assertEquals(null, state.account)
      }

  @Test
  fun `googleSignIn with Google Play Services unavailable shows error`() =
      testScope.runTest {
        val mockActivity = mockk<Activity>(relaxed = true)
        val mockCredentialManager = mockk<CredentialManager>(relaxed = true)
        val mockAvailability = mockk<GoogleApiAvailability>(relaxed = true)

        mockkStatic(GoogleApiAvailability::class)
        every { GoogleApiAvailability.getInstance() } returns mockAvailability
        every { mockAvailability.isGooglePlayServicesAvailable(mockActivity) } returns
            ConnectionResult.SERVICE_MISSING

        viewModel.googleSignIn(mockActivity, mockCredentialManager)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.errorMsg!!.contains("Google Play services not available"))
        assertEquals(false, state.isLoading)
        assertEquals(true, state.signedOut)
        assertEquals(null, state.account)
      }

  @Test
  fun `googleSignIn user cancellation shows appropriate error`() =
      testScope.runTest {
        val mockActivity = mockk<Activity>(relaxed = true)
        val mockCredentialManager = mockk<CredentialManager>(relaxed = true)
        val mockAvailability = mockk<GoogleApiAvailability>(relaxed = true)

        // Mock direct string resource access for default_web_client_id
        every { mockActivity.getString(R.string.default_web_client_id) } returns "test_client_id"

        mockkStatic(GoogleApiAvailability::class)
        every { GoogleApiAvailability.getInstance() } returns mockAvailability
        every { mockAvailability.isGooglePlayServicesAvailable(mockActivity) } returns
            ConnectionResult.SUCCESS

        // Mock credential manager to throw cancellation exception
        coEvery {
          mockCredentialManager.getCredential(any<Context>(), any<GetCredentialRequest>())
        } throws GetCredentialCancellationException("User cancelled")

        viewModel.googleSignIn(mockActivity, mockCredentialManager)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Sign-in cancelled", state.errorMsg)
        assertEquals(false, state.isLoading)
        assertEquals(true, state.signedOut)
        assertEquals(null, state.account)
      }

  @Test
  fun `googleSignIn no credential available shows appropriate error`() =
      testScope.runTest {
        val mockActivity = mockk<Activity>(relaxed = true)
        val mockCredentialManager = mockk<CredentialManager>(relaxed = true)
        val mockAvailability = mockk<GoogleApiAvailability>(relaxed = true)

        // Mock direct string resource access for default_web_client_id
        every { mockActivity.getString(R.string.default_web_client_id) } returns "test_client_id"

        mockkStatic(GoogleApiAvailability::class)
        every { GoogleApiAvailability.getInstance() } returns mockAvailability
        every { mockAvailability.isGooglePlayServicesAvailable(mockActivity) } returns
            ConnectionResult.SUCCESS

        // Mock credential manager to throw no credential exception
        coEvery {
          mockCredentialManager.getCredential(any<Context>(), any<GetCredentialRequest>())
        } throws NoCredentialException("No credential found")

        viewModel.googleSignIn(mockActivity, mockCredentialManager)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.errorMsg!!.contains("No Google account available"))
        assertEquals(false, state.isLoading)
        assertEquals(true, state.signedOut)
        assertEquals(null, state.account)
      }

  @Test
  fun `googleSignIn unexpected exception shows appropriate error`() =
      testScope.runTest {
        val mockActivity = mockk<Activity>(relaxed = true)
        val mockCredentialManager = mockk<CredentialManager>(relaxed = true)
        val mockAvailability = mockk<GoogleApiAvailability>(relaxed = true)

        // Mock direct string resource access for default_web_client_id
        every { mockActivity.getString(R.string.default_web_client_id) } returns "test_client_id"

        mockkStatic(GoogleApiAvailability::class)
        every { GoogleApiAvailability.getInstance() } returns mockAvailability
        every { mockAvailability.isGooglePlayServicesAvailable(mockActivity) } returns
            ConnectionResult.SUCCESS

        // Mock credential manager to throw unexpected exception
        coEvery {
          mockCredentialManager.getCredential(any<Context>(), any<GetCredentialRequest>())
        } throws RuntimeException("Unexpected error")

        viewModel.googleSignIn(mockActivity, mockCredentialManager)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.errorMsg!!.contains("Unexpected error"))
        assertEquals(false, state.isLoading)
        assertEquals(true, state.signedOut)
        assertEquals(null, state.account)
      }
}

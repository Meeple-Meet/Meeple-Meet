package com.github.meeplemeet.Authentication

import androidx.credentials.CustomCredential
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.systems.FirestoreRepository
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AuthRepoFirebaseTest {
  // Helper functions for completed and failed Tasks
  private fun <T> completedTask(result: T): Task<T> = Tasks.forResult(result)

  private fun <T> failedTask(exception: Exception): Task<T> = Tasks.forException(exception)

  //// Tests for registerWithEmail ////
  @Test
  fun `registerWithEmail returns failure for invalid email`() = runBlocking {
    val mockAuth = mock<FirebaseAuth>()
    val mockFirestore = mock<FirestoreRepository>()

    whenever(mockAuth.createUserWithEmailAndPassword(any(), any()))
        .thenReturn(failedTask(Exception("Invalid email")))

    val repo =
        AuthRepoFirebase(auth = mockAuth, helper = mock(), firestoreRepository = mockFirestore)
    val result = repo.registerWithEmail("invalid-email", "password123")

    assertTrue(result.isFailure)
  }

  @Test
  fun `registerWithEmail returns failure for empty password`() = runBlocking {
    val mockAuth = mock<FirebaseAuth>()
    val mockFirestore = mock<FirestoreRepository>()

    whenever(mockAuth.createUserWithEmailAndPassword(any(), any()))
        .thenReturn(failedTask(Exception("Empty password")))

    val repo =
        AuthRepoFirebase(auth = mockAuth, helper = mock(), firestoreRepository = mockFirestore)
    val result = repo.registerWithEmail("test@example.com", "")

    assertTrue(result.isFailure)
  }

  @Test
  fun `registerWithEmail returns success for valid email and password`() = runBlocking {
    val mockAuth = mock<FirebaseAuth>()
    val mockAuthResult = mock<AuthResult>()
    val mockUser = mock<FirebaseUser>()
    val mockFirestore = mock<FirestoreRepository>()
    val email = "test@example.com"
    val password = "password123"
    val uid = "uid123"
    val expectedAccount = Account(uid, "test", emptyMap(), email, null, null)

    whenever(mockAuth.createUserWithEmailAndPassword(email, password))
        .thenReturn(completedTask(mockAuthResult))
    whenever(mockAuthResult.user).thenReturn(mockUser)
    whenever(mockUser.uid).thenReturn(uid)
    whenever(mockUser.email).thenReturn(email)
    whenever(mockUser.photoUrl).thenReturn(null)
    whenever(mockFirestore.createAccount(name = "test", email = email, photoUrl = null))
        .thenReturn(expectedAccount)

    val repo =
        AuthRepoFirebase(auth = mockAuth, helper = mock(), firestoreRepository = mockFirestore)
    val result = repo.registerWithEmail(email, password)

    assertTrue(result.isSuccess)
    assertEquals(uid, result.getOrNull()?.uid)
    assertEquals(email, result.getOrNull()?.email)
  }

  @Test
  fun `registerWithEmail returns failure if createUserWithEmailAndPassword returns null user`() =
      runBlocking {
        val mockAuth = mock<FirebaseAuth>()
        val mockAuthResult = mock<AuthResult>()
        val mockFirestore = mock<FirestoreRepository>()

        whenever(mockAuth.createUserWithEmailAndPassword(any(), any()))
            .thenReturn(completedTask(mockAuthResult))
        whenever(mockAuthResult.user).thenReturn(null)

        val repo =
            AuthRepoFirebase(auth = mockAuth, helper = mock(), firestoreRepository = mockFirestore)
        val result = repo.registerWithEmail("test@example.com", "Password!123")

        assertTrue(result.isFailure)
      }

  //// Tests for loginWithEmail ////
  @Test
  fun `loginWithEmail returns failure for invalid credentials`() = runBlocking {
    val mockAuth = mock<FirebaseAuth>()
    val mockFirestore = mock<FirestoreRepository>()

    whenever(mockAuth.signInWithEmailAndPassword(any(), any()))
        .thenReturn(failedTask(Exception("Invalid credentials")))

    val repo =
        AuthRepoFirebase(auth = mockAuth, helper = mock(), firestoreRepository = mockFirestore)
    val result = repo.loginWithEmail("test@example.com", "wrongpassword")

    assertTrue(result.isFailure)
  }

  @Test
  fun `loginWithEmail returns success for valid credentials`() = runBlocking {
    val mockAuth = mock<FirebaseAuth>()
    val mockAuthResult = mock<AuthResult>()
    val mockUser = mock<FirebaseUser>()
    val mockFirestore = mock<FirestoreRepository>()
    val email = "test@example.com"
    val password = "password123"
    val uid = "uid123"
    val expectedAccount = Account(uid, "test", emptyMap(), email, null, null)

    whenever(mockAuth.signInWithEmailAndPassword(email, password))
        .thenReturn(completedTask(mockAuthResult))
    whenever(mockAuthResult.user).thenReturn(mockUser)
    whenever(mockUser.uid).thenReturn(uid)
    whenever(mockUser.email).thenReturn(email)
    whenever(mockUser.photoUrl).thenReturn(null)
    whenever(mockFirestore.getAccount(uid)).thenReturn(expectedAccount)

    val repo =
        AuthRepoFirebase(auth = mockAuth, helper = mock(), firestoreRepository = mockFirestore)
    val result = repo.loginWithEmail(email, password)

    assertTrue(result.isSuccess)
    assertEquals(uid, result.getOrNull()?.uid)
    assertEquals(email, result.getOrNull()?.email)
  }

  @Test
  fun `loginWithEmail returns failure if FirestoreRepository getAccount throws`() = runBlocking {
    val mockAuth = mock<FirebaseAuth>()
    val mockAuthResult = mock<AuthResult>()
    val mockUser = mock<FirebaseUser>()
    val mockFirestore = mock<FirestoreRepository>()
    val email = "test@example.com"
    val password = "password123"
    val uid = "uid123"

    whenever(mockAuth.signInWithEmailAndPassword(email, password))
        .thenReturn(completedTask(mockAuthResult))
    whenever(mockAuthResult.user).thenReturn(mockUser)
    whenever(mockUser.uid).thenReturn(uid)
    whenever(mockFirestore.getAccount(uid)).thenThrow(RuntimeException("Account not found"))

    val repo =
        AuthRepoFirebase(auth = mockAuth, helper = mock(), firestoreRepository = mockFirestore)
    val result = repo.loginWithEmail(email, password)

    assertTrue(result.isFailure)
  }

  @Test
  fun `loginWithEmail returns failure if signInWithEmailAndPassword returns null user`() =
      runBlocking {
        val mockAuth = mock<FirebaseAuth>()
        val mockAuthResult = mock<AuthResult>()
        val mockFirestore = mock<FirestoreRepository>()

        whenever(mockAuth.signInWithEmailAndPassword(any(), any()))
            .thenReturn(completedTask(mockAuthResult))
        whenever(mockAuthResult.user).thenReturn(null)

        val repo =
            AuthRepoFirebase(auth = mockAuth, helper = mock(), firestoreRepository = mockFirestore)
        val result = repo.loginWithEmail("test@example.com", "Password!123")

        assertTrue(result.isFailure)
      }

  //// Tests for logout ////
  @Test
  fun `logout returns success`() = runBlocking {
    val mockAuth = mock<FirebaseAuth>()
    val mockFirestore = mock<FirestoreRepository>()

    val repo =
        AuthRepoFirebase(auth = mockAuth, helper = mock(), firestoreRepository = mockFirestore)
    val result = repo.logout()

    assertTrue(result.isSuccess)
  }

  @Test
  fun `logout returns failure if signOut throws`() = runBlocking {
    val mockAuth = mock<FirebaseAuth>()
    val mockFirestore = mock<FirestoreRepository>()

    whenever(mockAuth.signOut()).thenThrow(RuntimeException("signOut failed"))

    val repo =
        AuthRepoFirebase(auth = mockAuth, helper = mock(), firestoreRepository = mockFirestore)
    val result = repo.logout()

    assertTrue(result.isFailure)
  }

  @Test
  fun `logout actually calls signOut on FirebaseAuth`() = runBlocking {
    val mockAuth = mock<FirebaseAuth>()
    val mockFirestore = mock<FirestoreRepository>()

    val repo =
        AuthRepoFirebase(auth = mockAuth, helper = mock(), firestoreRepository = mockFirestore)
    repo.logout()

    org.mockito.kotlin.verify(mockAuth).signOut()
  }

  //// Tests for loginWithGoogle ////
  @Test
  fun `loginWithGoogle returns failure for invalid credential type`() = runBlocking {
    val mockAuth = mock<FirebaseAuth>()
    val mockFirestore = mock<FirestoreRepository>()
    val credential = mock<androidx.credentials.Credential>()

    val repo =
        AuthRepoFirebase(auth = mockAuth, helper = mock(), firestoreRepository = mockFirestore)
    val result = repo.loginWithGoogle(credential)

    assertTrue(result.isFailure)
  }

  @Test
  fun `loginWithGoogle returns failure for CustomCredential with wrong type`() = runBlocking {
    val mockAuth = mock<FirebaseAuth>()
    val mockFirestore = mock<FirestoreRepository>()
    val credential = mock<androidx.credentials.CustomCredential>()

    whenever(credential.type).thenReturn("WRONG_TYPE")

    val repo =
        AuthRepoFirebase(auth = mockAuth, helper = mock(), firestoreRepository = mockFirestore)
    val result = repo.loginWithGoogle(credential)

    assertTrue(result.isFailure)
  }

  @Test
  fun `loginWithGoogle returns success when account exists`() = runBlocking {
    val mockAuth = mock<FirebaseAuth>()
    val mockHelper = mock<GoogleSignInHelper>()
    val mockFirestore = mock<FirestoreRepository>()
    val mockUser = mock<FirebaseUser>()
    val mockAuthResult = mock<AuthResult>()
    val credential =
        CustomCredential(
            type =
                com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
                    .TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
            data = android.os.Bundle())
    val mockIdTokenCredential =
        mock<com.google.android.libraries.identity.googleid.GoogleIdTokenCredential>()
    val mockFirebaseCredential = mock<com.google.firebase.auth.AuthCredential>()
    val uid = "uid123"
    val email = "test@example.com"
    val expectedAccount = Account(uid, "test", emptyMap(), email, null, null)

    whenever(mockHelper.extractIdTokenCredential(any())).thenReturn(mockIdTokenCredential)
    whenever(mockIdTokenCredential.idToken).thenReturn("idtoken")
    whenever(mockHelper.toFirebaseCredential(any())).thenReturn(mockFirebaseCredential)
    whenever(mockAuth.signInWithCredential(mockFirebaseCredential))
        .thenReturn(completedTask(mockAuthResult))
    whenever(mockAuthResult.user).thenReturn(mockUser)
    whenever(mockUser.uid).thenReturn(uid)
    whenever(mockUser.email).thenReturn(email)
    whenever(mockUser.displayName).thenReturn("test")
    whenever(mockUser.photoUrl).thenReturn(null)
    whenever(mockFirestore.getAccount(uid)).thenReturn(expectedAccount)

    val repo =
        AuthRepoFirebase(auth = mockAuth, helper = mockHelper, firestoreRepository = mockFirestore)
    val result = repo.loginWithGoogle(credential)

    assertTrue(result.isSuccess)
    assertEquals(uid, result.getOrNull()?.uid)
    assertEquals(email, result.getOrNull()?.email)
  }

  @Test
  fun `loginWithGoogle returns failure if signInWithCredential fails`() = runBlocking {
    val mockAuth = mock<FirebaseAuth>()
    val mockHelper = mock<GoogleSignInHelper>()
    val mockFirestore = mock<FirestoreRepository>()
    val credential =
        CustomCredential(
            type =
                com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
                    .TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
            data = android.os.Bundle())
    val mockIdTokenCredential =
        mock<com.google.android.libraries.identity.googleid.GoogleIdTokenCredential>()
    val mockFirebaseCredential = mock<com.google.firebase.auth.AuthCredential>()

    whenever(mockHelper.extractIdTokenCredential(any())).thenReturn(mockIdTokenCredential)
    whenever(mockIdTokenCredential.idToken).thenReturn("idtoken")
    whenever(mockHelper.toFirebaseCredential(any())).thenReturn(mockFirebaseCredential)
    whenever(mockAuth.signInWithCredential(mockFirebaseCredential))
        .thenReturn(failedTask(Exception("Sign in failed")))

    val repo =
        AuthRepoFirebase(auth = mockAuth, helper = mockHelper, firestoreRepository = mockFirestore)
    val result = repo.loginWithGoogle(credential)

    assertTrue(result.isFailure)
  }

  @Test
  fun `loginWithGoogle returns failure if user is null after signInWithCredential`() = runBlocking {
    val mockAuth = mock<FirebaseAuth>()
    val mockHelper = mock<GoogleSignInHelper>()
    val mockFirestore = mock<FirestoreRepository>()
    val credential =
        CustomCredential(
            type =
                com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
                    .TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
            data = android.os.Bundle())
    val mockIdTokenCredential =
        mock<com.google.android.libraries.identity.googleid.GoogleIdTokenCredential>()
    val mockFirebaseCredential = mock<com.google.firebase.auth.AuthCredential>()
    val mockAuthResult = mock<AuthResult>()

    whenever(mockHelper.extractIdTokenCredential(any())).thenReturn(mockIdTokenCredential)
    whenever(mockIdTokenCredential.idToken).thenReturn("idtoken")
    whenever(mockHelper.toFirebaseCredential(any())).thenReturn(mockFirebaseCredential)
    whenever(mockAuth.signInWithCredential(mockFirebaseCredential))
        .thenReturn(completedTask(mockAuthResult))
    whenever(mockAuthResult.user).thenReturn(null)

    val repo =
        AuthRepoFirebase(auth = mockAuth, helper = mockHelper, firestoreRepository = mockFirestore)
    val result = repo.loginWithGoogle(credential)

    assertTrue(result.isFailure)
  }

  @Test
  fun `loginWithGoogle returns failure if FirestoreRepository getAccount throws`() = runBlocking {
    val mockAuth = mock<FirebaseAuth>()
    val mockHelper = mock<GoogleSignInHelper>()
    val mockFirestore = mock<FirestoreRepository>()
    val mockUser = mock<FirebaseUser>()
    val mockAuthResult = mock<AuthResult>()
    val credential =
        CustomCredential(
            type =
                com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
                    .TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
            data = android.os.Bundle())
    val mockIdTokenCredential =
        mock<com.google.android.libraries.identity.googleid.GoogleIdTokenCredential>()
    val mockFirebaseCredential = mock<com.google.firebase.auth.AuthCredential>()
    val uid = "uid123"

    whenever(mockHelper.extractIdTokenCredential(any())).thenReturn(mockIdTokenCredential)
    whenever(mockIdTokenCredential.idToken).thenReturn("idtoken")
    whenever(mockHelper.toFirebaseCredential(any())).thenReturn(mockFirebaseCredential)
    whenever(mockAuth.signInWithCredential(mockFirebaseCredential))
        .thenReturn(completedTask(mockAuthResult))
    whenever(mockAuthResult.user).thenReturn(mockUser)
    whenever(mockUser.uid).thenReturn(uid)
    whenever(mockFirestore.getAccount(uid)).thenThrow(RuntimeException("Account not found"))

    val repo =
        AuthRepoFirebase(auth = mockAuth, helper = mockHelper, firestoreRepository = mockFirestore)
    val result = repo.loginWithGoogle(credential)

    assertTrue(result.isFailure)
  }
}

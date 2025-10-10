package com.github.meeplemeet.Authentication

import android.os.Bundle
import androidx.credentials.CustomCredential
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever

class AuthRepoFirebaseTest {
  // Helper functions for completed and failed Tasks
  private fun <T> completedTask(result: T): Task<T> = Tasks.forResult(result)

  private fun <T> failedTask(exception: Exception): Task<T> = Tasks.forException(exception)

  //// Tests for registerWithEmail ////
  @Test
  fun `registerWithEmail returns failure for invalid email`() = runBlocking {
    // Arrange: Use a mock FirebaseAuth and Firestore if needed, or test the real implementation
    val repo = AuthRepoFirebase(auth = mock(), helper = mock(), firestore = mock())
    // Act
    val result = repo.registerWithEmail("invalid-email", "password123")
    // Assert
    assertTrue(result.isFailure)
  }

  @Test
  fun `registerWithEmail returns failure for empty password`() = runBlocking {
    val repo = AuthRepoFirebase(auth = mock(), helper = mock(), firestore = mock())
    val result = repo.registerWithEmail("test@example.com", "")
    assertTrue(result.isFailure)
  }

  @Test
  fun `registerWithEmail returns failure if Firestore set throws`() = runBlocking {
    val mockAuth = mock<FirebaseAuth>()
    val mockAuthResult = mock<AuthResult>()
    val mockUser = mock<FirebaseUser>()
    val mockFirestore = mock<FirebaseFirestore>()
    val mockCollection = mock<CollectionReference>()
    val mockDocument = mock<DocumentReference>()
    val mockTaskAuthResult = completedTask(mockAuthResult)
    val email = "test@example.com"
    val password = "password123"
    val uid = "uid123"

    whenever(mockAuth.createUserWithEmailAndPassword(email, password))
        .thenReturn(mockTaskAuthResult)
    whenever(mockAuthResult.user).thenReturn(mockUser)
    whenever(mockUser.uid).thenReturn(uid)
    whenever(mockUser.email).thenReturn(email)
    whenever(mockUser.photoUrl).thenReturn(null)
    whenever(mockFirestore.collection("users")).thenReturn(mockCollection)
    whenever(mockCollection.document(uid)).thenReturn(mockDocument)
    whenever(mockDocument.set(any())).thenThrow(RuntimeException("Firestore set failed"))

    val repo = AuthRepoFirebase(auth = mockAuth, helper = mock(), firestore = mockFirestore)
    val result = repo.registerWithEmail(email, password)
    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()?.message?.contains("Firestore set failed") == true)
  }

  @Test
  fun `registerWithEmail returns success for valid email and password`() = runBlocking {
    val mockAuth = mock<FirebaseAuth>()
    val mockAuthResult = mock<AuthResult>()
    val mockUser = mock<FirebaseUser>()
    val mockFirestore = mock<FirebaseFirestore>()
    val mockCollection = mock<CollectionReference>()
    val mockDocument = mock<DocumentReference>()
    val mockTaskAuthResult = completedTask(mockAuthResult)
    val email = "test@example.com"
    val password = "password123"
    val uid = "uid123"

    whenever(mockAuth.createUserWithEmailAndPassword(email, password))
        .thenReturn(mockTaskAuthResult)
    whenever(mockAuthResult.user).thenReturn(mockUser)
    whenever(mockUser.uid).thenReturn(uid)
    whenever(mockUser.email).thenReturn(email)
    whenever(mockUser.photoUrl).thenReturn(null)
    whenever(mockFirestore.collection("users")).thenReturn(mockCollection)
    whenever(mockCollection.document(uid)).thenReturn(mockDocument)
    whenever(mockDocument.set(any())).thenReturn(completedTask(null))

    val repo = AuthRepoFirebase(auth = mockAuth, helper = mock(), firestore = mockFirestore)
    val result = repo.registerWithEmail(email, password)
    assertTrue(result.isSuccess)
  }

  @Test
  fun `registerWithEmail returns failure if createUserWithEmailAndPassword fails`() = runBlocking {
    val mockAuth = mock<FirebaseAuth>()
    val mockFirestore = mock<FirebaseFirestore>()
    val email = "test@example.com"
    val password = "password123"
    val exception = RuntimeException("Auth failed!")

    whenever(mockAuth.createUserWithEmailAndPassword(email, password))
        .thenReturn(failedTask(exception))

    val repo = AuthRepoFirebase(auth = mockAuth, helper = mock(), firestore = mockFirestore)
    val result = repo.registerWithEmail(email, password)
    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()?.message?.contains("Auth failed!") == true)
  }

  @Test
  fun `registerWithEmail returns failure if createUserWithEmailAndPassword returns null user`() =
      runBlocking {
        val mockAuth = mock<FirebaseAuth>()
        val mockAuthResult = mock<AuthResult>()
        val mockFirestore = mock<FirebaseFirestore>()
        whenever(mockAuth.createUserWithEmailAndPassword(any(), any()))
            .thenReturn(completedTask(mockAuthResult))
        whenever(mockAuthResult.user).thenReturn(null)
        val repo = AuthRepoFirebase(auth = mockAuth, helper = mock(), firestore = mockFirestore)
        val result = repo.registerWithEmail("test@example.com", "Password!123")
        assertTrue(result.isFailure)
      }

  //// Tests for loginWithEmail ////
  @Test
  fun `loginWithEmail returns user with null description if Firestore get throws`() = runBlocking {
    val mockAuth = mock<FirebaseAuth>()
    val mockAuthResult = mock<AuthResult>()
    val mockUser = mock<FirebaseUser>()
    val mockFirestore = mock<FirebaseFirestore>()
    val mockCollection = mock<CollectionReference>()
    val mockDocument = mock<DocumentReference>()
    val mockTaskAuthResult = completedTask(mockAuthResult)
    val email = "test@example.com"
    val password = "password123"
    val uid = "uid123"
    val name = "test"

    whenever(mockAuth.signInWithEmailAndPassword(email, password)).thenReturn(mockTaskAuthResult)
    whenever(mockAuthResult.user).thenReturn(mockUser)
    whenever(mockUser.uid).thenReturn(uid)
    whenever(mockUser.email).thenReturn(email)
    whenever(mockUser.displayName).thenReturn(name)
    whenever(mockUser.photoUrl).thenReturn(null)
    whenever(mockFirestore.collection("users")).thenReturn(mockCollection)
    whenever(mockCollection.document(uid)).thenReturn(mockDocument)
    whenever(mockDocument.get()).thenThrow(RuntimeException("Firestore get failed"))

    val repo = AuthRepoFirebase(auth = mockAuth, helper = mock(), firestore = mockFirestore)
    val result = repo.loginWithEmail(email, password)
    assertTrue(result.isSuccess)
    assertTrue(result.getOrNull()?.description == null)
  }

  @Test
  fun `loginWithEmail returns failure for invalid credentials`() = runBlocking {
    val repo = AuthRepoFirebase(auth = mock(), helper = mock(), firestore = mock())
    val result = repo.loginWithEmail("notanemail@example.com", "wrongpassword")
    assertTrue(result.isFailure)
  }

  @Test
  fun `loginWithEmail returns failure for empty password`() = runBlocking {
    val repo = AuthRepoFirebase(auth = mock(), helper = mock(), firestore = mock())
    val result = repo.loginWithEmail("test@example.com", "")
    assertTrue(result.isFailure)
  }

  @Test
  fun `loginWithEmail returns success for valid credentials`() = runBlocking {
    val mockAuth = mock<FirebaseAuth>()
    val mockAuthResult = mock<AuthResult>()
    val mockUser = mock<FirebaseUser>()
    val mockFirestore = mock<FirebaseFirestore>()
    val mockCollection = mock<CollectionReference>()
    val mockDocument = mock<DocumentReference>()
    val email = "test@example.com"
    val password = "password123"
    val uid = "uid123"
    val name = "test"
    val description = "desc"

    whenever(mockAuth.signInWithEmailAndPassword(email, password))
        .thenReturn(completedTask(mockAuthResult))
    whenever(mockAuthResult.user).thenReturn(mockUser)
    whenever(mockUser.uid).thenReturn(uid)
    whenever(mockUser.email).thenReturn(email)
    whenever(mockUser.displayName).thenReturn(name)
    whenever(mockUser.photoUrl).thenReturn(null)
    whenever(mockFirestore.collection("users")).thenReturn(mockCollection)
    whenever(mockCollection.document(uid)).thenReturn(mockDocument)
    val mockDocSnap = mock<com.google.firebase.firestore.DocumentSnapshot>()
    whenever(mockDocument.get()).thenReturn(completedTask(mockDocSnap))
    whenever(mockDocSnap.exists()).thenReturn(true)
    whenever(mockDocSnap.getString("description")).thenReturn(description)

    val repo = AuthRepoFirebase(auth = mockAuth, helper = mock(), firestore = mockFirestore)
    val result = repo.loginWithEmail(email, password)
    assertTrue(result.isSuccess)
    assertTrue(result.getOrNull()?.description == description)
  }

  @Test
  fun `loginWithEmail returns failure if signInWithEmailAndPassword fails`() = runBlocking {
    val mockAuth = mock<FirebaseAuth>()
    val mockFirestore = mock<FirebaseFirestore>()
    val email = "test@example.com"
    val password = "password123"
    val exception = RuntimeException("Sign in failed!")

    whenever(mockAuth.signInWithEmailAndPassword(email, password)).thenReturn(failedTask(exception))

    val repo = AuthRepoFirebase(auth = mockAuth, helper = mock(), firestore = mockFirestore)
    val result = repo.loginWithEmail(email, password)
    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()?.message?.contains("Sign in failed!") == true)
  }

  @Test
  fun `loginWithEmail returns failure if signInWithEmailAndPassword returns null user`() =
      runBlocking {
        val mockAuth = mock<FirebaseAuth>()
        val mockAuthResult = mock<AuthResult>()
        val mockFirestore = mock<FirebaseFirestore>()
        whenever(mockAuth.signInWithEmailAndPassword(any(), any()))
            .thenReturn(completedTask(mockAuthResult))
        whenever(mockAuthResult.user).thenReturn(null)
        val repo = AuthRepoFirebase(auth = mockAuth, helper = mock(), firestore = mockFirestore)
        val result = repo.loginWithEmail("test@example.com", "Password!123")
        assertTrue(result.isFailure)
      }

  //// Tests for getCurrentUser ////
  @Test
  fun `getCurrentUser returns null when not signed in`() = runBlocking {
    // Arrange: Use a mock FirebaseAuth and Firestore if needed, or test the real implementation
    val repo = AuthRepoFirebase(auth = mock(), helper = mock(), firestore = mock())
    // Act
    val user = repo.getCurrentUser()
    // Assert
    assertNull(user)
  }

  @Test
  fun `getCurrentUser returns user with null description if Firestore throws`() = runBlocking {
    val mockAuth = mock<FirebaseAuth>()
    val mockUser = mock<FirebaseUser>()
    val mockFirestore = mock<FirebaseFirestore>()
    val mockCollection = mock<CollectionReference>()
    val mockDocument = mock<DocumentReference>()
    val uid = "uid123"
    val email = "test@example.com"
    val name = "test"

    whenever(mockAuth.currentUser).thenReturn(mockUser)
    whenever(mockUser.uid).thenReturn(uid)
    whenever(mockUser.email).thenReturn(email)
    whenever(mockUser.displayName).thenReturn(name)
    whenever(mockUser.photoUrl).thenReturn(null)
    whenever(mockFirestore.collection("users")).thenReturn(mockCollection)
    whenever(mockCollection.document(uid)).thenReturn(mockDocument)
    whenever(mockDocument.get()).thenThrow(RuntimeException("Firestore error"))

    val repo = AuthRepoFirebase(auth = mockAuth, helper = mock(), firestore = mockFirestore)
    val user = repo.getCurrentUser()
    assertTrue(user != null)
    assertTrue(user?.description == null)
  }

  @Test
  fun `getCurrentUser returns user when signed in`() = runBlocking {
    val mockAuth = mock<FirebaseAuth>()
    val mockUser = mock<FirebaseUser>()
    val mockFirestore = mock<FirebaseFirestore>()
    val mockCollection = mock<CollectionReference>()
    val mockDocument = mock<DocumentReference>()
    val uid = "uid123"
    val email = "test@example.com"
    val name = "test"
    val description = "desc"
    val mockDocSnap = mock<com.google.firebase.firestore.DocumentSnapshot>()

    whenever(mockAuth.currentUser).thenReturn(mockUser)
    whenever(mockUser.uid).thenReturn(uid)
    whenever(mockUser.email).thenReturn(email)
    whenever(mockUser.displayName).thenReturn(name)
    whenever(mockUser.photoUrl).thenReturn(null)
    whenever(mockFirestore.collection("users")).thenReturn(mockCollection)
    whenever(mockCollection.document(uid)).thenReturn(mockDocument)
    whenever(mockDocument.get()).thenReturn(completedTask(mockDocSnap))
    whenever(mockDocSnap.exists()).thenReturn(true)
    whenever(mockDocSnap.getString("description")).thenReturn(description)

    val repo = AuthRepoFirebase(auth = mockAuth, helper = mock(), firestore = mockFirestore)
    val user = repo.getCurrentUser()
    assertTrue(user != null)
    assertTrue(user?.description == description)
  }

  //// Tests for logout ////
  @Test
  fun `logout always returns success`() = runBlocking {
    // Arrange: Use a mock FirebaseAuth and Firestore if needed, or test the real implementation
    val repo = AuthRepoFirebase(auth = mock(), helper = mock(), firestore = mock())
    // Act
    val result = repo.logout()
    // Assert
    assertTrue(result.isSuccess)
  }

  @Test
  fun `logout returns failure if signOut throws`() = runBlocking {
    val mockAuth = mock<FirebaseAuth>()
    whenever(mockAuth.signOut()).thenThrow(RuntimeException("signOut failed"))
    val repo = AuthRepoFirebase(auth = mockAuth, helper = mock(), firestore = mock())
    val result = repo.logout()
    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()?.message?.contains("signOut failed") == true)
  }

  //// Tests for loginWithGoogle ////
  @Test
  fun `loginWithGoogle returns failure for invalid credential type`() = runBlocking {
    val repo = AuthRepoFirebase(auth = mock(), helper = mock(), firestore = mock())
    // Use a mock Credential that is not a CustomCredential or has wrong type
    val credential = mock<androidx.credentials.Credential>()
    val result = repo.loginWithGoogle(credential)
    assertTrue(result.isFailure)
  }

  @Test
  fun `loginWithGoogle returns failure for CustomCredential with wrong type`() = runBlocking {
    val repo = AuthRepoFirebase(auth = mock(), helper = mock(), firestore = mock())
    val credential = mock<androidx.credentials.CustomCredential>()
    whenever(credential.type).thenReturn("WRONG_TYPE")
    val result = repo.loginWithGoogle(credential)
    assertTrue(result.isFailure)
  }

  @Test
  fun `loginWithGoogle returns failure if credential is not CustomCredential`() = runBlocking {
    val repo = AuthRepoFirebase(auth = mock(), helper = mock(), firestore = mock())
    val credential = mock<androidx.credentials.Credential>()
    val result = repo.loginWithGoogle(credential)
    assertTrue(result.isFailure)
  }

  @Test
  fun `loginWithGoogle returns failure if credential type is wrong`() = runBlocking {
    val repo = AuthRepoFirebase(auth = mock(), helper = mock(), firestore = mock())
    val credential = CustomCredential(type = "WRONG_TYPE", data = Bundle())
    val result = repo.loginWithGoogle(credential)
    assertTrue(result.isFailure)
  }

  @Test
  fun `loginWithGoogle returns failure if extractIdTokenCredential throws`() = runBlocking {
    val mockHelper = mock<GoogleSignInHelper>()
    whenever(mockHelper.extractIdTokenCredential(any()))
        .thenThrow(RuntimeException("extract failed"))
    val repo = AuthRepoFirebase(auth = mock(), helper = mockHelper, firestore = mock())
    val credential = CustomCredential(type = "google_id_token", data = android.os.Bundle())
    val result = repo.loginWithGoogle(credential)
    assertTrue(result.isFailure)
  }

  @Test
  fun `loginWithGoogle returns failure if toFirebaseCredential throws`() = runBlocking {
    val mockHelper = mock<GoogleSignInHelper>()
    val mockIdTokenCredential =
        mock<com.google.android.libraries.identity.googleid.GoogleIdTokenCredential>()
    whenever(mockHelper.extractIdTokenCredential(any())).thenReturn(mockIdTokenCredential)
    whenever(mockIdTokenCredential.idToken).thenReturn("idtoken")
    whenever(mockHelper.toFirebaseCredential(any()))
        .thenThrow(RuntimeException("toFirebaseCredential failed"))
    val repo = AuthRepoFirebase(auth = mock(), helper = mockHelper, firestore = mock())
    val credential = CustomCredential(type = "google_id_token", data = android.os.Bundle())
    val result = repo.loginWithGoogle(credential)
    assertTrue(result.isFailure)
  }

  @Test
  fun `loginWithGoogle does not create user if Firestore doc exists`() = runBlocking {
    val mockAuth = mock<FirebaseAuth>()
    val mockHelper = mock<GoogleSignInHelper>()
    val mockFirestore = mock<FirebaseFirestore>()
    val mockCollection = mock<CollectionReference>()
    val mockDocument = mock<DocumentReference>()
    val mockUser = mock<FirebaseUser>()
    val mockAuthResult = mock<AuthResult>()
    val mockDocSnap = mock<com.google.firebase.firestore.DocumentSnapshot>()
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

    whenever(mockHelper.extractIdTokenCredential(any())).thenReturn(mockIdTokenCredential)
    whenever(mockIdTokenCredential.idToken).thenReturn("idtoken")
    whenever(mockHelper.toFirebaseCredential(any())).thenReturn(mockFirebaseCredential)
    whenever(mockAuth.signInWithCredential(mockFirebaseCredential))
        .thenReturn(completedTask(mockAuthResult))
    whenever(mockAuthResult.user).thenReturn(mockUser)
    whenever(mockUser.uid).thenReturn(uid)
    whenever(mockUser.email).thenReturn(email)
    whenever(mockUser.photoUrl).thenReturn(null)
    whenever(mockFirestore.collection("users")).thenReturn(mockCollection)
    whenever(mockCollection.document(uid)).thenReturn(mockDocument)
    whenever(mockDocument.get()).thenReturn(completedTask(mockDocSnap))
    whenever(mockDocSnap.exists()).thenReturn(true)

    val repo = AuthRepoFirebase(auth = mockAuth, helper = mockHelper, firestore = mockFirestore)
    val result = repo.loginWithGoogle(credential)
    assertTrue(result.isSuccess)
    // Optionally verify createUser is NOT called (if you spy AuthRepoFirebase)
  }

  @Test
  fun `loginWithGoogle returns failure if Firestore set throws when creating user`() = runBlocking {
    val mockAuth = mock<FirebaseAuth>()
    val mockHelper = mock<GoogleSignInHelper>()
    val mockFirestore = mock<FirebaseFirestore>()
    val mockCollection = mock<CollectionReference>()
    val mockDocument = mock<DocumentReference>()
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
    val mockDocSnap = mock<com.google.firebase.firestore.DocumentSnapshot>()

    whenever(mockHelper.extractIdTokenCredential(any())).thenReturn(mockIdTokenCredential)
    whenever(mockIdTokenCredential.idToken).thenReturn("idtoken")
    whenever(mockHelper.toFirebaseCredential(any())).thenReturn(mockFirebaseCredential)
    whenever(mockAuth.signInWithCredential(mockFirebaseCredential))
        .thenReturn(completedTask(mockAuthResult))
    whenever(mockAuthResult.user).thenReturn(mockUser)
    whenever(mockUser.uid).thenReturn(uid)
    whenever(mockUser.email).thenReturn(email)
    whenever(mockUser.photoUrl).thenReturn(null)
    whenever(mockFirestore.collection("users")).thenReturn(mockCollection)
    whenever(mockCollection.document(uid)).thenReturn(mockDocument)
    whenever(mockDocument.get()).thenReturn(completedTask(mockDocSnap))
    whenever(mockDocSnap.exists()).thenReturn(false)
    whenever(mockDocument.set(any())).thenThrow(RuntimeException("Firestore set failed"))

    val repo = AuthRepoFirebase(auth = mockAuth, helper = mockHelper, firestore = mockFirestore)
    val result = repo.loginWithGoogle(credential)
    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()?.message?.contains("Firestore set failed") == true)
  }
}

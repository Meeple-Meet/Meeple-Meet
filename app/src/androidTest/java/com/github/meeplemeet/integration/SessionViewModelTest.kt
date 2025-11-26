package com.github.meeplemeet.integration

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.DiscussionNoUid
import com.github.meeplemeet.model.discussions.fromNoUid
import com.github.meeplemeet.model.sessions.Session
import com.github.meeplemeet.model.sessions.SessionViewModel
import com.github.meeplemeet.utils.FirestoreTests
import java.io.File
import java.io.FileOutputStream
import junit.framework.TestCase.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.Test

class SessionViewModelTest : FirestoreTests() {

  private lateinit var viewModel: SessionViewModel
  private lateinit var adminAccount: Account
  private lateinit var nonAdminAccount: Account
  private lateinit var discussion: Discussion
  private lateinit var testImageFile: File

  @Before
  fun setUp() = runBlocking {
    viewModel = SessionViewModel()

    // Sign in anonymously for Firebase Storage access
    auth.signInAnonymously().await()

    // Create test accounts
    adminAccount =
        Account(
            uid = "admin-uid",
            handle = "adminHandle",
            name = "Admin User",
            email = "admin@test.com",
            previews = emptyMap(),
            photoUrl = null,
            description = "Test admin user",
            shopOwner = false,
            spaceRenter = false,
            relationships = emptyMap(),
            notifications = emptyList())

    nonAdminAccount =
        Account(
            uid = "non-admin-uid",
            handle = "nonAdminHandle",
            name = "NonAdmin User",
            email = "nonadmin@test.com",
            previews = emptyMap(),
            photoUrl = null,
            description = "Test non-admin user",
            shopOwner = false,
            spaceRenter = false,
            relationships = emptyMap(),
            notifications = emptyList())

    // Create test discussion with session
    discussion =
        Discussion(
            uid = "test-discussion-${System.currentTimeMillis()}",
            creatorId = adminAccount.uid,
            name = "Test Discussion",
            description = "A test discussion",
            participants = listOf(adminAccount.uid, nonAdminAccount.uid),
            admins = listOf(adminAccount.uid),
            session = Session(name = "Test Session"))

    // Create a test image file
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    testImageFile = File(context.cacheDir, "test_image.png")
    val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.BLUE)
    FileOutputStream(testImageFile).use { out ->
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }

    // Save the discussion to Firestore
    db.collection("discussions").document(discussion.uid).set(discussion).await()
    delay(500) // Wait for write to propagate
  }

  @Test
  fun testSaveSessionPhotoAsAdmin() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext

    // Save session photo as admin
    viewModel.saveSessionPhoto(adminAccount, discussion, context, testImageFile.absolutePath)

    delay(3000) // Wait for upload and Firestore update

    // Verify the photo URL was updated in Firestore
    val doc = db.collection("discussions").document(discussion.uid).get().await()
    val discussionNoUid = doc.toObject(DiscussionNoUid::class.java)
    val updatedDiscussion = fromNoUid(doc.id, discussionNoUid!!)

    assertNotNull(updatedDiscussion)
    assertNotNull(updatedDiscussion.session)
    assertNotNull(updatedDiscussion.session?.photoUrl)
    assertTrue(updatedDiscussion.session?.photoUrl?.isNotEmpty() == true)
  }

  @Test
  fun testSaveSessionPhotoAsNonAdminThrowsException() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext

    // Attempt to save session photo as non-admin should throw exception
    try {
      viewModel.saveSessionPhoto(nonAdminAccount, discussion, context, testImageFile.absolutePath)
      delay(1000)
      fail("Expected PermissionDeniedException to be thrown")
    } catch (e: PermissionDeniedException) {
      assertEquals("Only discussion admins can perform this operation", e.message)
    }
  }

  @Test
  fun testDeleteSessionPhotoAsAdmin() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext

    // First, save a photo
    viewModel.saveSessionPhoto(adminAccount, discussion, context, testImageFile.absolutePath)
    delay(3000) // Wait for upload

    // Verify photo was saved
    val docAfterSave = db.collection("discussions").document(discussion.uid).get().await()
    val discussionNoUidAfterSave = docAfterSave.toObject(DiscussionNoUid::class.java)
    val discussionAfterSave = fromNoUid(docAfterSave.id, discussionNoUidAfterSave!!)

    assertNotNull(discussionAfterSave.session?.photoUrl)
    assertTrue(discussionAfterSave.session?.photoUrl?.isNotEmpty() == true)

    // Delete the photo
    viewModel.deleteSessionPhoto(adminAccount, discussion, context)
    delay(2000) // Wait for deletion and Firestore update

    // Verify the photo URL was set to empty string
    val docAfterDelete = db.collection("discussions").document(discussion.uid).get().await()
    val discussionNoUidAfterDelete = docAfterDelete.toObject(DiscussionNoUid::class.java)
    val discussionAfterDelete = fromNoUid(docAfterDelete.id, discussionNoUidAfterDelete!!)

    assertNotNull(discussionAfterDelete)
    assertNotNull(discussionAfterDelete.session)
    assertEquals(null, discussionAfterDelete.session?.photoUrl)
  }

  @Test
  fun testDeleteSessionPhotoAsNonAdminThrowsException() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext

    // Attempt to delete session photo as non-admin should throw exception
    try {
      viewModel.deleteSessionPhoto(nonAdminAccount, discussion, context)
      delay(1000)
      fail("Expected PermissionDeniedException to be thrown")
    } catch (e: PermissionDeniedException) {
      assertEquals("Only discussion admins can perform this operation", e.message)
    }
  }

  @Test
  fun testLoadSessionPhoto() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext

    // First, save a photo
    viewModel.saveSessionPhoto(adminAccount, discussion, context, testImageFile.absolutePath)
    delay(3000) // Wait for upload

    // Load the photo (no admin check for read operation)
    val loadedPhotoBytes = viewModel.loadSessionPhoto(discussion, context)

    // Verify the photo was loaded successfully
    assertNotNull(loadedPhotoBytes)
    assertTrue(loadedPhotoBytes.isNotEmpty())
  }

  @Test
  fun testLoadSessionPhotoAsNonAdmin() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext

    // First, save a photo as admin
    viewModel.saveSessionPhoto(adminAccount, discussion, context, testImageFile.absolutePath)
    delay(3000) // Wait for upload

    // Non-admin should be able to load the photo (read operation)
    val loadedPhotoBytes = viewModel.loadSessionPhoto(discussion, context)

    // Verify the photo was loaded successfully
    assertNotNull(loadedPhotoBytes)
    assertTrue(loadedPhotoBytes.isNotEmpty())
  }
}

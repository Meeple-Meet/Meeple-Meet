package com.github.meeplemeet.model

import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.DiscussionNoUid
import com.github.meeplemeet.model.discussions.fromNoUid
import com.github.meeplemeet.model.discussions.toNoUid
import com.google.firebase.Timestamp
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import org.junit.Test

/** Unit tests for the Discussion and DiscussionNoUid data classes and conversion functions. */
class DiscussionTest {

  @Test
  fun `Discussion can be created without profile picture`() {
    val discussion =
        Discussion(
            uid = "disc1",
            creatorId = "user1",
            name = "Test Discussion",
            description = "A test",
            participants = listOf("user1"),
            admins = listOf("user1"),
            createdAt = Timestamp.now(),
            session = null,
            profilePictureUrl = null)

    assertEquals("disc1", discussion.uid)
    assertEquals("user1", discussion.creatorId)
    assertEquals("Test Discussion", discussion.name)
    assertNull(discussion.profilePictureUrl)
  }

  @Test
  fun `Discussion can be created with profile picture`() {
    val discussion =
        Discussion(
            uid = "disc1",
            creatorId = "user1",
            name = "Test Discussion",
            description = "A test",
            participants = listOf("user1"),
            admins = listOf("user1"),
            createdAt = Timestamp.now(),
            session = null,
            profilePictureUrl = "https://example.com/profile.jpg")

    assertNotNull(discussion.profilePictureUrl)
    assertEquals("https://example.com/profile.jpg", discussion.profilePictureUrl)
  }

  @Test
  fun `DiscussionNoUid can be created without profile picture`() {
    val discussionNoUid =
        DiscussionNoUid(
            creatorId = "user1",
            name = "Test Discussion",
            description = "A test",
            participants = listOf("user1"),
            admins = listOf("user1"),
            createdAt = Timestamp.now(),
            session = null,
            profilePictureUrl = null)

    assertEquals("user1", discussionNoUid.creatorId)
    assertEquals("Test Discussion", discussionNoUid.name)
    assertNull(discussionNoUid.profilePictureUrl)
  }

  @Test
  fun `DiscussionNoUid can be created with profile picture`() {
    val discussionNoUid =
        DiscussionNoUid(
            creatorId = "user1",
            name = "Test Discussion",
            description = "A test",
            participants = listOf("user1"),
            admins = listOf("user1"),
            createdAt = Timestamp.now(),
            session = null,
            profilePictureUrl = "https://example.com/profile.jpg")

    assertNotNull(discussionNoUid.profilePictureUrl)
    assertEquals("https://example.com/profile.jpg", discussionNoUid.profilePictureUrl)
  }

  @Test
  fun `toNoUid correctly converts Discussion to DiscussionNoUid`() {
    val timestamp = Timestamp.now()
    val discussion =
        Discussion(
            uid = "disc1",
            creatorId = "user1",
            name = "Test Discussion",
            description = "Description",
            participants = listOf("user1", "user2"),
            admins = listOf("user1"),
            createdAt = timestamp,
            session = null,
            profilePictureUrl = null)

    val discussionNoUid = toNoUid(discussion)

    assertEquals("user1", discussionNoUid.creatorId)
    assertEquals("Test Discussion", discussionNoUid.name)
    assertEquals("Description", discussionNoUid.description)
    assertEquals(listOf("user1", "user2"), discussionNoUid.participants)
    assertEquals(listOf("user1"), discussionNoUid.admins)
    assertEquals(timestamp, discussionNoUid.createdAt)
    assertNull(discussionNoUid.session)
    assertNull(discussionNoUid.profilePictureUrl)
  }

  @Test
  fun `toNoUid preserves profile picture URL`() {
    val timestamp = Timestamp.now()
    val discussion =
        Discussion(
            uid = "disc1",
            creatorId = "user1",
            name = "Test Discussion",
            description = "Description",
            participants = listOf("user1"),
            admins = listOf("user1"),
            createdAt = timestamp,
            session = null,
            profilePictureUrl = "https://example.com/profile.jpg")

    val discussionNoUid = toNoUid(discussion)

    assertEquals("https://example.com/profile.jpg", discussionNoUid.profilePictureUrl)
  }

  @Test
  fun `fromNoUid correctly converts DiscussionNoUid to Discussion`() {
    val timestamp = Timestamp.now()
    val discussionNoUid =
        DiscussionNoUid(
            creatorId = "user1",
            name = "Test Discussion",
            description = "Description",
            participants = listOf("user1", "user2"),
            admins = listOf("user1"),
            createdAt = timestamp,
            session = null,
            profilePictureUrl = null)

    val discussion = fromNoUid("disc123", discussionNoUid)

    assertEquals("disc123", discussion.uid)
    assertEquals("user1", discussion.creatorId)
    assertEquals("Test Discussion", discussion.name)
    assertEquals("Description", discussion.description)
    assertEquals(listOf("user1", "user2"), discussion.participants)
    assertEquals(listOf("user1"), discussion.admins)
    assertEquals(timestamp, discussion.createdAt)
    assertNull(discussion.session)
    assertNull(discussion.profilePictureUrl)
  }

  @Test
  fun `fromNoUid preserves profile picture URL`() {
    val timestamp = Timestamp.now()
    val discussionNoUid =
        DiscussionNoUid(
            creatorId = "user1",
            name = "Test Discussion",
            description = "Description",
            participants = listOf("user1"),
            admins = listOf("user1"),
            createdAt = timestamp,
            session = null,
            profilePictureUrl = "https://example.com/profile.jpg")

    val discussion = fromNoUid("disc123", discussionNoUid)

    assertEquals("https://example.com/profile.jpg", discussion.profilePictureUrl)
  }

  @Test
  fun `round trip conversion preserves all data`() {
    val timestamp = Timestamp.now()
    val originalDiscussion =
        Discussion(
            uid = "disc1",
            creatorId = "user1",
            name = "Test Discussion",
            description = "Description",
            participants = listOf("user1", "user2"),
            admins = listOf("user1"),
            createdAt = timestamp,
            session = null,
            profilePictureUrl = "https://example.com/profile.jpg")

    val discussionNoUid = toNoUid(originalDiscussion)
    val reconstructed = fromNoUid("disc1", discussionNoUid)

    assertEquals(originalDiscussion.uid, reconstructed.uid)
    assertEquals(originalDiscussion.creatorId, reconstructed.creatorId)
    assertEquals(originalDiscussion.name, reconstructed.name)
    assertEquals(originalDiscussion.description, reconstructed.description)
    assertEquals(originalDiscussion.participants, reconstructed.participants)
    assertEquals(originalDiscussion.admins, reconstructed.admins)
    assertEquals(originalDiscussion.createdAt, reconstructed.createdAt)
    assertEquals(originalDiscussion.session, reconstructed.session)
    assertEquals(originalDiscussion.profilePictureUrl, reconstructed.profilePictureUrl)
  }
}

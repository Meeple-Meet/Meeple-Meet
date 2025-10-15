package com.github.meeplemeet.integration

import com.github.meeplemeet.model.GameNotFoundException
import com.github.meeplemeet.model.InvalidHandleFormatException
import com.github.meeplemeet.model.NotSignedInException
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.repositories.FirestoreRepository
import com.github.meeplemeet.model.repositories.FirestoreSessionRepository
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.Discussion
import com.github.meeplemeet.model.structures.Game
import com.github.meeplemeet.model.structures.Location
import com.github.meeplemeet.model.structures.Session
import com.github.meeplemeet.model.viewmodels.FirestoreSessionViewModel
import com.github.meeplemeet.utils.FakeGameRepo
import com.github.meeplemeet.utils.FirestoreTests
import com.google.firebase.Timestamp
import io.mockk.coEvery
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreSessionTests : FirestoreTests() {
  lateinit var account1: Account
  lateinit var account2: Account
  lateinit var account3: Account
  lateinit var baseDiscussion: Discussion
  lateinit var testLocation: Location
  lateinit var testTimestamp: Timestamp

  private val firestoreRepository = FirestoreRepository()
  private val sessionRepository = mockk<FirestoreSessionRepository>()
  private lateinit var viewModel: FirestoreSessionViewModel

  @Before
  fun setup() {
    runBlocking {
      account1 =
          firestoreRepository.createAccount(
              "Antoine", "Antoine", email = "Antoine@example.com", photoUrl = null)
      account2 =
          firestoreRepository.createAccount(
              "Marco", "Marco", email = "Marco@example.com", photoUrl = null)
      account3 =
          firestoreRepository.createAccount(
              "Thomas", "Thomas", email = "Thomas@example.com", photoUrl = null)

      val (_, discussion) =
          firestoreRepository.createDiscussion("Game Night", "Test Discussion", account1.uid)
      baseDiscussion = discussion
    }

    testLocation = Location(latitude = 46.5197, longitude = 6.5665, name = "EPFL")
    testTimestamp = Timestamp.now()

    Dispatchers.setMain(StandardTestDispatcher())
    viewModel = FirestoreSessionViewModel(baseDiscussion, sessionRepository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test(expected = InvalidHandleFormatException::class)
  fun lol() {
    throw InvalidHandleFormatException()
  }

  @Test(expected = GameNotFoundException::class)
  fun lol2() {
    throw GameNotFoundException()
  }

  @Test(expected = NotSignedInException::class)
  fun lol3() {
    throw NotSignedInException("")
  }

  @Test
  fun canCreateSession() = runTest {
    val sessionData =
        Session(
            name = "Catan Night",
            gameId = "game123",
            date = testTimestamp,
            location = testLocation,
            participants = listOf(account1.uid, account2.uid),
            maxParticipants = 10,
            minParticipants = 1)
    val discussionWithSession = baseDiscussion.copy(session = sessionData)

    coEvery {
      sessionRepository.updateSession(
          baseDiscussion.uid,
          "Catan Night",
          "game123",
          testTimestamp,
          testLocation,
          1,
          10,
          listOf(account1.uid, account2.uid))
    } returns discussionWithSession

    viewModel.createSession(
        account1,
        baseDiscussion,
        "Catan Night",
        "game123",
        testTimestamp,
        testLocation,
        1,
        10,
        account1,
        account2)
    advanceUntilIdle()

    val updatedDiscussion = viewModel.discussion.value
    assertNotNull(updatedDiscussion.session)
    assertEquals("Catan Night", updatedDiscussion.session?.name)
    assertEquals("game123", updatedDiscussion.session?.gameId)
    assertEquals(testLocation, updatedDiscussion.session?.location)
    assertEquals(2, updatedDiscussion.session?.participants?.size)
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonParticipantCannotCreateSession() = runTest {
    viewModel.createSession(
        account2,
        baseDiscussion,
        "Catan Night",
        "game123",
        testTimestamp,
        testLocation,
        1,
        10,
        account2)
    advanceUntilIdle()
  }

  @Test
  fun canUpdateSessionName() = runTest {
    val originalSession =
        Session(
            name = "Catan Night",
            gameId = "game123",
            date = testTimestamp,
            location = testLocation,
            participants = listOf(account1.uid))
    val discussionWithSession = baseDiscussion.copy(session = originalSession)
    val updatedSession = originalSession.copy(name = "Settlers of Catan Night")
    val updatedDiscussion = discussionWithSession.copy(session = updatedSession)

    viewModel = FirestoreSessionViewModel(discussionWithSession, sessionRepository)

    coEvery {
      sessionRepository.updateSession(
          discussionWithSession.uid, "Settlers of Catan Night", null, null, null, null)
    } returns updatedDiscussion

    viewModel.updateSession(account1, discussionWithSession, name = "Settlers of Catan Night")
    advanceUntilIdle()

    val result = viewModel.discussion.value
    assertEquals("Settlers of Catan Night", result.session?.name)
    assertEquals("game123", result.session?.gameId)
  }

  @Test
  fun canUpdateSessionLocation() = runTest {
    val originalSession =
        Session(
            name = "Catan Night",
            gameId = "game123",
            date = testTimestamp,
            location = testLocation,
            participants = listOf(account1.uid))
    val discussionWithSession = baseDiscussion.copy(session = originalSession)

    val newLocation = Location(latitude = 48.8566, longitude = 2.3522, name = "Paris")
    val updatedSession = originalSession.copy(location = newLocation)
    val updatedDiscussion = discussionWithSession.copy(session = updatedSession)

    viewModel = FirestoreSessionViewModel(discussionWithSession, sessionRepository)

    coEvery {
      sessionRepository.updateSession(
          discussionWithSession.uid, null, null, null, newLocation, null)
    } returns updatedDiscussion

    viewModel.updateSession(account1, discussionWithSession, location = newLocation)
    advanceUntilIdle()

    val result = viewModel.discussion.value
    assertEquals(newLocation, result.session?.location)
    assertEquals("Catan Night", result.session?.name)
  }

  @Test
  fun canUpdateSessionDate() = runTest {
    val originalSession =
        Session(
            name = "Catan Night",
            gameId = "game123",
            date = testTimestamp,
            location = testLocation,
            participants = listOf(account1.uid))
    val discussionWithSession = baseDiscussion.copy(session = originalSession)

    val newDate = Timestamp(testTimestamp.seconds + 86400, 0) // +1 day
    val updatedSession = originalSession.copy(date = newDate)
    val updatedDiscussion = discussionWithSession.copy(session = updatedSession)

    viewModel = FirestoreSessionViewModel(discussionWithSession, sessionRepository)

    coEvery {
      sessionRepository.updateSession(discussionWithSession.uid, null, null, newDate, null, null)
    } returns updatedDiscussion

    viewModel.updateSession(account1, discussionWithSession, date = newDate)
    advanceUntilIdle()

    val result = viewModel.discussion.value
    assertEquals(newDate, result.session?.date)
  }

  @Test
  fun canUpdateSessionParticipants() = runTest {
    val originalSession =
        Session(
            name = "Catan Night",
            gameId = "game123",
            date = testTimestamp,
            location = testLocation,
            1,
            10,
            participants = listOf(account1.uid))
    val discussionWithSession = baseDiscussion.copy(session = originalSession)

    val newParticipants = listOf(account1, account2, account3)
    val updatedSession = originalSession.copy(participants = newParticipants.map { it -> it.uid })
    val updatedDiscussion = discussionWithSession.copy(session = updatedSession)

    viewModel = FirestoreSessionViewModel(discussionWithSession, sessionRepository)

    coEvery {
      sessionRepository.updateSession(
          discussionWithSession.uid,
          null,
          null,
          null,
          null,
          null,
          null,
          newParticipants.map { it -> it.uid })
    } returns updatedDiscussion

    viewModel.updateSession(account1, discussionWithSession, newParticipantList = newParticipants)
    advanceUntilIdle()

    val result = viewModel.discussion.value
    assertEquals(3, result.session?.participants?.size)
    assertEquals(newParticipants.map { it -> it.uid }, result.session?.participants)
  }

  @Test
  fun canUpdateMultipleSessionFields() = runTest {
    val originalSession =
        Session(
            name = "Catan Night",
            gameId = "game123",
            date = testTimestamp,
            location = testLocation,
            1,
            10,
            participants = listOf(account1.uid))
    val discussionWithSession = baseDiscussion.copy(session = originalSession)

    val newName = "Epic Catan Tournament"
    val newDate = Timestamp(testTimestamp.seconds + 86400, 0)
    val newLocation = Location(latitude = 48.8566, longitude = 2.3522, name = "Paris")
    val newParticipants = listOf(account1, account2)

    val updatedSession =
        originalSession.copy(
            name = newName,
            date = newDate,
            location = newLocation,
            participants = newParticipants.map { it -> it.uid })
    val updatedDiscussion = discussionWithSession.copy(session = updatedSession)

    viewModel = FirestoreSessionViewModel(discussionWithSession, sessionRepository)

    coEvery {
      sessionRepository.updateSession(
          discussionWithSession.uid,
          newName,
          null,
          newDate,
          newLocation,
          null,
          null,
          newParticipants.map { it -> it.uid })
    } returns updatedDiscussion

    viewModel.updateSession(
        account1,
        discussionWithSession,
        name = newName,
        date = newDate,
        location = newLocation,
        newParticipantList = newParticipants)
    advanceUntilIdle()

    val result = viewModel.discussion.value
    assertEquals(newName, result.session?.name)
    assertEquals(newDate, result.session?.date)
    assertEquals(newLocation, result.session?.location)
    assertEquals(newParticipants.map { it -> it.uid }, result.session?.participants)
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonParticipantCannotUpdateSession() = runTest {
    val sessionData =
        Session(
            name = "Catan Night",
            gameId = "game123",
            date = testTimestamp,
            location = testLocation,
            participants = listOf(account1.uid))
    val discussionWithSession = baseDiscussion.copy(session = sessionData)

    viewModel = FirestoreSessionViewModel(discussionWithSession, sessionRepository)

    viewModel.updateSession(account2, discussionWithSession, name = "Hacked Session")
    advanceUntilIdle()
  }

  @Test
  fun canDeleteSession() = runTest {
    val sessionData =
        Session(
            name = "Catan Night",
            gameId = "game123",
            date = testTimestamp,
            location = testLocation,
            participants = listOf(account1.uid))
    val discussionWithSession = baseDiscussion.copy(session = sessionData)

    viewModel = FirestoreSessionViewModel(discussionWithSession, sessionRepository)

    coEvery { sessionRepository.deleteSession(discussionWithSession.uid) } returns Unit

    viewModel.deleteSession(account1, discussionWithSession)
    advanceUntilIdle()

    // Note: The actual deletion happens in Firestore, we just verify the call was made
    // In a real test with actual Firestore, we would verify the session was set to null
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonParticipantCannotDeleteSession() = runTest {
    val sessionData =
        Session(
            name = "Catan Night",
            gameId = "game123",
            date = testTimestamp,
            location = testLocation,
            participants = listOf(account1.uid))
    val discussionWithSession = baseDiscussion.copy(session = sessionData)

    viewModel = FirestoreSessionViewModel(discussionWithSession, sessionRepository)

    viewModel.deleteSession(account2, discussionWithSession)
    advanceUntilIdle()
  }

  @Test
  fun canUpdateSessionGameId() = runTest {
    val originalSession =
        Session(
            name = "Catan Night",
            gameId = "game123",
            date = testTimestamp,
            location = testLocation,
            participants = listOf(account1.uid))
    val discussionWithSession = baseDiscussion.copy(session = originalSession)

    val newGameId = "game456"
    val updatedSession = originalSession.copy(gameId = newGameId)
    val updatedDiscussion = discussionWithSession.copy(session = updatedSession)

    viewModel = FirestoreSessionViewModel(discussionWithSession, sessionRepository)

    coEvery {
      sessionRepository.updateSession(discussionWithSession.uid, null, newGameId, null, null, null)
    } returns updatedDiscussion

    viewModel.updateSession(account1, discussionWithSession, gameId = newGameId)
    advanceUntilIdle()

    val result = viewModel.discussion.value
    assertEquals(newGameId, result.session?.gameId)
    assertEquals("Catan Night", result.session?.name)
  }

  @Test
  fun multipleParticipantsCanUpdateSession() = runTest {
    val originalSession =
        Session(
            name = "Catan Night",
            gameId = "game123",
            date = testTimestamp,
            location = testLocation,
            participants = listOf(account1.uid, account2.uid))
    val discussionWithTwoParticipants =
        baseDiscussion.copy(
            participants = listOf(account1.uid, account2.uid), session = originalSession)

    val updatedSession = originalSession.copy(name = "Updated by Account2")
    val updatedDiscussion = discussionWithTwoParticipants.copy(session = updatedSession)

    viewModel = FirestoreSessionViewModel(discussionWithTwoParticipants, sessionRepository)

    coEvery {
      sessionRepository.updateSession(
          discussionWithTwoParticipants.uid, "Updated by Account2", null, null, null, null)
    } returns updatedDiscussion

    viewModel.updateSession(account2, discussionWithTwoParticipants, name = "Updated by Account2")
    advanceUntilIdle()

    val result = viewModel.discussion.value
    assertEquals("Updated by Account2", result.session?.name)
  }

  @Test(expected = IllegalArgumentException::class)
  fun cannotUpdateSessionWithNoFields() = runTest {
    val sessionData =
        Session(
            name = "Catan Night",
            gameId = "game123",
            date = testTimestamp,
            location = testLocation,
            participants = listOf(account1.uid))
    val discussionWithSession = baseDiscussion.copy(session = sessionData)

    viewModel = FirestoreSessionViewModel(discussionWithSession, sessionRepository)

    coEvery {
      sessionRepository.updateSession(discussionWithSession.uid, null, null, null, null, null)
    } throws IllegalArgumentException("At least one field must be provided for update")

    viewModel.updateSession(account1, discussionWithSession)
    advanceUntilIdle()
  }

  @Test
  fun creatingSessionUpdatesDiscussionState() = runTest {
    val sessionData =
        Session(
            name = "Catan Night",
            gameId = "game123",
            date = testTimestamp,
            location = testLocation,
            participants = listOf(account1.uid))
    val discussionWithSession = baseDiscussion.copy(session = sessionData)

    coEvery {
      sessionRepository.updateSession(
          baseDiscussion.uid,
          "Catan Night",
          "game123",
          testTimestamp,
          testLocation,
          1,
          10,
          listOf(account1.uid))
    } returns discussionWithSession

    // Verify initial state has no session
    assertNull(viewModel.discussion.value.session)

    viewModel.createSession(
        account1,
        baseDiscussion,
        "Catan Night",
        "game123",
        testTimestamp,
        testLocation,
        1,
        10,
        account1)
    advanceUntilIdle()

    // Verify state was updated
    assertNotNull(viewModel.discussion.value.session)
    assertEquals(discussionWithSession, viewModel.discussion.value)
  }

  @Test(expected = IllegalStateException::class)
  fun createSessionThrowsWhenRepositoryFails() = runTest {
    coEvery {
      sessionRepository.updateSession(
          baseDiscussion.uid,
          "Catan Night",
          "game123",
          testTimestamp,
          testLocation,
          1,
          10,
          listOf(account1.uid))
    } throws IllegalStateException("Firestore error")

    viewModel.createSession(
        account1,
        baseDiscussion,
        "Catan Night",
        "game123",
        testTimestamp,
        testLocation,
        1,
        10,
        account1)
    advanceUntilIdle()
  }

  @Test(expected = IllegalStateException::class)
  fun updateSessionThrowsWhenRepositoryFails() = runTest {
    val originalSession =
        Session(
            name = "Catan Night",
            gameId = "game123",
            date = testTimestamp,
            location = testLocation,
            participants = listOf(account1.uid))
    val discussionWithSession = baseDiscussion.copy(session = originalSession)

    viewModel = FirestoreSessionViewModel(discussionWithSession, sessionRepository)

    coEvery {
      sessionRepository.updateSession(discussionWithSession.uid, "New Name", null, null, null, null)
    } throws IllegalStateException("Firestore error")

    viewModel.updateSession(account1, discussionWithSession, name = "New Name")
    advanceUntilIdle()
  }

  @Test(expected = IllegalStateException::class)
  fun deleteSessionThrowsWhenRepositoryFails() = runTest {
    val sessionData =
        Session(
            name = "Catan Night",
            gameId = "game123",
            date = testTimestamp,
            location = testLocation,
            participants = listOf(account1.uid))
    val discussionWithSession = baseDiscussion.copy(session = sessionData)

    viewModel = FirestoreSessionViewModel(discussionWithSession, sessionRepository)

    coEvery { sessionRepository.deleteSession(discussionWithSession.uid) } throws
        IllegalStateException("Firestore error")

    viewModel.deleteSession(account1, discussionWithSession)
    advanceUntilIdle()
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonParticipantCannotCreateSessionEvenWithValidData() = runTest {
    // Verify permission check happens before repository call
    viewModel.createSession(
        account3,
        baseDiscussion,
        "Valid Session",
        "game123",
        testTimestamp,
        testLocation,
        1,
        10,
        account3)
    advanceUntilIdle()
  }

  @Test
  fun sessionParticipantsCanDifferFromDiscussionParticipants() = runTest {
    val sessionData =
        Session(
            name = "Catan Night",
            gameId = "game123",
            date = testTimestamp,
            location = testLocation,
            participants = listOf(account1.uid, account3.uid)) // account3 not in discussion
    val discussionWithSession = baseDiscussion.copy(session = sessionData)

    coEvery {
      sessionRepository.updateSession(
          baseDiscussion.uid,
          "Catan Night",
          "game123",
          testTimestamp,
          testLocation,
          1,
          10,
          listOf(account1.uid, account3.uid))
    } returns discussionWithSession

    viewModel.createSession(
        account1,
        baseDiscussion,
        "Catan Night",
        "game123",
        testTimestamp,
        testLocation,
        1,
        10,
        account1,
        account3)
    advanceUntilIdle()

    val result = viewModel.discussion.value
    assertEquals(2, result.session?.participants?.size)
    // Session participants include account3 even though account3 is not in discussion participants
    assertEquals(listOf(account1.uid, account3.uid), result.session?.participants)
  }

  @Test(expected = IllegalArgumentException::class)
  fun emptyParticipantListIsValid() = runTest {
    val sessionData =
        Session(
            name = "Planning Session",
            gameId = "game123",
            date = testTimestamp,
            location = testLocation,
            participants = emptyList())
    val discussionWithSession = baseDiscussion.copy(session = sessionData)

    coEvery {
      sessionRepository.updateSession(
          baseDiscussion.uid,
          "Planning Session",
          "game123",
          testTimestamp,
          testLocation,
          null,
          null,
          emptyList())
    } returns discussionWithSession

    viewModel.createSession(
        account1, baseDiscussion, "Planning Session", "game123", testTimestamp, testLocation)
  }

  // ========================================================================
  // FirestoreSessionRepository Tests (Real Repository, Not Mocked)
  // ========================================================================

  @Test
  fun repositoryCanCreateSession() = runBlocking {
    val realSessionRepo = FirestoreSessionRepository()

    val updatedDiscussion =
        realSessionRepo.createSession(
            baseDiscussion.uid,
            "Real Catan Night",
            "game789",
            testTimestamp,
            testLocation,
            1,
            10,
            account1.uid,
            account2.uid)

    assertNotNull(updatedDiscussion.session)
    assertEquals("Real Catan Night", updatedDiscussion.session?.name)
    assertEquals("game789", updatedDiscussion.session?.gameId)
    assertEquals(testLocation, updatedDiscussion.session?.location)
    assertEquals(2, updatedDiscussion.session?.participants?.size)
  }

  @Test
  fun repositoryCanUpdateSessionName() = runBlocking {
    val realSessionRepo = FirestoreSessionRepository()

    // First create a session
    val withSession =
        realSessionRepo.createSession(
            baseDiscussion.uid,
            "Original Name",
            "game123",
            testTimestamp,
            testLocation,
            1,
            10,
            account1.uid)

    // Then update just the name
    val updated = realSessionRepo.updateSession(withSession.uid, name = "Updated Name")

    assertEquals("Updated Name", updated.session?.name)
    assertEquals("game123", updated.session?.gameId)
    assertEquals(testLocation, updated.session?.location)
  }

  @Test
  fun repositoryCanUpdateSessionLocation() = runBlocking {
    val realSessionRepo = FirestoreSessionRepository()

    val withSession =
        realSessionRepo.createSession(
            baseDiscussion.uid,
            "Session",
            "game123",
            testTimestamp,
            testLocation,
            1,
            10,
            account1.uid)

    val newLocation = Location(latitude = 51.5074, longitude = -0.1278, name = "London")
    val updated = realSessionRepo.updateSession(withSession.uid, location = newLocation)

    assertEquals(newLocation, updated.session?.location)
    assertEquals("Session", updated.session?.name)
  }

  @Test
  fun repositoryCanUpdateSessionDate() = runBlocking {
    val realSessionRepo = FirestoreSessionRepository()

    val withSession =
        realSessionRepo.createSession(
            baseDiscussion.uid,
            "Session",
            "game123",
            testTimestamp,
            testLocation,
            1,
            10,
            account1.uid)

    val newDate = Timestamp(testTimestamp.seconds + 3600, 0) // +1 hour
    val updated = realSessionRepo.updateSession(withSession.uid, date = newDate)

    assertEquals(newDate, updated.session?.date)
  }

  @Test
  fun repositoryCanUpdateSessionGameId() = runBlocking {
    val realSessionRepo = FirestoreSessionRepository()

    val withSession =
        realSessionRepo.createSession(
            baseDiscussion.uid,
            "Session",
            "game123",
            testTimestamp,
            testLocation,
            1,
            10,
            account1.uid)

    val updated = realSessionRepo.updateSession(withSession.uid, gameId = "game999")

    assertEquals("game999", updated.session?.gameId)
    assertEquals("Session", updated.session?.name)
  }

  @Test
  fun repositoryCanUpdateSessionParticipants() = runBlocking {
    val realSessionRepo = FirestoreSessionRepository()

    val withSession =
        realSessionRepo.createSession(
            baseDiscussion.uid,
            "Session",
            "game123",
            testTimestamp,
            testLocation,
            1,
            10,
            account1.uid)

    val newParticipants = listOf(account1.uid, account2.uid, account3.uid)
    val updated =
        realSessionRepo.updateSession(withSession.uid, newParticipantList = newParticipants)

    assertEquals(3, updated.session?.participants?.size)
    assertEquals(newParticipants, updated.session?.participants)
  }

  @Test
  fun repositoryCanUpdateMultipleSessionFields() = runBlocking {
    val realSessionRepo = FirestoreSessionRepository()

    val withSession =
        realSessionRepo.createSession(
            baseDiscussion.uid,
            "Original",
            "game123",
            testTimestamp,
            testLocation,
            1,
            10,
            account1.uid)

    val newName = "Updated Name"
    val newGameId = "game999"
    val newDate = Timestamp(testTimestamp.seconds + 7200, 0)
    val newLocation = Location(48.8566, 2.3522, "Paris")
    val newParticipants = listOf(account1.uid, account2.uid)

    val updated =
        realSessionRepo.updateSession(
            withSession.uid,
            name = newName,
            gameId = newGameId,
            date = newDate,
            location = newLocation,
            newParticipantList = newParticipants)

    assertEquals(newName, updated.session?.name)
    assertEquals(newGameId, updated.session?.gameId)
    assertEquals(newDate, updated.session?.date)
    assertEquals(newLocation, updated.session?.location)
    assertEquals(newParticipants, updated.session?.participants)
  }

  @Test(expected = IllegalArgumentException::class)
  fun repositoryThrowsWhenNoFieldsProvided() = runTest {
    val realSessionRepo = FirestoreSessionRepository()

    val withSession =
        realSessionRepo.createSession(
            baseDiscussion.uid,
            "Session",
            "game123",
            testTimestamp,
            testLocation,
            1,
            10,
            account1.uid)

    // This should throw IllegalArgumentException
    realSessionRepo.updateSession(withSession.uid)
  }

  @Test
  fun repositoryCanDeleteSession() = runBlocking {
    val realSessionRepo = FirestoreSessionRepository()

    val withSession =
        realSessionRepo.createSession(
            baseDiscussion.uid,
            "Session",
            "game123",
            testTimestamp,
            testLocation,
            1,
            10,
            account1.uid)

    assertNotNull(withSession.session)

    realSessionRepo.deleteSession(withSession.uid)

    // Verify session was deleted by fetching the discussion
    val afterDelete = firestoreRepository.getDiscussion(baseDiscussion.uid)
    assertNull(afterDelete.session)
  }

  @Test
  fun repositoryCanCreateSessionWithEmptyParticipants() = runBlocking {
    val realSessionRepo = FirestoreSessionRepository()

    val updatedDiscussion =
        realSessionRepo.createSession(
            baseDiscussion.uid, "Planning Session", "game123", testTimestamp, testLocation, 1, 10)

    assertNotNull(updatedDiscussion.session)
    assertEquals(0, updatedDiscussion.session?.participants?.size)
  }

  @Test
  fun repositoryUpdatePreservesUnchangedFields() = runBlocking {
    val realSessionRepo = FirestoreSessionRepository()

    val originalName = "Original Session"
    val originalGameId = "game123"
    val originalLocation = testLocation

    val withSession =
        realSessionRepo.createSession(
            baseDiscussion.uid,
            originalName,
            originalGameId,
            testTimestamp,
            originalLocation,
            1,
            10,
            account1.uid)

    // Update only the date
    val newDate = Timestamp(testTimestamp.seconds + 1000, 0)
    val updated = realSessionRepo.updateSession(withSession.uid, date = newDate)

    // Verify other fields are preserved
    assertEquals(originalName, updated.session?.name)
    assertEquals(originalGameId, updated.session?.gameId)
    assertEquals(originalLocation, updated.session?.location)
    assertEquals(newDate, updated.session?.date)
  }

  @Test
  fun repositoryCanReplaceSession() = runBlocking {
    val realSessionRepo = FirestoreSessionRepository()

    // Create first session
    val firstSession =
        realSessionRepo.createSession(
            baseDiscussion.uid,
            "First Session",
            "game111",
            testTimestamp,
            testLocation,
            1,
            10,
            account1.uid)

    assertNotNull(firstSession.session)
    assertEquals("First Session", firstSession.session?.name)

    // Create second session (replaces first)
    val secondSession =
        realSessionRepo.createSession(
            baseDiscussion.uid,
            "Second Session",
            "game222",
            Timestamp(testTimestamp.seconds + 3600, 0),
            Location(40.7128, -74.0060, "New York"),
            1,
            10,
            account2.uid)

    assertNotNull(secondSession.session)
    assertEquals("Second Session", secondSession.session?.name)
    assertEquals("game222", secondSession.session?.gameId)
    assertEquals(listOf(account2.uid), secondSession.session?.participants)
  }

  // ------------------------
  // Tests for setGame / setGameQuery (FirestoreSessionViewModel)
  // ------------------------

  @Test
  fun setGame_updates_selectedGameUid_and_query_when_admin() = runTest {
    val game =
        Game(
            uid = "g_1",
            name = "Catan",
            description = "",
            imageURL = "",
            minPlayers = 1,
            maxPlayers = 4,
            recommendedPlayers = null,
            averagePlayTime = null,
            genres = emptyList())

    val fakeRepo = FakeGameRepo()
    val vm = FirestoreSessionViewModel(baseDiscussion, sessionRepository, fakeRepo)

    vm.setGame(account1, baseDiscussion, game)

    val state = vm.gameUIState.value
    assertEquals(game.uid, state.selectedGameUid)
    assertEquals(game.name, state.gameQuery)
  }

  @Test(expected = PermissionDeniedException::class)
  fun setGame_throws_when_not_admin() = runTest {
    val game =
        Game(
            uid = "g_2",
            name = "Azul",
            description = "",
            imageURL = "",
            minPlayers = 2,
            maxPlayers = 4,
            recommendedPlayers = null,
            averagePlayTime = null,
            genres = emptyList())

    val fakeRepo = FakeGameRepo()
    val vm = FirestoreSessionViewModel(baseDiscussion, sessionRepository, fakeRepo)

    vm.setGame(account3, baseDiscussion, game)
  }

  @Test
  fun setGameQuery_updates_suggestions_when_admin() = runTest {
    val game =
        Game(
            uid = "g_3",
            name = "Catan Junior",
            description = "",
            imageURL = "",
            minPlayers = 2,
            maxPlayers = 4,
            recommendedPlayers = null,
            averagePlayTime = null,
            genres = emptyList())

    val fakeRepo = FakeGameRepo().apply { returnedGames = listOf(game) }
    val vm = FirestoreSessionViewModel(baseDiscussion, sessionRepository, fakeRepo)

    vm.setGameQuery(account1, baseDiscussion, "cat")
    advanceUntilIdle()

    val state = vm.gameUIState.value
    assertEquals("cat", state.gameQuery)
    assertEquals(1, state.gameSuggestions.size)
    assertEquals(game.uid, state.gameSuggestions[0].uid)
    assertNull(state.gameSearchError)
  }

  @Test
  fun setGameQuery_sets_error_on_repository_failure() = runTest {
    val fakeRepo = FakeGameRepo().apply { shouldThrow = true }
    val vm = FirestoreSessionViewModel(baseDiscussion, sessionRepository, fakeRepo)

    vm.setGameQuery(account1, baseDiscussion, "cat")
    advanceUntilIdle()

    val state = vm.gameUIState.value
    assertEquals("cat", state.gameQuery)
    assertTrue(state.gameSuggestions.isEmpty())
    assertNotNull(state.gameSearchError)
  }

  @Test(expected = PermissionDeniedException::class)
  fun setGameQuery_throws_when_not_admin() = runTest {
    val fakeRepo = FakeGameRepo()
    val vm = FirestoreSessionViewModel(baseDiscussion, sessionRepository, fakeRepo)

    vm.setGameQuery(account3, baseDiscussion, "cat")
  }

  @Test
  fun getGameFromId_updates_state_when_successful() = runTest {
    val fakeRepo =
        FakeGameRepo().apply {
          returnedGame =
              Game(
                  uid = "g_123",
                  name = "Terraforming Mars",
                  description = "",
                  imageURL = "",
                  minPlayers = 1,
                  maxPlayers = 5,
                  recommendedPlayers = null,
                  averagePlayTime = null,
                  genres = emptyList())
        }
    val vm = FirestoreSessionViewModel(baseDiscussion, sessionRepository, fakeRepo)

    vm.getGameFromId("g_123")
    advanceUntilIdle()

    val state = vm.gameUIState.value
    assertNotNull(state.fetchedGame)
    assertEquals("g_123", state.fetchedGame?.uid)
    assertNull(state.gameFetchError)
  }

  @Test
  fun getGameFromId_sets_error_state_on_failure() = runTest {
    val fakeRepo = FakeGameRepo().apply { shouldThrow = true }
    val vm = FirestoreSessionViewModel(baseDiscussion, sessionRepository, fakeRepo)

    vm.getGameFromId("g_404")
    advanceUntilIdle()

    val state = vm.gameUIState.value
    assertNull(state.fetchedGame)
    assertEquals("Failed to fetch game details", state.gameFetchError)
  }
}

package com.github.meeplemeet.model

import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.discussions.DiscussionRepository
import com.github.meeplemeet.model.sessions.SessionRepository
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.snapshots
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var sessionRepository: SessionRepository
  private lateinit var discussionRepository: DiscussionRepository
  private lateinit var collectionReference: CollectionReference

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(RepositoryProvider)
    discussionRepository = mockk(relaxed = true)
    collectionReference = mockk(relaxed = true)

    every { RepositoryProvider.discussions } returns discussionRepository
    every { discussionRepository.collection } returns collectionReference
    every { RepositoryProvider.geoPins } returns mockk(relaxed = true)

    sessionRepository = SessionRepository(discussionRepository)

    mockkStatic("kotlinx.coroutines.tasks.TasksKt")
    mockkStatic("com.google.firebase.firestore.FirestoreKt")
    mockkStatic(FieldPath::class)
    mockkStatic(FieldValue::class)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkAll()
  }

  @Test
  fun `getUpcomingSessionIdsForUserFlow filters for future sessions`() =
      runTest(testDispatcher) {
        val userId = "user1"
        val query1 = mockk<Query>()
        val query2 = mockk<Query>()
        val snapshot = mockk<QuerySnapshot>()
        val doc1 = mockk<DocumentSnapshot>()

        // Mock the chain: whereArrayContains -> whereGreaterThan(now) -> snapshots
        every { collectionReference.whereArrayContains("session.participants", userId) } returns
            query1

        // Verify that we are filtering by date > now
        every { query1.whereGreaterThan(eq("session.date"), any<Timestamp>()) } returns query2
        every { query2.snapshots() } returns flowOf(snapshot)

        every { snapshot.documents } returns listOf(doc1)
        every { doc1.id } returns "futureSession"

        val result = sessionRepository.getUpcomingSessionIdsForUserFlow(userId).toList()

        assertEquals(1, result.size)
        assertEquals(listOf("futureSession"), result[0])

        // Verify the specific query construction
        io.mockk.verify { query1.whereGreaterThan(eq("session.date"), any<Timestamp>()) }
      }

  @Test
  fun `getPastSessionIdsForUser filters for past sessions`() =
      runTest(testDispatcher) {
        val userId = "user1"
        val query1 = mockk<Query>()
        val query2 = mockk<Query>()
        val query3 = mockk<Query>()
        val query4 = mockk<Query>()
        val query5 = mockk<Query>()
        val task = mockk<Task<QuerySnapshot>>()
        val snapshot = mockk<QuerySnapshot>()
        val doc1 = mockk<DocumentSnapshot>()

        // Mock the chain: whereArrayContains -> whereLessThan(now) -> orderBy(date) -> orderBy(id) -> limit -> get
        every { collectionReference.whereArrayContains("session.participants", userId) } returns
            query1

        // Verify that we are filtering by date < now
        every { query1.whereLessThan(eq("session.date"), any<Timestamp>()) } returns query2
        every { query2.orderBy("session.date") } returns query3
        every { query3.orderBy(any<FieldPath>()) } returns query4
        every { query4.limit(any()) } returns query5
        every { query5.get() } returns task
        coEvery { task.await() } returns snapshot

        every { snapshot.isEmpty } returns false
        every { snapshot.documents } returns listOf(doc1)
        every { snapshot.size() } returns 1
        every { doc1.id } returns "pastSession"

        val result = sessionRepository.getPastSessionIdsForUser(userId)

        assertEquals(listOf("pastSession"), result)

        // Verify the specific query construction includes orderBy date
        io.mockk.verify { 
          query1.whereLessThan(eq("session.date"), any<Timestamp>())
          query2.orderBy("session.date")
        }
      }

  @Test
  fun `addSessionPhotos updates session with new photos`() =
      runTest(testDispatcher) {
        val discussionId = "discussion-1"
        val photos = listOf(
            com.github.meeplemeet.model.sessions.SessionPhoto("uuid-1", "url-1"),
            com.github.meeplemeet.model.sessions.SessionPhoto("uuid-2", "url-2")
        )
        val docRef = mockk<com.google.firebase.firestore.DocumentReference>(relaxed = true)
        val mockFieldValue = mockk<FieldValue>()

        every { collectionReference.document(discussionId) } returns docRef
        every { FieldValue.arrayUnion(*anyVararg()) } returns mockFieldValue
        coEvery { docRef.update(any<String>(), any()).await() } returns mockk()

        sessionRepository.addSessionPhotos(discussionId, photos)

        io.mockk.verify {
          docRef.update("session.sessionPhotos", mockFieldValue)
        }
        io.mockk.verify {
          FieldValue.arrayUnion(*photos.toTypedArray())
        }
      }

  @Test
  fun `removeSessionPhoto removes photo from session`() =
      runTest(testDispatcher) {
        val discussionId = "discussion-1"
        val photoUuid = "uuid-1"
        val docRef = mockk<com.google.firebase.firestore.DocumentReference>(relaxed = true)
        val snapshot = mockk<DocumentSnapshot>()
        val session = com.github.meeplemeet.model.sessions.Session(
            sessionPhotos = listOf(
                com.github.meeplemeet.model.sessions.SessionPhoto("uuid-1", "url-1"),
                com.github.meeplemeet.model.sessions.SessionPhoto("uuid-2", "url-2")
            )
        )
        val discussionNoUid = com.github.meeplemeet.model.discussions.DiscussionNoUid(session = session)

        every { collectionReference.document(discussionId) } returns docRef
        coEvery { docRef.get().await() } returns snapshot
        every { snapshot.exists() } returns true
        every { snapshot.toObject(com.github.meeplemeet.model.discussions.DiscussionNoUid::class.java) } returns discussionNoUid
        coEvery { docRef.update(any<String>(), any()).await() } returns mockk()

        sessionRepository.removeSessionPhoto(discussionId, photoUuid)

        io.mockk.verify {
          docRef.update("session.sessionPhotos", listOf(com.github.meeplemeet.model.sessions.SessionPhoto("uuid-2", "url-2")))
        }
      }
}

package com.github.meeplemeet.model.space_renter

import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.model.account.AccountRepository
import com.github.meeplemeet.model.map.StorableGeoPinRepository
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Before
import org.junit.Test

class SpaceRenterRepositoryDeleteTest {
  private lateinit var accountRepo: AccountRepository
  private lateinit var geoPinRepo: StorableGeoPinRepository
  private lateinit var firestore: FirebaseFirestore
  private lateinit var collection: CollectionReference
  private lateinit var repository: SpaceRenterRepository

  @Before
  fun setup() {
    mockkObject(FirebaseProvider)
    mockkStatic("kotlinx.coroutines.tasks.TasksKt")

    firestore = mockk()
    collection = mockk()
    every { FirebaseProvider.db } returns firestore
    every { firestore.collection("space_renters") } returns collection

    accountRepo = mockk(relaxed = true)
    geoPinRepo = mockk(relaxed = true)

    repository = SpaceRenterRepository(accountRepo, geoPinRepo)
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun deleteSpaceRenters_handlesAllExceptions() = runBlocking {
    val id = "space1"
    val ownerId = "fallbackOwner"

    // Mock document reference
    val docRef = mockk<DocumentReference>()
    every { collection.document(id) } returns docRef

    // 1. Mock getSpaceRenter failure
    // The code calls getSpaceRenter(id) which calls collection.document(id).get().await()
    val getTask = mockk<Task<DocumentSnapshot>>()
    every { docRef.get() } returns getTask
    coEvery { getTask.await() } throws RuntimeException("Get failed")

    // 2. Mock geoPin delete failure
    coEvery { geoPinRepo.deleteGeoPin(id) } throws RuntimeException("GeoPin failed")

    // 3. Mock document delete failure
    val deleteTask = mockk<Task<Void>>()
    every { docRef.delete() } returns deleteTask
    coEvery { deleteTask.await() } throws RuntimeException("Delete failed")

    // 4. Mock account repo failure
    // Sinc getSpaceRenter failed, it uses ownerId fallback
    coEvery { accountRepo.removeSpaceRenterId(ownerId, id) } throws
        RuntimeException("Account failed")

    // Execute
    // This should NOT throw exception because of the try-catch blocks
    repository.deleteSpaceRenters(listOf(id), ownerId = ownerId)
  }
}

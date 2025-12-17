package com.github.meeplemeet.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.offline.OfflineModeManager
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.space_renter.EditSpaceRenterViewModel
import com.github.meeplemeet.model.space_renter.Space
import com.github.meeplemeet.model.space_renter.SpaceRenter
import com.github.meeplemeet.model.space_renter.SpaceRenterRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class EditSpaceRenterViewModelTest {

  private lateinit var viewModel: EditSpaceRenterViewModel
  private lateinit var fakeSpaceRenterRepository: FakeSpaceRenterRepository
  private lateinit var fakeImageRepository: FakeImageRepository

  private val testOwner = Account(uid = "owner123", name = "Test Owner", email = "owner@test.com", handle = "test_owner")
  private val otherUser = Account(uid = "other456", name = "Other User", email = "other@test.com", handle = "other_user")

  private val testSpaceRenter = SpaceRenter(
    id = "renter123",
    owner = testOwner,
    name = "Test Space",
    phone = "1234567890",
    email = "space@test.com",
    website = "https://example.com",
    address = Location(0.0, 0.0, "Test Address"),
    openingHours = emptyList(),
    spaces = emptyList(),
    photoCollectionUrl = listOf("https://example.com/old1.jpg", "https://example.com/old2.jpg")
  )

  @Before
  fun setUp() {
    mockkObject(RepositoryProvider)
    
    // Use real OfflineModeManager but force online/offline state
    OfflineModeManager.setNetworkStatusForTesting(true)
    OfflineModeManager.clearOfflineMode() // Ensure clean state

    fakeSpaceRenterRepository = FakeSpaceRenterRepository()
    fakeSpaceRenterRepository.spaceRenterToReturn = testSpaceRenter
    fakeImageRepository = FakeImageRepository()

    every { RepositoryProvider.spaceRenters } returns fakeSpaceRenterRepository
    every { RepositoryProvider.images } returns fakeImageRepository

    viewModel = EditSpaceRenterViewModel(fakeSpaceRenterRepository, fakeImageRepository)
    viewModel.initialize(testSpaceRenter)
    // Wait for async initialization to complete to avoid race conditions in tearDown
    Thread.sleep(200)
  }

  @After
  fun tearDown() {
    unmockkAll()
    OfflineModeManager.clearOfflineMode()
  }

  @Test
  fun updateSpaceRenter_updatesRepository_whenOnline() = runBlocking {
    val newName = "Updated Space Name"
    val newImages = listOf("https://example.com/old1.jpg", "local/path/image.jpg")
    val context = ApplicationProvider.getApplicationContext<Context>()

    viewModel.updateSpaceRenter(
      context = context,
      spaceRenter = testSpaceRenter,
      requester = testOwner,
      name = newName,
      photoCollectionUrl = newImages,
      // Provide dummy required fields suitable for validation
      openingHours = List(7) { index -> OpeningHours(day = index) }, 
      address = Location(1.0, 1.0, "New Address")
    )

    assertEquals(newName, fakeSpaceRenterRepository.lastUpdatedName)
    // Check that local path was "uploaded" (replaced by fake URL) and old1 kept
    assertTrue(fakeSpaceRenterRepository.lastUpdatedPhotos?.contains("https://example.com/old1.jpg") == true)
    // The fake image repo turns "local/path/..." into "https://example.com/spacerenter/renter123/photo_1.webp" approx
    // We can check if it contains *a* new url
    assertTrue(fakeSpaceRenterRepository.lastUpdatedPhotos?.any { it.contains("spacerenter") } == true)
    
    // Verify removed photo ("old2.jpg") is gone from the update payload
    assertTrue(fakeSpaceRenterRepository.lastUpdatedPhotos?.contains("https://example.com/old2.jpg") == false)
  }

  @Test
  fun deleteSpaceRenter_deletesFromRepository() = runBlocking {
    viewModel.deleteSpaceRenter(testSpaceRenter, testOwner)
    
    // Poll for deletion since it happens in viewModelScope.launch
    var retries = 0
    while (fakeSpaceRenterRepository.lastDeletedId == null && retries < 20) {
        Thread.sleep(50)
        retries++
    }
    
    assertEquals(testSpaceRenter.id, fakeSpaceRenterRepository.lastDeletedId)
  }

  // A manual fake allows us to bypass "IncompatibleClassChangeError" seen with Mockk+Lists sometimes
  // and gives us full control over logic without complex mocking chains.
  class FakeSpaceRenterRepository : SpaceRenterRepository() {
    var lastUpdatedName: String? = null
    var lastUpdatedPhotos: List<String>? = null
    var lastDeletedId: String? = null

    var spaceRenterToReturn: SpaceRenter? = null

    override suspend fun updateSpaceRenter(
      id: String,
      ownerId: String?,
      name: String?,
      phone: String?,
      email: String?,
      website: String?,
      address: Location?,
      openingHours: List<OpeningHours>?,
      spaces: List<Space>?,
      photoCollectionUrl: List<String>?
    ) {
      this.lastUpdatedName = name
      this.lastUpdatedPhotos = photoCollectionUrl
    }

    override suspend fun deleteSpaceRenter(id: String) {
      this.lastDeletedId = id
    }

    override suspend fun getSpaceRenterSafe(renterId: String): SpaceRenter? {
      return spaceRenterToReturn
    }

    override suspend fun getSpaceRenter(id: String): SpaceRenter {
        return spaceRenterToReturn ?: throw IllegalArgumentException("Not found")
    }
  }
}

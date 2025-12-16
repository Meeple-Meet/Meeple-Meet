package com.github.meeplemeet

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.model.MainActivityViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityIntegrationTest {

  @Test
  fun mainActivity_launches() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        assertNotNull(activity)
        assertNotNull(activity.syncScope)
      }
    }
  }

  @Test
  fun mainActivity_hasSyncScope() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity -> assertNotNull(activity.syncScope) }
    }
  }
}

@RunWith(AndroidJUnit4::class)
class MainActivityViewModelFactoryIntegrationTest {

  @Test
  fun factory_createsViewModel_withInTestsTrue() {
    val factory = MainActivityViewModelFactory(inTests = true)

    val viewModel = factory.create(MainActivityViewModel::class.java)

    assertNotNull(viewModel)
    assertTrue(viewModel is MainActivityViewModel)
  }

  @Test
  fun factory_createsViewModel_withInTestsFalse() {
    val factory = MainActivityViewModelFactory(inTests = false)

    val viewModel = factory.create(MainActivityViewModel::class.java)

    assertNotNull(viewModel)
    assertTrue(viewModel is MainActivityViewModel)
  }

  @Test
  fun factory_throwsException_forInvalidViewModel() {
    val factory = MainActivityViewModelFactory(inTests = true)

    try {
      factory.create(InvalidViewModel::class.java)
      throw AssertionError("Expected IllegalArgumentException")
    } catch (e: IllegalArgumentException) {
      assertEquals("Unknown ViewModel class", e.message)
    }
  }

  private class InvalidViewModel : androidx.lifecycle.ViewModel()
}

@RunWith(AndroidJUnit4::class)
class RepositoryProviderIntegrationTest {

  @Test
  fun repositoryProvider_providesAuthentication() {
    val auth1 = RepositoryProvider.authentication
    val auth2 = RepositoryProvider.authentication

    assertNotNull(auth1)
    assertEquals(auth1, auth2)
  }

  @Test
  fun repositoryProvider_providesHandles() {
    val handles1 = RepositoryProvider.handles
    val handles2 = RepositoryProvider.handles

    assertNotNull(handles1)
    assertEquals(handles1, handles2)
  }

  @Test
  fun repositoryProvider_providesAccounts() {
    val accounts1 = RepositoryProvider.accounts
    val accounts2 = RepositoryProvider.accounts

    assertNotNull(accounts1)
    assertEquals(accounts1, accounts2)
  }

  @Test
  fun repositoryProvider_providesDiscussions() {
    val discussions1 = RepositoryProvider.discussions
    val discussions2 = RepositoryProvider.discussions

    assertNotNull(discussions1)
    assertEquals(discussions1, discussions2)
  }

  @Test
  fun repositoryProvider_providesSessions() {
    val sessions1 = RepositoryProvider.sessions
    val sessions2 = RepositoryProvider.sessions

    assertNotNull(sessions1)
    assertEquals(sessions1, sessions2)
  }

  @Test
  fun repositoryProvider_providesLocations() {
    val locations1 = RepositoryProvider.locations
    val locations2 = RepositoryProvider.locations

    assertNotNull(locations1)
    assertEquals(locations1, locations2)
  }

  @Test
  fun repositoryProvider_providesGeoPins() {
    val geoPins1 = RepositoryProvider.geoPins
    val geoPins2 = RepositoryProvider.geoPins

    assertNotNull(geoPins1)
    assertEquals(geoPins1, geoPins2)
  }

  @Test
  fun repositoryProvider_providesMarkerPreviews() {
    val markerPreviews1 = RepositoryProvider.markerPreviews
    val markerPreviews2 = RepositoryProvider.markerPreviews

    assertNotNull(markerPreviews1)
    assertEquals(markerPreviews1, markerPreviews2)
  }

  @Test
  fun repositoryProvider_providesPosts() {
    val posts1 = RepositoryProvider.posts
    val posts2 = RepositoryProvider.posts

    assertNotNull(posts1)
    assertEquals(posts1, posts2)
  }

  @Test
  fun repositoryProvider_providesShops() {
    val shops1 = RepositoryProvider.shops
    val shops2 = RepositoryProvider.shops

    assertNotNull(shops1)
    assertEquals(shops1, shops2)
  }

  @Test
  fun repositoryProvider_providesSpaceRenters() {
    val spaceRenters1 = RepositoryProvider.spaceRenters
    val spaceRenters2 = RepositoryProvider.spaceRenters

    assertNotNull(spaceRenters1)
    assertEquals(spaceRenters1, spaceRenters2)
  }

  @Test
  fun repositoryProvider_providesImages() {
    val images1 = RepositoryProvider.images
    val images2 = RepositoryProvider.images

    assertNotNull(images1)
    assertEquals(images1, images2)
  }
}

@RunWith(AndroidJUnit4::class)
class HttpClientProviderIntegrationTest {

  @Test
  fun httpClientProvider_providesClient() {
    val client = HttpClientProvider.client

    assertNotNull(client)
  }

  @Test
  fun httpClientProvider_allowsClientReplacement() {
    val originalClient = HttpClientProvider.client
    val newClient = okhttp3.OkHttpClient()

    HttpClientProvider.client = newClient

    assertEquals(newClient, HttpClientProvider.client)

    // Restore
    HttpClientProvider.client = originalClient
  }
}

package com.github.meeplemeet.api

import com.github.meeplemeet.model.LocationSearchException
import com.github.meeplemeet.model.map.NominatimLocationRepository
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class NominatimLocationRepositoryTest {

  private lateinit var mockWebServer: MockWebServer
  private lateinit var repository: NominatimLocationRepository

  @Before
  fun setup() {
    mockWebServer = MockWebServer()
    mockWebServer.start()

    val client = OkHttpClient()
    val baseUrl = mockWebServer.url("/")
    repository = NominatimLocationRepository(client, baseUrl)
  }

  @After
  fun teardown() {
    mockWebServer.shutdown()
  }

  @Test
  fun searchReturnsParsedLocations() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        [
          {
            "lat": "46.5191",
            "lon": "6.5668",
            "display_name": "EPFL, Lausanne, Switzerland"
          },
          {
            "lat": "46.5235",
            "lon": "6.5796",
            "display_name": "UNIL, Lausanne, Switzerland"
          }
        ]
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val results = repository.search("Lausanne")

    assertEquals(2, results.size)
    assertEquals("EPFL, Lausanne, Switzerland", results[0].name)
    assertEquals("UNIL, Lausanne, Switzerland", results[1].name)
  }

  @Test
  fun searchThrowsExceptionOnHttpError() = runTest {
    val mockResponse = MockResponse().setResponseCode(500)
    mockWebServer.enqueue(mockResponse)

    val exception = assertFailsWith<LocationSearchException> { repository.search("Lausanne") }

    assertTrue(exception.message!!.contains("Nominatim request failed"))
  }

  @Test
  fun searchUsesCacheForRepeatedQueries() = runTest {
    val mockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
        [
          {
            "lat": "46.5191",
            "lon": "6.5668",
            "display_name": "EPFL, Lausanne, Switzerland"
          }
        ]
      """
                    .trimIndent())

    mockWebServer.enqueue(mockResponse)

    val firstCall = repository.search("EPFL")
    val secondCall = repository.search("EPFL")

    assertEquals(1, mockWebServer.requestCount)
    assertEquals(firstCall, secondCall)
  }

  @Test
  fun searchReturnsEmptyListOnEmptyResponse() = runTest {
    val mockResponse = MockResponse().setResponseCode(200).setBody("[]")

    mockWebServer.enqueue(mockResponse)

    val results = repository.search("UnknownPlace")
    assertEquals(0, results.size)
  }

  @Test
  fun searchReturnsEmptyListOnMissingBody() = runTest {
    val mockResponse = MockResponse().setResponseCode(200).setBody("")

    mockWebServer.enqueue(mockResponse)

    val exception = assertFailsWith<LocationSearchException> { repository.search("EmptyBody") }

    assertTrue(exception.message!!.contains("Failed to parse location response"))
  }

  @Test
  fun searchThrowsExceptionOnMalformedJson() = runTest {
    val mockResponse = MockResponse().setResponseCode(200).setBody("{ invalid json")

    mockWebServer.enqueue(mockResponse)

    val exception = assertFailsWith<LocationSearchException> { repository.search("Broken") }

    assertTrue(exception.message!!.contains("Failed to parse location response"))
  }

  @Test
  fun searchDoesNotCacheFailedRequests() = runTest {
    val errorResponse = MockResponse().setResponseCode(500)
    val successResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
      [
        {
          "lat": "46.5191",
          "lon": "6.5668",
          "display_name": "EPFL, Lausanne, Switzerland"
        }
      ]
    """
                    .trimIndent())

    mockWebServer.enqueue(errorResponse)
    mockWebServer.enqueue(successResponse)

    assertFailsWith<LocationSearchException> { repository.search("EPFL") }

    val results = repository.search("EPFL")
    assertEquals(1, results.size)
    assertEquals("EPFL, Lausanne, Switzerland", results[0].name)
  }
}

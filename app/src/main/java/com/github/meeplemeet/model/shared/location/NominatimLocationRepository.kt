package com.github.meeplemeet.model.shared.location

import com.github.meeplemeet.HttpClientProvider
import com.github.meeplemeet.model.LocationSearchException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException

/**
 * Implementation of [LocationRepository] using the Nominatim API.
 *
 * Performs location search queries and parses results into [Location] objects. Includes in-memory
 * caching to avoid repeated requests for the same query. Complies with Nominatim's usage policy by
 * setting a proper User-Agent header.
 *
 * The [baseUrl] parameter is configurable for testing purposes. In production, it defaults to the
 * official Nominatim endpoint.
 *
 * Note: Cache is local to the app instance and cleared on app restart.
 */
class NominatimLocationRepository(
    private val client: OkHttpClient = HttpClientProvider.client,
    private val baseUrl: HttpUrl = DEFAULT_URL
) : LocationRepository {

  /**
   * Constants used for Nominatim API requests.
   *
   * Includes the default base URL, user-agent string (required by Nominatim usage policy), and
   * optional referer header.
   *
   * See: https://operations.osmfoundation.org/policies/nominatim/
   */
  companion object {
    private const val APP_NAME = "MeepleMeet"
    private const val APP_VERSION = "1.0"
    private const val CONTACT = "thomas.picart90@gmail.com"
    private const val REFERER = ""
    private const val USER_AGENT = "$APP_NAME/$APP_VERSION ($CONTACT)"

    /** Default base URL for Nominatim API (used in production) */
    val DEFAULT_URL: HttpUrl =
        HttpUrl.Builder().scheme("https").host("nominatim.openstreetmap.org").build()
  }

  // // LRU cache for up to 50 queries (max 10 suggestions each) to reduce network calls
  private val cache =
      object : LinkedHashMap<String, List<Location>>(16, 0.75f, true) {
        private val MAX_SIZE = 50

        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Location>>?) =
            size > MAX_SIZE
      }

  // Mutex for thread-safe access to cache
  private val cacheMutex = Mutex()

  /**
   * Searches for geographic locations matching the given query string.
   *
   * Results are cached in memory to reduce network usage and comply with the Nominatim Usage
   * Policy.
   *
   * @param query The location name or description (e.g. "Paris", "Meeple Café Lyon").
   * @return A list of [Location] results ordered by relevance.
   * @throws LocationSearchException if the request fails or the response is invalid.
   */
  override suspend fun search(query: String): List<Location> =
      withContext(Dispatchers.IO) {

        // Check cache first
        cacheMutex.withLock {
          cache[query]?.let {
            return@withContext it
          }
        }

        val url =
            baseUrl
                .newBuilder()
                .addPathSegment("search")
                .addQueryParameter("q", query)
                .addQueryParameter("format", "json")
                .addQueryParameter("limit", "5")
                .build()

        val builder = Request.Builder().url(url).header("User-Agent", USER_AGENT)

        if (REFERER.isNotBlank()) builder.addHeader("Referer", REFERER)

        val request = builder.build()

        try {
          val response = client.newCall(request).execute()
          response.use {
            if (!response.isSuccessful) {
              throw LocationSearchException("Nominatim request failed: ${response.code}")
            }

            val body = response.body?.string()
            val results =
                try {
                  if (body != null) parseBody(body) else emptyList()
                } catch (e: JSONException) {
                  throw LocationSearchException("Failed to parse location response: ${e.message}")
                }

            // Update cache
            cacheMutex.withLock { cache[query] = results }

            return@withContext results
          }
        } catch (e: IOException) {
          throw LocationSearchException("Failed to search location: ${e.message}")
        }
      }

  /**
   * Parses a JSON response from Nominatim into a list of [Location] objects.
   *
   * @param body the raw JSON string returned by the API
   * @return a list of [Location]
   */
  private fun parseBody(body: String): List<Location> {
    val jsonArray = JSONArray(body)

    return List(jsonArray.length()) { i ->
      val jsonObject = jsonArray.getJSONObject(i)
      val lat = jsonObject.getDouble("lat")
      val lon = jsonObject.getDouble("lon")
      val name = jsonObject.getString("display_name")
      Location(lat, lon, name)
    }
  }
}

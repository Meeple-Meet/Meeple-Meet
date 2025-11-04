package com.github.meeplemeet.model.map

import com.github.meeplemeet.model.LocationSearchException
import com.github.meeplemeet.model.shared.Location
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

class NominatimLocationRepository(private val client: OkHttpClient) : LocationRepository {

  /**
   * User-Agent header required by Nominatim usage policy. Referer is optional but recommended. See
   * https://operations.osmfoundation.org/policies/nominatim/
   */
  companion object {
    private const val APP_NAME = "MeepleMeet"
    private const val APP_VERSION = "1.0"
    private const val CONTACT = "thomas.picart90@gmail.com"
    private const val REFERER = ""
    private val USER_AGENT = "$APP_NAME/$APP_VERSION ($CONTACT)"
  }

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

  override suspend fun search(query: String): List<Location> =
      withContext(Dispatchers.IO) {
        val url =
            HttpUrl.Builder()
                .scheme("https")
                .host("nominatim.openstreetmap.org")
                .addPathSegment("search")
                .addQueryParameter("q", query)
                .addQueryParameter("format", "json")
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
            return@withContext if (body != null) {
              parseBody(body)
            } else {
              emptyList()
            }
          }
        } catch (e: IOException) {
          throw LocationSearchException("Failed to search location: ${e.message}", e)
        }
      }
}

package com.antigravity.screensaver.data

import com.antigravity.screensaver.model.WeatherInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class WeatherRepository {

    private var cachedWeather: WeatherInfo? = null
    private var lastFetchTimeMs: Long = 0
    private val cacheDurationMs: Long = 30 * 60 * 1000 // 30 minutes

    suspend fun getWeather(cityName: String, useFahrenheit: Boolean): WeatherInfo = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedWeather != null && (now - lastFetchTimeMs < cacheDurationMs) && cachedWeather?.cityName == cityName) {
            return@withContext cachedWeather!!
        }

        try {
            var lat = 40.7128 // Default NYC
            var lon = -74.0060
            var resolvedName = "Local"

            if (cityName.isNotBlank()) {
                val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(cityName, "UTF-8")}&count=1"
                val geoJson = httpGet(geoUrl)
                if (geoJson != null) {
                    val root = JSONObject(geoJson)
                    val results = root.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        val first = results.getJSONObject(0)
                        lat = first.getDouble("latitude")
                        lon = first.getDouble("longitude")
                        resolvedName = first.optString("name", cityName)
                    }
                }
            }

            val tempUnit = if (useFahrenheit) "&temperature_unit=fahrenheit" else ""
            val forecastUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto$tempUnit"
            val forecastJson = httpGet(forecastUrl)

            if (forecastJson != null) {
                val root = JSONObject(forecastJson)
                val current = root.optJSONObject("current")
                val daily = root.optJSONObject("daily")
                if (current != null) {
                    val temp = current.getDouble("temperature_2m").toFloat()
                    val code = current.getInt("weather_code")
                    val condition = mapWmoCode(code)

                    var tomorrowCode = 0
                    var tomorrowMax = 0f
                    var tomorrowMin = 0f
                    var tomorrowCondition = ""
                    var hasTomorrow = false

                    if (daily != null) {
                        val dailyCodes = daily.optJSONArray("weather_code")
                        val dailyMax = daily.optJSONArray("temperature_2m_max")
                        val dailyMin = daily.optJSONArray("temperature_2m_min")
                        if (dailyCodes != null && dailyCodes.length() > 1 &&
                            dailyMax != null && dailyMax.length() > 1 &&
                            dailyMin != null && dailyMin.length() > 1) {
                            tomorrowCode = dailyCodes.getInt(1)
                            tomorrowMax = dailyMax.getDouble(1).toFloat()
                            tomorrowMin = dailyMin.getDouble(1).toFloat()
                            tomorrowCondition = mapWmoCode(tomorrowCode)
                            hasTomorrow = true
                        }
                    }

                    val info = WeatherInfo(
                        temperature = temp,
                        weatherCode = code,
                        conditionDescription = condition,
                        cityName = resolvedName,
                        isLoaded = true,
                        tomorrowWeatherCode = tomorrowCode,
                        tomorrowTempMax = tomorrowMax,
                        tomorrowTempMin = tomorrowMin,
                        tomorrowConditionDescription = tomorrowCondition,
                        hasTomorrowForecast = hasTomorrow
                    )
                    cachedWeather = info
                    lastFetchTimeMs = now
                    return@withContext info
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        cachedWeather ?: WeatherInfo(
            temperature = if (useFahrenheit) 72f else 22f,
            weatherCode = 0,
            conditionDescription = "Clear",
            cityName = if (cityName.isNotBlank()) cityName else "Local",
            isLoaded = false,
            tomorrowWeatherCode = 0,
            tomorrowTempMax = if (useFahrenheit) 75f else 24f,
            tomorrowTempMin = if (useFahrenheit) 55f else 13f,
            tomorrowConditionDescription = "Partly Cloudy",
            hasTomorrowForecast = true
        )
    }

    private fun httpGet(urlStr: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", "PixelSmartDisplay/1.0")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                reader.close()
                sb.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun mapWmoCode(code: Int): String {
        return when (code) {
            0 -> "Clear Sky"
            1, 2, 3 -> "Partly Cloudy"
            45, 48 -> "Foggy"
            51, 53, 55 -> "Drizzle"
            61, 63, 65 -> "Rain"
            71, 73, 75, 77 -> "Snow"
            80, 81, 82 -> "Showers"
            85, 86 -> "Snow Showers"
            95, 96, 99 -> "Thunderstorm"
            else -> "Fair"
        }
    }
}

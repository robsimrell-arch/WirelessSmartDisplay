package com.antigravity.screensaver.data

import android.content.Context
import com.antigravity.screensaver.model.WeatherInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class WeatherRepository(context: Context? = null) {

    private val prefs = context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var cachedBaseWeather: WeatherInfo? = null
    private var cachedCityQuery: String = ""
    private var lastFetchTimeMs: Long = 0
    private val cacheDurationMs: Long = 30 * 60 * 1000 // 30 minutes

    init {
        loadPersistedWeather()
    }

    private fun loadPersistedWeather() {
        val p = prefs ?: return
        if (!p.getBoolean(KEY_HAS_CACHED_WEATHER, false)) return
        cachedCityQuery = p.getString(KEY_CACHED_QUERY, "") ?: ""
        lastFetchTimeMs = p.getLong(KEY_LAST_FETCH_MS, 0L)
        cachedBaseWeather = WeatherInfo(
            temperature = p.getFloat(KEY_TEMP_C, 20f),
            weatherCode = p.getInt(KEY_WEATHER_CODE, 0),
            conditionDescription = p.getString(KEY_CONDITION, "Clear Sky") ?: "Clear Sky",
            cityName = p.getString(KEY_RESOLVED_CITY, cachedCityQuery.ifBlank { "Local" }) ?: "Local",
            isLoaded = true,
            todayTempMax = p.getFloat(KEY_TODAY_MAX_C, 22f),
            todayTempMin = p.getFloat(KEY_TODAY_MIN_C, 10f),
            hasTodayHiLo = p.getBoolean(KEY_HAS_TODAY, true),
            tomorrowWeatherCode = p.getInt(KEY_TOMORROW_CODE, 0),
            tomorrowTempMax = p.getFloat(KEY_TOMORROW_MAX_C, 22f),
            tomorrowTempMin = p.getFloat(KEY_TOMORROW_MIN_C, 10f),
            tomorrowConditionDescription = p.getString(KEY_TOMORROW_CONDITION, "Clear Sky") ?: "Clear Sky",
            hasTomorrowForecast = p.getBoolean(KEY_HAS_TOMORROW, true),
            isFahrenheit = false
        )
    }

    private fun persistWeather(query: String, info: WeatherInfo, fetchTimeMs: Long) {
        val p = prefs ?: return
        p.edit()
            .putBoolean(KEY_HAS_CACHED_WEATHER, true)
            .putString(KEY_CACHED_QUERY, query)
            .putLong(KEY_LAST_FETCH_MS, fetchTimeMs)
            .putFloat(KEY_TEMP_C, info.temperature)
            .putInt(KEY_WEATHER_CODE, info.weatherCode)
            .putString(KEY_CONDITION, info.conditionDescription)
            .putString(KEY_RESOLVED_CITY, info.cityName)
            .putFloat(KEY_TODAY_MAX_C, info.todayTempMax)
            .putFloat(KEY_TODAY_MIN_C, info.todayTempMin)
            .putBoolean(KEY_HAS_TODAY, info.hasTodayHiLo)
            .putInt(KEY_TOMORROW_CODE, info.tomorrowWeatherCode)
            .putFloat(KEY_TOMORROW_MAX_C, info.tomorrowTempMax)
            .putFloat(KEY_TOMORROW_MIN_C, info.tomorrowTempMin)
            .putString(KEY_TOMORROW_CONDITION, info.tomorrowConditionDescription)
            .putBoolean(KEY_HAS_TOMORROW, info.hasTomorrowForecast)
            .apply()
    }

    /**
     * Returns the best immediate weather state for initial UI composition so the widget
     * never flashes "Local / 32°F" while waiting for the network.
     */
    fun getInitialWeather(cityName: String, useFahrenheit: Boolean): WeatherInfo {
        val trimmedCity = cityName.trim()
        val cached = cachedBaseWeather
        if (cached != null && (trimmedCity.isBlank() || cachedCityQuery.equals(trimmedCity, ignoreCase = true))) {
            return cached.forUnit(useFahrenheit)
        }
        return WeatherInfo(
            temperature = if (useFahrenheit) 68f else 20f,
            weatherCode = 0,
            conditionDescription = "Loading...",
            cityName = trimmedCity.ifBlank { cached?.cityName ?: "Local" },
            isLoaded = false,
            hasTodayHiLo = false,
            hasTomorrowForecast = false,
            isFahrenheit = useFahrenheit
        )
    }

    suspend fun getWeather(cityName: String, useFahrenheit: Boolean): WeatherInfo = withContext(Dispatchers.IO) {
        val trimmedCity = cityName.trim()
        val now = System.currentTimeMillis()
        if (cachedBaseWeather != null &&
            (now - lastFetchTimeMs < cacheDurationMs) &&
            cachedCityQuery.equals(trimmedCity, ignoreCase = true)
        ) {
            return@withContext cachedBaseWeather!!.forUnit(useFahrenheit)
        }

        try {
            var lat: Double? = null
            var lon: Double? = null
            var resolvedName: String = trimmedCity.ifBlank { "Local" }

            if (trimmedCity.isNotBlank()) {
                // 1. Check persisted coordinates for this exact city query first
                val savedGeoQuery = prefs?.getString(KEY_GEO_QUERY, null)
                if (savedGeoQuery != null && savedGeoQuery.equals(trimmedCity, ignoreCase = true) &&
                    prefs.contains(KEY_GEO_LAT) && prefs.contains(KEY_GEO_LON)
                ) {
                    lat = Double.fromBits(prefs.getLong(KEY_GEO_LAT, 0L))
                    lon = Double.fromBits(prefs.getLong(KEY_GEO_LON, 0L))
                    resolvedName = prefs.getString(KEY_GEO_NAME, trimmedCity) ?: trimmedCity
                } else {
                    // 2. Geocode via Open-Meteo (with 1 retry on transient timeout)
                    val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(trimmedCity, "UTF-8")}&count=1"
                    val geoJson = httpGet(geoUrl) ?: httpGet(geoUrl)
                    if (geoJson != null) {
                        val root = JSONObject(geoJson)
                        val results = root.optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            val first = results.getJSONObject(0)
                            lat = first.getDouble("latitude")
                            lon = first.getDouble("longitude")
                            resolvedName = first.optString("name", trimmedCity)
                            prefs?.edit()
                                ?.putString(KEY_GEO_QUERY, trimmedCity)
                                ?.putLong(KEY_GEO_LAT, lat.toRawBits())
                                ?.putLong(KEY_GEO_LON, lon.toRawBits())
                                ?.putString(KEY_GEO_NAME, resolvedName)
                                ?.apply()
                        }
                    }
                }
            } else {
                // Blank city -> Auto-detect local coordinates via IP geolocation
                val ipJson = httpGet("https://ipwho.is/")
                if (ipJson != null) {
                    val root = JSONObject(ipJson)
                    if (root.optBoolean("success", true) && root.has("latitude") && root.has("longitude")) {
                        lat = root.getDouble("latitude")
                        lon = root.getDouble("longitude")
                        resolvedName = root.optString("city", "Local").ifBlank { "Local" }
                    }
                }
            }

            // Never fall back to hardcoded NYC when a custom city failed to geocode!
            if (lat == null || lon == null) {
                if (cachedBaseWeather != null && cachedCityQuery.equals(trimmedCity, ignoreCase = true)) {
                    return@withContext cachedBaseWeather!!.forUnit(useFahrenheit)
                }
                return@withContext getInitialWeather(trimmedCity, useFahrenheit)
            }

            // Always fetch standard Celsius from Open-Meteo and convert dynamically for perfect unit switching
            val forecastUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto"
            val forecastJson = httpGet(forecastUrl) ?: httpGet(forecastUrl)

            if (forecastJson != null) {
                val root = JSONObject(forecastJson)
                val current = root.optJSONObject("current")
                val daily = root.optJSONObject("daily")
                if (current != null) {
                    val temp = current.getDouble("temperature_2m").toFloat()
                    val code = current.getInt("weather_code")
                    val condition = mapWmoCode(code)

                    var todayMax = 0f
                    var todayMin = 0f
                    var hasToday = false
                    var tomorrowCode = 0
                    var tomorrowMax = 0f
                    var tomorrowMin = 0f
                    var tomorrowCondition = ""
                    var hasTomorrow = false

                    if (daily != null) {
                        val dailyCodes = daily.optJSONArray("weather_code")
                        val dailyMax = daily.optJSONArray("temperature_2m_max")
                        val dailyMin = daily.optJSONArray("temperature_2m_min")
                        if (dailyMax != null && dailyMax.length() > 0 &&
                            dailyMin != null && dailyMin.length() > 0) {
                            todayMax = dailyMax.getDouble(0).toFloat()
                            todayMin = dailyMin.getDouble(0).toFloat()
                            hasToday = true
                        }
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
                        todayTempMax = todayMax,
                        todayTempMin = todayMin,
                        hasTodayHiLo = hasToday,
                        tomorrowWeatherCode = tomorrowCode,
                        tomorrowTempMax = tomorrowMax,
                        tomorrowTempMin = tomorrowMin,
                        tomorrowConditionDescription = tomorrowCondition,
                        hasTomorrowForecast = hasTomorrow,
                        isFahrenheit = false
                    )
                    cachedBaseWeather = info
                    cachedCityQuery = trimmedCity
                    lastFetchTimeMs = now
                    persistWeather(trimmedCity, info, now)
                    return@withContext info.forUnit(useFahrenheit)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (cachedBaseWeather != null && cachedCityQuery.equals(trimmedCity, ignoreCase = true)) {
            return@withContext cachedBaseWeather!!.forUnit(useFahrenheit)
        }
        getInitialWeather(trimmedCity, useFahrenheit)
    }

    private fun httpGet(urlStr: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
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

    companion object {
        private const val PREFS_NAME = "weather_cache_prefs"
        private const val KEY_HAS_CACHED_WEATHER = "key_has_cached_weather"
        private const val KEY_CACHED_QUERY = "key_cached_query"
        private const val KEY_LAST_FETCH_MS = "key_last_fetch_ms"
        private const val KEY_TEMP_C = "key_temp_c"
        private const val KEY_WEATHER_CODE = "key_weather_code"
        private const val KEY_CONDITION = "key_condition"
        private const val KEY_RESOLVED_CITY = "key_resolved_city"
        private const val KEY_TODAY_MAX_C = "key_today_max_c"
        private const val KEY_TODAY_MIN_C = "key_today_min_c"
        private const val KEY_HAS_TODAY = "key_has_today"
        private const val KEY_TOMORROW_CODE = "key_tomorrow_code"
        private const val KEY_TOMORROW_MAX_C = "key_tomorrow_max_c"
        private const val KEY_TOMORROW_MIN_C = "key_tomorrow_min_c"
        private const val KEY_TOMORROW_CONDITION = "key_tomorrow_condition"
        private const val KEY_HAS_TOMORROW = "key_has_tomorrow"
        private const val KEY_GEO_QUERY = "key_geo_query"
        private const val KEY_GEO_LAT = "key_geo_lat"
        private const val KEY_GEO_LON = "key_geo_lon"
        private const val KEY_GEO_NAME = "key_geo_name"
    }
}

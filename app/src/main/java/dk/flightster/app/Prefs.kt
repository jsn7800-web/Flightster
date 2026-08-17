package dk.flightster.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Indstillinger og den seneste observation.
 *
 * Widgeten viser det sidst sete fly, også når himlen er tom lige nu.
 * Derfor gemmes observationen med tidsstempel, så panelet ærligt kan sige
 * "for 12 minutter siden" i stedet for at lade som om det er live.
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("flightster", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_BASE_URL = "https://flightster.netlify.app"
        private const val KEY_BASE = "base_url"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
        private const val KEY_RADIUS = "radius"
        private const val KEY_ELEV = "elev"
        private const val KEY_AUTO_LOCATION = "auto_location"
        private const val KEY_PHOTOS = "photos"
        private const val KEY_PLACE = "place"

        private const val KEY_LAST_JSON = "last_flight"
        private const val KEY_LAST_TIME = "last_time"
        private const val KEY_LAST_CHECK = "last_check"
    }

    var baseUrl: String
        get() = sp.getString(KEY_BASE, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(v) = sp.edit().putString(KEY_BASE, v.trim().trimEnd('/')).apply()

    var latitude: Double
        get() = java.lang.Double.longBitsToDouble(
            sp.getLong(KEY_LAT, java.lang.Double.doubleToRawLongBits(56.5670))
        )
        set(v) = sp.edit().putLong(KEY_LAT, java.lang.Double.doubleToRawLongBits(v)).apply()

    var longitude: Double
        get() = java.lang.Double.longBitsToDouble(
            sp.getLong(KEY_LON, java.lang.Double.doubleToRawLongBits(9.0280))
        )
        set(v) = sp.edit().putLong(KEY_LON, java.lang.Double.doubleToRawLongBits(v)).apply()

    var radiusKm: Int
        get() = sp.getInt(KEY_RADIUS, 90)
        set(v) = sp.edit().putInt(KEY_RADIUS, v).apply()

    var minElevation: Int
        get() = sp.getInt(KEY_ELEV, 2)
        set(v) = sp.edit().putInt(KEY_ELEV, v).apply()

    var autoLocation: Boolean
        get() = sp.getBoolean(KEY_AUTO_LOCATION, true)
        set(v) = sp.edit().putBoolean(KEY_AUTO_LOCATION, v).apply()

    var showPhotos: Boolean
        get() = sp.getBoolean(KEY_PHOTOS, true)
        set(v) = sp.edit().putBoolean(KEY_PHOTOS, v).apply()

    var placeName: String
        get() = sp.getString(KEY_PLACE, "") ?: ""
        set(v) = sp.edit().putString(KEY_PLACE, v).apply()

    /** Tidspunktet for seneste forsøg, uanset om der var et fly. */
    var lastCheck: Long
        get() = sp.getLong(KEY_LAST_CHECK, 0L)
        set(v) = sp.edit().putLong(KEY_LAST_CHECK, v).apply()

    fun saveFlight(f: Flight) {
        val json = org.json.JSONObject().apply {
            put("cs", f.callsign)
            put("reg", f.registration ?: org.json.JSONObject.NULL)
            put("type", f.type ?: org.json.JSONObject.NULL)
            put("from", f.from ?: org.json.JSONObject.NULL)
            put("to", f.to ?: org.json.JSONObject.NULL)
            put("airline", f.airline ?: org.json.JSONObject.NULL)
            put("alt_m", f.altitudeM)
            put("alt_ft", f.altitudeFt)
            put("kmh", f.speedKmh ?: org.json.JSONObject.NULL)
            put("kt", f.speedKt ?: org.json.JSONObject.NULL)
            put("dist_km", f.distanceKm)
            put("elev", f.elevation)
            put("compass", f.compass)
            put("photo", f.photoUrl ?: org.json.JSONObject.NULL)
            put("photo_credit", f.photoCredit ?: org.json.JSONObject.NULL)
        }
        sp.edit()
            .putString(KEY_LAST_JSON, json.toString())
            .putLong(KEY_LAST_TIME, f.seenAtMillis)
            .apply()
    }

    fun loadFlight(): Flight? {
        val raw = sp.getString(KEY_LAST_JSON, null) ?: return null
        return try {
            val j = org.json.JSONObject(raw)
            fun s(k: String) = if (j.isNull(k)) null else j.optString(k).ifEmpty { null }
            fun i(k: String) = if (j.isNull(k)) null else j.optInt(k)
            Flight(
                callsign = j.optString("cs"),
                registration = s("reg"),
                type = s("type"),
                from = s("from"),
                to = s("to"),
                airline = s("airline"),
                altitudeM = j.optInt("alt_m"),
                altitudeFt = j.optInt("alt_ft"),
                speedKmh = i("kmh"),
                speedKt = i("kt"),
                distanceKm = j.optDouble("dist_km"),
                elevation = j.optInt("elev"),
                compass = j.optString("compass"),
                photoUrl = s("photo"),
                photoCredit = s("photo_credit"),
                seenAtMillis = sp.getLong(KEY_LAST_TIME, 0L)
            )
        } catch (_: Exception) {
            null
        }
    }
}

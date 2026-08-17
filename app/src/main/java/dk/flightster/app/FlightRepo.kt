package dk.flightster.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Ét fly, som det ser ud efter at serveren har gjort det tunge arbejde. */
data class Flight(
    val callsign: String,
    val registration: String?,
    val type: String?,
    val from: String?,
    val to: String?,
    val airline: String?,
    val altitudeM: Int,
    val altitudeFt: Int,
    val speedKmh: Int?,
    val speedKt: Int?,
    val distanceKm: Double,
    val elevation: Int,
    val compass: String,
    val seenAtMillis: Long = System.currentTimeMillis()
)

data class PhotoResult(val bitmap: Bitmap, val credit: String)

/**
 * Al netværkstrafik.
 *
 * Widgeten taler kun med /api/now på dit eget Netlify-domæne. Serveren henter
 * hele ADS-B-feedet, filtrerer på højdevinkel og vælger det fly der står
 * højest på himlen, og sender omkring 200 bytes tilbage. Telefonen slipper
 * dermed for at hente og parse hundredvis af kilobyte hvert kvarter.
 */
object FlightRepo {

    private const val TIMEOUT_MS = 12_000

    private fun readText(url: String): String? = try {
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Flightster/1.0 (Android)")
            try {
                if (responseCode !in 200..299) null
                else inputStream.bufferedReader().use { it.readText() }
            } finally {
                disconnect()
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun JSONObject.stringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = optString(key, "").trim()
        return v.ifEmpty { null }
    }

    private fun JSONObject.intOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)

    /**
     * Henter det fly der lige nu står højest over [lat],[lon].
     * Returnerer null hvis himlen er tom eller kaldet fejler — begge dele
     * er normale tilstande, ikke fejl der skal vises som fejl.
     */
    fun fetchNow(baseUrl: String, lat: Double, lon: Double, radiusKm: Int, minElev: Int): Flight? {
        val url = "${baseUrl.trimEnd('/')}/api/now" +
            "?lat=${"%.4f".format(java.util.Locale.US, lat)}" +
            "&lon=${"%.4f".format(java.util.Locale.US, lon)}" +
            "&radius=$radiusKm&elev=$minElev"

        val body = readText(url) ?: return null
        val json = try { JSONObject(body) } catch (_: Exception) { return null }
        if (!json.optBoolean("ok", false)) return null

        val callsign = json.stringOrNull("cs") ?: return null
        return Flight(
            callsign = callsign,
            registration = json.stringOrNull("reg"),
            type = json.stringOrNull("type"),
            from = json.stringOrNull("from"),
            to = json.stringOrNull("to"),
            airline = json.stringOrNull("airline"),
            altitudeM = json.optInt("alt_m", 0),
            altitudeFt = json.optInt("alt_ft", 0),
            speedKmh = json.intOrNull("kmh"),
            speedKt = json.intOrNull("kt"),
            distanceKm = json.optDouble("dist_km", 0.0),
            elevation = json.optInt("elev", 0),
            compass = json.optString("compass", "")
        )
    }

    /**
     * Foto af det præcise fly, slået op på registrering hos Planespotters.
     *
     * Billedet vises med fotografens navn, som deres vilkår kræver. Der
     * hentes bevidst en lille udgave: RemoteViews sender widgetens indhold
     * over IPC med en grænse omkring 1 MB, så et stort billede ville få
     * widgeten til at fejle tavst.
     */
    fun fetchPhoto(baseUrl: String, flight: Flight, maxWidth: Int): PhotoResult? {
        val reg = flight.registration?.trim().orEmpty()
        if (reg.isEmpty()) return null

        val url = "${baseUrl.trimEnd('/')}/api/planespotters/pub/photos/reg/$reg"
        val body = readText(url) ?: return null

        val (src, credit) = try {
            val photos = JSONObject(body).optJSONArray("photos") ?: return null
            if (photos.length() == 0) return null
            val p = photos.getJSONObject(0)
            val thumb = p.optJSONObject("thumbnail_large") ?: p.optJSONObject("thumbnail")
            val s = thumb?.optString("src").orEmpty()
            if (s.isEmpty()) return null
            s to "© ${p.optString("photographer", "ukendt")} / Planespotters.net"
        } catch (_: Exception) {
            return null
        }

        val bitmap = try {
            (URL(src).openConnection() as HttpURLConnection).run {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", "Flightster/1.0 (Android)")
                try {
                    if (responseCode !in 200..299) null
                    else inputStream.use { BitmapFactory.decodeStream(it) }
                } finally {
                    disconnect()
                }
            }
        } catch (_: Exception) {
            null
        } ?: return null

        val scaled = if (bitmap.width > maxWidth) {
            val h = (bitmap.height.toFloat() * maxWidth / bitmap.width).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, maxWidth, h, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
        } else bitmap

        return PhotoResult(scaled, credit)
    }
}

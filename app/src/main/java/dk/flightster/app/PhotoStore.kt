package dk.flightster.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Gemmer fotoet på disken i stedet for kun i hukommelsen.
 *
 * Android lukker app-processer når som helst, og widgeten bliver tegnet om
 * længe efter at baggrundsjobbet er kørt — ved genstart, ved skift af
 * skærmstørrelse, ved tema. Et billede der kun ligger i RAM er derfor væk
 * næsten hver gang det skal bruges, og widgeten ville falde tilbage på
 * stregtegningen uden nogen synlig grund.
 */
object PhotoStore {

    private const val FILE_NAME = "widget_photo.png"
    private const val PREF_CREDIT = "photo_credit"
    private const val PREF_KEY = "photo_key"

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private fun prefs(context: Context) =
        context.getSharedPreferences("flightster", Context.MODE_PRIVATE)

    /** Nøgle der binder billedet til ét bestemt fly. */
    private fun keyFor(flight: Flight) =
        flight.registration?.trim().orEmpty().ifEmpty { flight.callsign }

    @Synchronized
    fun save(context: Context, flight: Flight, photo: PhotoResult?) {
        val f = file(context)
        if (photo == null) {
            f.delete()
            prefs(context).edit().remove(PREF_CREDIT).remove(PREF_KEY).apply()
            return
        }
        try {
            f.outputStream().use { out ->
                photo.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            prefs(context).edit()
                .putString(PREF_CREDIT, photo.credit)
                .putString(PREF_KEY, keyFor(flight))
                .apply()
        } catch (_: Exception) {
            f.delete()
        }
    }

    /**
     * Henter billedet, men kun hvis det hører til netop dette fly. Ellers
     * ville widgeten kunne vise et Ryanair-fly under et SAS-callsign.
     */
    @Synchronized
    fun load(context: Context, flight: Flight): PhotoResult? {
        val p = prefs(context)
        if (p.getString(PREF_KEY, null) != keyFor(flight)) return null
        val f = file(context)
        if (!f.exists()) return null
        return try {
            val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: return null
            PhotoResult(bmp, p.getString(PREF_CREDIT, "") ?: "")
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun clear(context: Context) {
        file(context).delete()
        prefs(context).edit().remove(PREF_CREDIT).remove(PREF_KEY).apply()
    }
}

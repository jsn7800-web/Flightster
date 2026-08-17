package dk.flightster.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min

/**
 * Tegner hele widgetpanelet til ét Bitmap.
 *
 * En Android-widget kan ikke indeholde en WebView, og RemoteViews har kun et
 * snævert udvalg af komponenter. Derfor tegnes panelet i stedet på et lærred
 * og lægges i én ImageView. Det giver fuld kontrol over udseendet og gør det
 * muligt at genbruge designet fra webudgaven præcist.
 */
object WidgetRenderer {

    private const val BG = 0xFF000000.toInt()
    private const val LINE = 0xFFE6E2D9.toInt()
    private const val AMBER = 0xFFF2A63B.toInt()
    private const val MUTED = 0xFF6E6A62.toInt()
    private const val DIM = 0xFF403D38.toInt()

    private val condensed: Typeface =
        Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val condensedMedium: Typeface =
        Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    private val mono: Typeface =
        Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)

    /**
     * @param widthPx  widgetens bredde i pixels
     * @param heightPx widgetens højde i pixels
     * @param flight   seneste observation, eller null hvis der aldrig har været en
     * @param photo    foto af flyet, eller null — så bruges stregtegningen
     * @param place    stednavn i topbjælken
     * @param agoMinutes hvor længe siden observationen blev gjort
     * @param offline  true hvis der ikke har været kontakt i lang tid
     */
    fun render(
        widthPx: Int,
        heightPx: Int,
        flight: Flight?,
        photo: PhotoResult?,
        place: String,
        agoMinutes: Long,
        offline: Boolean
    ): Bitmap {
        val w = widthPx.coerceIn(160, 2400)
        val h = heightPx.coerceIn(120, 2400)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // afrundet sort baggrund
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BG }
        val radius = min(w, h) * 0.055f
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, bgPaint)

        // enhed: al typografi skaleres efter panelets størrelse, som på web
        val u = min(w / 100f, h / 62f)
        val pad = u * 4.5f

        drawHeader(c, w, u, pad, place, offline)

        if (flight == null) {
            drawEmpty(c, w, h, u, pad, offline)
            return bmp
        }

        val headerBottom = pad + u * 4.5f
        val footerTop = h - pad - u * 3.2f

        // Visuel del øverst, data nedenunder. Kvadratiske og brede widgets
        // får forskellig fordeling, så teksten aldrig klemmes.
        val wide = w.toFloat() / h > 2.0f
        // Selv en bred widget skal vise flyet — bare i en lavere stribe.
        val visualHeight = (footerTop - headerBottom) * (if (wide) 0.34f else 0.46f)
        val visualTop = headerBottom + u * 1.2f

        if (visualHeight > u * 8f) {
            drawVisual(c, flight, photo, pad, visualTop, w - pad * 2f, visualHeight, u)
        }

        val textTop = if (visualHeight > u * 8f) visualTop + visualHeight + u * 2.0f
                      else headerBottom + u * 1.5f
        drawFlight(c, flight, pad, textTop, w - pad * 2f, footerTop - textTop, u, wide)
        drawFooter(c, flight, w, footerTop, pad, u, agoMinutes)

        return bmp
    }

    private fun drawHeader(
        c: Canvas, w: Int, u: Float, pad: Float, place: String, offline: Boolean
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = mono
            textSize = u * 2.4f
            color = AMBER
            letterSpacing = 0.18f
        }
        val y = pad + u * 2.6f
        c.drawText("FLIGHTSTER", pad, y, p)
        val markWidth = p.measureText("FLIGHTSTER") + u * 2.2f

        if (place.isNotBlank()) {
            p.color = MUTED
            c.drawText(place.uppercase(), pad + markWidth, y, p)
        }

        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (offline) 0xFFC24B36.toInt() else AMBER
        }
        c.drawCircle(w - pad - u * 0.7f, y - u * 0.8f, u * 0.7f, dot)
    }

    private fun drawEmpty(c: Canvas, w: Int, h: Int, u: Float, pad: Float, offline: Boolean) {
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = condensed
            textSize = u * 7f
            color = MUTED
        }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = mono
            textSize = u * 2.3f
            color = DIM
        }
        val cy = h / 2f
        c.drawText(if (offline) "Ingen forbindelse" else "Himlen er tom", pad, cy, title)
        c.drawText(
            if (offline) "Prøver igen ved næste opdatering"
            else "Ingen fly har været over dig endnu",
            pad, cy + u * 3.4f, sub
        )
    }

    /** Foto hvis vi har et, ellers stregtegningen af typen. */
    private fun drawVisual(
        c: Canvas, f: Flight, photo: PhotoResult?,
        left: Float, top: Float, w: Float, h: Float, u: Float
    ) {
        if (photo != null) {
            val bmp = photo.bitmap
            val scale = min(w / bmp.width, h / bmp.height)
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            val dstLeft = left + (w - dw) / 2f
            val dstTop = top + (h - dh) / 2f
            val dst = RectF(dstLeft, dstTop, dstLeft + dw, dstTop + dh)
            c.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), dst, Paint(Paint.FILTER_BITMAP_FLAG))

            val credit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = mono
                textSize = u * 1.6f
                color = MUTED
            }
            val text = photo.credit
            val tw = credit.measureText(text)
            val bg = Paint().apply { color = 0x8C000000.toInt() }
            c.drawRect(
                dst.right - tw - u * 1.2f, dst.bottom - u * 2.6f,
                dst.right, dst.bottom, bg
            )
            c.drawText(text, dst.right - tw - u * 0.6f, dst.bottom - u * 0.8f, credit)
            return
        }

        val spec = AircraftDraw.specFor(f.type) ?: return
        AircraftDraw.draw(
            canvas = c, sp = spec,
            left = left, top = top, w = w, h = h,
            stroke = LINE, accent = AMBER, faint = MUTED, background = BG,
            strokeWidth = max(1.1f, u * 0.30f)
        )
    }

    private fun drawFlight(
        c: Canvas, f: Flight,
        left: Float, top: Float, w: Float, h: Float, u: Float, wide: Boolean
    ) {
        var y = top

        // callsign, skaleret ned hvis det er langt
        val cs = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = condensed
            color = Color.WHITE
            textSize = u * (if (wide) 8.5f else 9.5f)
        }
        while (cs.measureText(f.callsign) > w * 0.62f && cs.textSize > u * 4f) {
            cs.textSize -= u * 0.3f
        }
        y += cs.textSize * 0.82f
        c.drawText(f.callsign, left, y, cs)

        // kig-retningen står til højre, det er det eneste tal man handler på
        val look = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = condensed
            color = AMBER
            textSize = u * (if (wide) 6.5f else 7f)
            textAlign = Paint.Align.RIGHT
        }
        c.drawText("${f.elevation}° ${f.compass}", left + w, y, look)

        // rute
        y += u * (if (wide) 5.2f else 5.8f)
        val apt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = condensed
            color = AMBER
            textSize = u * (if (wide) 5.4f else 6f)
        }
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DIM
            strokeWidth = max(1f, u * 0.18f)
        }
        if (f.from != null && f.to != null) {
            c.drawText(f.from, left, y, apt)
            val fromW = apt.measureText(f.from)
            val ax = left + fromW + u * 2.2f
            val aw = u * 5f
            c.drawLine(ax, y - apt.textSize * 0.32f, ax + aw, y - apt.textSize * 0.32f, arrowPaint)
            c.drawLine(ax + aw - u * 1.1f, y - apt.textSize * 0.32f - u * 0.7f,
                ax + aw, y - apt.textSize * 0.32f, arrowPaint)
            c.drawLine(ax + aw - u * 1.1f, y - apt.textSize * 0.32f + u * 0.7f,
                ax + aw, y - apt.textSize * 0.32f, arrowPaint)
            c.drawText(f.to, ax + aw + u * 2.2f, y, apt)
        } else {
            apt.color = DIM
            c.drawText("rute ukendt", left, y, apt)
        }

        // type og registrering
        y += u * 3.4f
        val meta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = mono
            color = MUTED
            textSize = u * 2.1f
            letterSpacing = 0.10f
        }
        val spec = AircraftDraw.specFor(f.type)
        val typeLine = buildString {
            append(spec?.name ?: f.type ?: "ukendt type")
            f.registration?.let { append("  ·  ").append(it) }
        }
        c.drawText(ellipsize(typeLine, meta, w), left, y, meta)

        // nøgletal
        y += u * 4.2f
        val rule = Paint().apply { color = DIM; strokeWidth = max(1f, u * 0.12f) }
        c.drawLine(left, y - u * 2.4f, left + w, y - u * 2.4f, rule)

        val cols = if (wide) 4 else 3
        val colW = w / cols
        val key = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = mono
            color = MUTED
            textSize = u * 1.7f
            letterSpacing = 0.16f
        }
        val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = condensedMedium
            color = Color.WHITE
            textSize = u * 4.6f
        }

        data class Cell(val k: String, val v: String)
        val cells = mutableListOf(
            Cell("HØJDE", "${thousands(f.altitudeM)} m"),
            Cell("FART", f.speedKmh?.let { "$it km/t" } ?: "–"),
            Cell("AFSTAND", "${"%.1f".format(java.util.Locale("da", "DK"), f.distanceKm)} km")
        )
        if (wide) cells.add(Cell("KIG", "${f.elevation}° ${f.compass}"))

        cells.forEachIndexed { i, cell ->
            val x = left + colW * i
            c.drawText(cell.k, x, y, key)
            c.drawText(cell.v, x, y + u * 4.2f, value)
        }
    }

    private fun drawFooter(
        c: Canvas, f: Flight, w: Int, top: Float, pad: Float, u: Float, agoMinutes: Long
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = mono
            color = DIM
            textSize = u * 1.9f
            letterSpacing = 0.10f
        }
        val text = when {
            agoMinutes <= 0L -> "netop nu"
            agoMinutes == 1L -> "for 1 minut siden"
            agoMinutes < 60L -> "for $agoMinutes minutter siden"
            agoMinutes < 120L -> "for 1 time siden"
            agoMinutes < 1440L -> "for ${agoMinutes / 60} timer siden"
            else -> "for over et døgn siden"
        }
        c.drawText(text, pad, top + u * 2.2f, p)

        p.textAlign = Paint.Align.RIGHT
        c.drawText("tryk for live", w - pad, top + u * 2.2f, p)
    }

    private fun thousands(v: Int): String =
        String.format(java.util.Locale("da", "DK"), "%,d", v)

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var s = text
        while (s.length > 4 && paint.measureText("$s…") > maxWidth) {
            s = s.substring(0, s.length - 1)
        }
        return "$s…"
    }
}

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
 *
 * Layoutet på en bred widget: identitet til venstre, foto til højre, og
 * nøgletallene i en fuld bredde nederst. Kig-retningen står blandt dem, fordi
 * det er et tal på linje med de andre — ikke en overskrift.
 */
object WidgetRenderer {

    private const val BG = 0xFF000000.toInt()
    private const val LINE = 0xFFE6E2D9.toInt()
    private const val AMBER = 0xFFF2A63B.toInt()
    private const val MUTED = 0xFF6E6A62.toInt()
    private const val DIM = 0xFF403D38.toInt()

    private val condensed: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val condensedMedium: Typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    private val mono: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)

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

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BG }
        val radius = min(w, h) * 0.055f
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, bgPaint)

        // Al typografi skaleres efter panelets størrelse, som i webudgaven
        val u = min(w / 100f, h / 62f)
        val pad = u * 4.5f

        drawHeader(c, w, u, pad, place, offline)

        if (flight == null) {
            drawEmpty(c, h, u, pad, offline)
            return bmp
        }

        val headerBottom = pad + u * 5.0f
        val footerTop = h - pad - u * 3.0f
        val metricsHeight = u * 9.2f
        val metricsTop = footerTop - metricsHeight
        val bandTop = headerBottom
        val bandBottom = metricsTop - u * 1.4f

        val wide = w.toFloat() / h > 1.6f

        if (wide) {
            // Foto til højre, identitet til venstre
            val gap = u * 3f
            val rightWidth = (w - pad * 2f) * 0.40f
            val leftWidth = (w - pad * 2f) - rightWidth - gap
            drawIdentity(c, flight, pad, bandTop, leftWidth, bandBottom - bandTop, u)
            drawVisual(
                c, flight, photo,
                pad + leftWidth + gap, bandTop, rightWidth, bandBottom - bandTop, u
            )
        } else {
            // Smal widget: visuel del øverst, identitet under
            val visualHeight = (bandBottom - bandTop) * 0.52f
            drawVisual(c, flight, photo, pad, bandTop, w - pad * 2f, visualHeight, u)
            drawIdentity(
                c, flight, pad, bandTop + visualHeight + u * 1.5f,
                w - pad * 2f, bandBottom - bandTop - visualHeight - u * 1.5f, u
            )
        }

        drawMetrics(c, flight, pad, metricsTop, w - pad * 2f, u)
        drawFooter(c, w, footerTop, pad, u, agoMinutes)

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

    private fun drawEmpty(c: Canvas, h: Int, u: Float, pad: Float, offline: Boolean) {
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
            pad, cy + u * 4.2f, sub
        )
    }

    /** Callsign, rute og type. */
    private fun drawIdentity(
        c: Canvas, f: Flight,
        left: Float, top: Float, w: Float, h: Float, u: Float
    ) {
        var y = top

        val cs = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = condensed
            color = Color.WHITE
            textSize = u * 10f
        }
        while (cs.measureText(f.callsign) > w && cs.textSize > u * 4.5f) {
            cs.textSize -= u * 0.25f
        }
        y += cs.textSize * 0.80f
        c.drawText(f.callsign, left, y, cs)

        y += u * 6.2f
        val apt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = condensed
            color = AMBER
            textSize = u * 6.2f
        }
        val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DIM
            strokeWidth = max(1f, u * 0.20f)
        }
        if (f.from != null && f.to != null) {
            c.drawText(f.from, left, y, apt)
            val ax = left + apt.measureText(f.from) + u * 2.0f
            val aw = u * 4.6f
            val ay = y - apt.textSize * 0.33f
            c.drawLine(ax, ay, ax + aw, ay, arrow)
            c.drawLine(ax + aw - u * 1.1f, ay - u * 0.7f, ax + aw, ay, arrow)
            c.drawLine(ax + aw - u * 1.1f, ay + u * 0.7f, ax + aw, ay, arrow)
            c.drawText(f.to, ax + aw + u * 2.0f, y, apt)
        } else {
            apt.color = DIM
            c.drawText("rute ukendt", left, y, apt)
        }

        y += u * 3.2f
        val meta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = mono
            color = MUTED
            textSize = u * 2.1f
            letterSpacing = 0.08f
        }
        val spec = AircraftDraw.specFor(f.type)
        val typeLine = buildString {
            append(spec?.name ?: f.type ?: "ukendt type")
            f.registration?.let { append("  ·  ").append(it) }
        }
        c.drawText(ellipsize(typeLine, meta, w), left, y, meta)
    }

    /** Foto hvis vi har et, ellers stregtegningen af typen. */
    private fun drawVisual(
        c: Canvas, f: Flight, photo: PhotoResult?,
        left: Float, top: Float, w: Float, h: Float, u: Float
    ) {
        if (w < u * 6f || h < u * 6f) return

        if (photo != null) {
            val bmp = photo.bitmap
            val scale = min(w / bmp.width, h / bmp.height)
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            val dstLeft = left + (w - dw) / 2f
            val dstTop = top + (h - dh) / 2f
            val dst = RectF(dstLeft, dstTop, dstLeft + dw, dstTop + dh)
            c.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), dst, Paint(Paint.FILTER_BITMAP_FLAG))

            // Fotografen skal krediteres, men må ikke stjæle billedet
            val credit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = mono
                textSize = u * 1.5f
                color = MUTED
            }
            val text = ellipsize(photo.credit, credit, dw - u * 1.4f)
            val tw = credit.measureText(text)
            c.drawRect(
                dst.right - tw - u * 1.2f, dst.bottom - u * 2.5f,
                dst.right, dst.bottom,
                Paint().apply { color = 0x99000000.toInt() }
            )
            c.drawText(text, dst.right - tw - u * 0.6f, dst.bottom - u * 0.8f, credit)
            return
        }

        val spec = AircraftDraw.specFor(f.type) ?: return
        AircraftDraw.draw(
            canvas = c, sp = spec,
            left = left, top = top, w = w, h = h,
            stroke = LINE, accent = AMBER, faint = MUTED, background = BG,
            strokeWidth = max(1.1f, u * 0.28f)
        )
    }

    /** Nøgletal i fuld bredde, med kig-retningen som fjerde kolonne. */
    private fun drawMetrics(
        c: Canvas, f: Flight, left: Float, top: Float, w: Float, u: Float
    ) {
        val rule = Paint().apply { color = DIM; strokeWidth = max(1f, u * 0.12f) }
        c.drawLine(left, top, left + w, top, rule)

        val key = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = mono
            color = MUTED
            textSize = u * 1.7f
            letterSpacing = 0.16f
        }
        val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = condensedMedium
            color = Color.WHITE
            textSize = u * 4.8f
        }
        val hot = Paint(value).apply { color = AMBER }

        val cells = listOf(
            "HØJDE" to "${thousands(f.altitudeM)} m",
            "FART" to (f.speedKmh?.let { "$it km/t" } ?: "–"),
            "AFSTAND" to "${"%.1f".format(java.util.Locale("da", "DK"), f.distanceKm)} km",
            "KIG" to "${f.elevation}° ${f.compass}"
        )

        val colW = w / cells.size
        cells.forEachIndexed { i, (k, v) ->
            val x = left + colW * i
            c.drawText(k, x, top + u * 2.6f, key)
            val paint = if (i == cells.lastIndex) hot else value
            // krymp hvis værdien er bredere end sin kolonne
            val p = Paint(paint)
            while (p.measureText(v) > colW - u * 1.2f && p.textSize > u * 2.6f) {
                p.textSize -= u * 0.2f
            }
            c.drawText(v, x, top + u * 7.6f, p)
        }
    }

    private fun drawFooter(
        c: Canvas, w: Int, top: Float, pad: Float, u: Float, agoMinutes: Long
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

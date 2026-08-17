package dk.flightster.app

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * Stregtegning af fly i to visninger: set ovenfra og i profil.
 *
 * Intet billedbibliotek. Formen udledes af flyets faktiske mål — længde,
 * spændvidde, vingepilning, antal motorer og hvor de sidder — så en 747
 * får fire motorer og overdæk, og en Cessna får propel og lige vinger,
 * uden at nogen har tegnet dem i hånden.
 *
 * Det er en direkte oversættelse af tegnemotoren i webudgaven.
 */
object AircraftDraw {

    // mount
    const val WING = 0
    const val REAR = 1
    const val NOSE = 2

    // tips
    const val TIP_NONE = 0
    const val TIP_WINGLET = 1
    const val TIP_SHARKLET = 2
    const val TIP_FENCE = 3
    const val TIP_RAKED = 4

    data class Spec(
        val name: String,
        val length: Float,      // meter
        val span: Float,        // meter
        val sweep: Float,       // graders vingepilning
        val engines: Int,
        val mount: Int,
        val tTail: Boolean,
        val tips: Int = TIP_NONE,
        val deck: Boolean = false,   // ekstra dæk (747, A380)
        val highWing: Boolean = false
    )

    private fun ac(
        name: String, l: Float, s: Float, sweep: Float, eng: Int,
        mount: Int, tTail: Boolean, tips: Int = TIP_NONE, deck: Boolean = false
    ) = Spec(name, l, s, sweep, eng, mount, tTail, tips, deck)

    private val HIGH_WING = setOf(
        "C152", "C162", "C172", "C77R", "C182", "C206", "C210", "C208",
        "AT45", "AT72", "AT76", "DH8D", "C130", "A400", "B463", "SF34"
    )

    val TYPES: Map<String, Spec> = buildMap {
        // Airbus smalkrop
        put("A318", ac("Airbus A318", 31.4f, 34.1f, 25f, 2, WING, false, TIP_SHARKLET))
        put("A319", ac("Airbus A319", 33.8f, 35.8f, 25f, 2, WING, false, TIP_SHARKLET))
        put("A320", ac("Airbus A320", 37.6f, 35.8f, 25f, 2, WING, false, TIP_FENCE))
        put("A321", ac("Airbus A321", 44.5f, 35.8f, 25f, 2, WING, false, TIP_SHARKLET))
        put("A19N", ac("Airbus A319neo", 33.8f, 35.8f, 25f, 2, WING, false, TIP_SHARKLET))
        put("A20N", ac("Airbus A320neo", 37.6f, 35.8f, 25f, 2, WING, false, TIP_SHARKLET))
        put("A21N", ac("Airbus A321neo", 44.5f, 35.8f, 25f, 2, WING, false, TIP_SHARKLET))
        put("BCS1", ac("Airbus A220-100", 35.0f, 35.1f, 25f, 2, WING, false, TIP_RAKED))
        put("BCS3", ac("Airbus A220-300", 38.7f, 35.1f, 25f, 2, WING, false, TIP_RAKED))
        // Airbus bredkrop
        put("A332", ac("Airbus A330-200", 58.8f, 60.3f, 30f, 2, WING, false, TIP_WINGLET))
        put("A333", ac("Airbus A330-300", 63.7f, 60.3f, 30f, 2, WING, false, TIP_WINGLET))
        put("A339", ac("Airbus A330-900neo", 63.7f, 64.0f, 30f, 2, WING, false, TIP_RAKED))
        put("A343", ac("Airbus A340-300", 63.7f, 60.3f, 30f, 4, WING, false, TIP_WINGLET))
        put("A346", ac("Airbus A340-600", 75.3f, 63.5f, 30f, 4, WING, false, TIP_WINGLET))
        put("A359", ac("Airbus A350-900", 66.8f, 64.8f, 31f, 2, WING, false, TIP_RAKED))
        put("A35K", ac("Airbus A350-1000", 73.8f, 64.8f, 31f, 2, WING, false, TIP_RAKED))
        put("A388", ac("Airbus A380-800", 72.7f, 79.8f, 33f, 4, WING, false, TIP_FENCE, true))
        // Boeing smalkrop
        put("B733", ac("Boeing 737-300", 33.4f, 28.9f, 25f, 2, WING, false))
        put("B735", ac("Boeing 737-500", 31.0f, 28.9f, 25f, 2, WING, false))
        put("B736", ac("Boeing 737-600", 31.2f, 34.3f, 25f, 2, WING, false, TIP_WINGLET))
        put("B737", ac("Boeing 737-700", 33.6f, 34.3f, 25f, 2, WING, false, TIP_WINGLET))
        put("B738", ac("Boeing 737-800", 39.5f, 34.3f, 25f, 2, WING, false, TIP_WINGLET))
        put("B739", ac("Boeing 737-900", 42.1f, 34.3f, 25f, 2, WING, false, TIP_WINGLET))
        put("B38M", ac("Boeing 737 MAX 8", 39.5f, 35.9f, 25f, 2, WING, false, TIP_RAKED))
        put("B39M", ac("Boeing 737 MAX 9", 42.2f, 35.9f, 25f, 2, WING, false, TIP_RAKED))
        put("B3XM", ac("Boeing 737 MAX 10", 43.8f, 35.9f, 25f, 2, WING, false, TIP_RAKED))
        put("B752", ac("Boeing 757-200", 47.3f, 38.0f, 25f, 2, WING, false, TIP_WINGLET))
        put("B753", ac("Boeing 757-300", 54.4f, 38.0f, 25f, 2, WING, false, TIP_WINGLET))
        // Boeing bredkrop
        put("B762", ac("Boeing 767-200", 48.5f, 47.6f, 31f, 2, WING, false, TIP_WINGLET))
        put("B763", ac("Boeing 767-300", 54.9f, 47.6f, 31f, 2, WING, false, TIP_WINGLET))
        put("B764", ac("Boeing 767-400", 61.4f, 51.9f, 31f, 2, WING, false, TIP_RAKED))
        put("B772", ac("Boeing 777-200", 63.7f, 60.9f, 31f, 2, WING, false, TIP_RAKED))
        put("B77L", ac("Boeing 777-200LR", 63.7f, 64.8f, 31f, 2, WING, false, TIP_RAKED))
        put("B77W", ac("Boeing 777-300ER", 73.9f, 64.8f, 31f, 2, WING, false, TIP_RAKED))
        put("B778", ac("Boeing 777-8", 69.8f, 71.8f, 32f, 2, WING, false, TIP_RAKED))
        put("B779", ac("Boeing 777-9", 76.7f, 71.8f, 32f, 2, WING, false, TIP_RAKED))
        put("B788", ac("Boeing 787-8", 56.7f, 60.1f, 32f, 2, WING, false, TIP_RAKED))
        put("B789", ac("Boeing 787-9", 62.8f, 60.1f, 32f, 2, WING, false, TIP_RAKED))
        put("B78X", ac("Boeing 787-10", 68.3f, 60.1f, 32f, 2, WING, false, TIP_RAKED))
        put("B741", ac("Boeing 747-100", 70.6f, 59.6f, 37f, 4, WING, false, TIP_NONE, true))
        put("B742", ac("Boeing 747-200", 70.6f, 59.6f, 37f, 4, WING, false, TIP_NONE, true))
        put("B744", ac("Boeing 747-400", 70.6f, 64.4f, 37f, 4, WING, false, TIP_WINGLET, true))
        put("B748", ac("Boeing 747-8", 76.3f, 68.4f, 37f, 4, WING, false, TIP_RAKED, true))
        put("B74F", ac("Boeing 747 Freighter", 70.6f, 64.4f, 37f, 4, WING, false, TIP_WINGLET, true))
        // Cessna
        put("C152", ac("Cessna 152", 7.3f, 10.2f, 0f, 1, NOSE, false))
        put("C162", ac("Cessna 162 Skycatcher", 6.9f, 9.1f, 0f, 1, NOSE, false))
        put("C172", ac("Cessna 172 Skyhawk", 8.3f, 11.0f, 0f, 1, NOSE, false))
        put("C77R", ac("Cessna 177 Cardinal", 8.3f, 10.8f, 0f, 1, NOSE, false))
        put("C182", ac("Cessna 182 Skylane", 8.8f, 11.0f, 0f, 1, NOSE, false))
        put("C206", ac("Cessna 206 Stationair", 8.6f, 11.0f, 0f, 1, NOSE, false))
        put("C210", ac("Cessna 210 Centurion", 8.6f, 11.2f, 0f, 1, NOSE, false))
        put("C208", ac("Cessna 208 Caravan", 11.5f, 15.9f, 0f, 1, NOSE, false))
        put("C25A", ac("Cessna Citation CJ2", 14.5f, 15.2f, 10f, 2, REAR, true))
        put("C25B", ac("Cessna Citation CJ3", 15.6f, 16.3f, 10f, 2, REAR, true))
        put("C25C", ac("Cessna Citation CJ4", 16.3f, 15.5f, 15f, 2, REAR, true))
        put("C56X", ac("Cessna Citation Excel", 16.0f, 17.2f, 10f, 2, REAR, true))
        put("C68A", ac("Cessna Citation Latitude", 18.9f, 22.0f, 12f, 2, REAR, true, TIP_WINGLET))
        put("C700", ac("Cessna Citation Longitude", 22.3f, 20.9f, 20f, 2, REAR, true, TIP_WINGLET))
        // Regional og naboer i dansk luftrum
        put("E170", ac("Embraer 170", 29.9f, 26.0f, 25f, 2, WING, false, TIP_WINGLET))
        put("E75L", ac("Embraer 175", 31.7f, 28.7f, 25f, 2, WING, false, TIP_WINGLET))
        put("E190", ac("Embraer 190", 36.2f, 28.7f, 25f, 2, WING, false, TIP_WINGLET))
        put("E195", ac("Embraer 195", 38.7f, 28.7f, 25f, 2, WING, false, TIP_WINGLET))
        put("E290", ac("Embraer E190-E2", 36.2f, 33.7f, 25f, 2, WING, false, TIP_RAKED))
        put("E295", ac("Embraer E195-E2", 41.5f, 35.1f, 25f, 2, WING, false, TIP_RAKED))
        put("CRJ2", ac("Bombardier CRJ200", 26.8f, 21.2f, 25f, 2, REAR, true, TIP_WINGLET))
        put("CRJ7", ac("Bombardier CRJ700", 32.3f, 23.2f, 25f, 2, REAR, true, TIP_WINGLET))
        put("CRJ9", ac("Bombardier CRJ900", 36.4f, 24.9f, 25f, 2, REAR, true, TIP_WINGLET))
        put("CRJX", ac("Bombardier CRJ1000", 39.1f, 26.2f, 25f, 2, REAR, true, TIP_WINGLET))
        put("DH8D", ac("De Havilland Dash 8 Q400", 32.8f, 28.4f, 0f, 2, WING, true))
        put("AT45", ac("ATR 42-500", 22.7f, 24.6f, 0f, 2, WING, true))
        put("AT72", ac("ATR 72", 27.2f, 27.1f, 0f, 2, WING, true))
        put("AT76", ac("ATR 72-600", 27.2f, 27.1f, 0f, 2, WING, true))
        put("SF34", ac("Saab 340", 19.7f, 21.4f, 0f, 2, WING, false))
        put("PC12", ac("Pilatus PC-12", 14.4f, 16.3f, 0f, 1, NOSE, true, TIP_WINGLET))
        put("TBM9", ac("Daher TBM 900", 10.7f, 12.8f, 0f, 1, NOSE, false, TIP_WINGLET))
        put("SR22", ac("Cirrus SR22", 7.9f, 11.7f, 0f, 1, NOSE, false))
        put("DA40", ac("Diamond DA40", 8.1f, 11.9f, 0f, 1, NOSE, true))
        put("DA42", ac("Diamond DA42", 8.6f, 13.4f, 0f, 2, WING, true))
        put("P28A", ac("Piper PA-28 Cherokee", 7.3f, 10.7f, 0f, 1, NOSE, false))
        put("BE20", ac("Beechcraft King Air 200", 13.3f, 16.6f, 0f, 2, WING, true))
        put("GLF5", ac("Gulfstream G550", 29.4f, 28.5f, 27f, 2, REAR, true, TIP_WINGLET))
        put("GLEX", ac("Bombardier Global Express", 30.3f, 28.7f, 27f, 2, REAR, true, TIP_WINGLET))
        put("FA7X", ac("Dassault Falcon 7X", 23.4f, 26.2f, 25f, 3, REAR, true, TIP_WINGLET))
        put("A400", ac("Airbus A400M Atlas", 45.1f, 42.4f, 0f, 4, WING, true))
        put("C130", ac("Lockheed C-130 Hercules", 29.8f, 40.4f, 0f, 4, WING, false))
        put("B463", ac("BAe 146-300", 30.9f, 26.2f, 15f, 4, WING, true))
    }.mapValues { (code, s) ->
        if (code in HIGH_WING) s.copy(highWing = true) else s
    }

    /** Ukendte varianter falder tilbage på nærmeste familie. */
    fun specFor(code: String?): Spec? {
        if (code.isNullOrBlank()) return null
        val c = code.uppercase().filter { it.isLetterOrDigit() }
        TYPES[c]?.let { return it }
        return when {
            c.startsWith("A31") || c.startsWith("A32") -> TYPES["A320"]
            c.startsWith("A33") -> TYPES["A333"]
            c.startsWith("A34") -> TYPES["A343"]
            c.startsWith("A35") -> TYPES["A359"]
            c.startsWith("A38") -> TYPES["A388"]
            c.startsWith("B74") -> TYPES["B744"]
            c.startsWith("B73") -> TYPES["B738"]
            c.startsWith("B75") -> TYPES["B752"]
            c.startsWith("B76") -> TYPES["B763"]
            c.startsWith("B77") -> TYPES["B772"]
            c.startsWith("B78") -> TYPES["B789"]
            c.startsWith("C20") -> TYPES["C208"]
            Regex("^C1[5-9]|^C2[01]").containsMatchIn(c) -> TYPES["C172"]
            c.startsWith("C2") || c.startsWith("C5") ||
                c.startsWith("C6") || c.startsWith("C7") -> TYPES["C56X"]
            Regex("^E1[79]|^E29|^E75").containsMatchIn(c) -> TYPES["E190"]
            c.startsWith("CRJ") -> TYPES["CRJ9"]
            c.startsWith("AT4") || c.startsWith("AT7") -> TYPES["AT72"]
            c.startsWith("DH8") -> TYPES["DH8D"]
            c.startsWith("BCS") -> TYPES["BCS3"]
            c.startsWith("GL") || c.startsWith("FA") -> TYPES["GLF5"]
            else -> null
        }
    }

    // ---------------------------------------------------------------- geometri

    private class Geom(sp: Spec) {
        val fw: Float = max(0.8f, sp.span * 0.100f) * (if (sp.deck) 1.08f else 1f)
        val prop: Boolean = sp.mount == NOSE || (sp.sweep == 0f && sp.mount == WING)
        val wx: Float = if (sp.mount == NOSE) sp.length * 0.26f else sp.length * 0.36f
        val rootC: Float = if (sp.sweep > 0f) sp.span * 0.19f else sp.span * 0.125f
        val tipC: Float = rootC * (if (sp.sweep > 0f) 0.36f else 0.58f)
        val tanSweep: Float = tan((if (sp.sweep > 0f) sp.sweep else 4f) * PI.toFloat() / 180f)
        val semi: Float = sp.span / 2f
        val finH: Float =
            if (prop) sp.length * (if (sp.tTail) 0.22f else 0.17f)
            else sp.length * (if (sp.tTail) 0.24f else 0.20f)
        val nw: Float = if (prop) fw * 0.42f else fw * 0.62f
        val nl: Float = if (prop) sp.length * 0.17f else nw * 1.85f
    }

    /** Afsætter en tegning i modelmeter til pixels på lærredet. */
    private class Frame(val ox: Float, val oy: Float, val scale: Float) {
        fun x(v: Float) = ox + v * scale
        fun y(v: Float) = oy + v * scale
    }

    // ------------------------------------------------------------- grundformer

    /** Bæreplan set fra kanten: rund forkant, tykkest ca. 30 % inde, skarp bagkant. */
    private fun airfoil(
        p: Path, f: Frame, x0: Float, y0: Float, c: Float, t: Float, camber: Float
    ) {
        val tU = t * (0.5f + camber)
        val tL = t * (0.5f - camber)
        val yTE = y0 - if (camber > 0f) c * 0.030f else 0f
        p.moveTo(f.x(x0), f.y(y0))
        p.cubicTo(
            f.x(x0 + c * 0.05f), f.y(y0 - tU * 0.86f),
            f.x(x0 + c * 0.16f), f.y(y0 - tU),
            f.x(x0 + c * 0.30f), f.y(y0 - tU)
        )
        p.cubicTo(
            f.x(x0 + c * 0.54f), f.y(y0 - tU * 0.90f),
            f.x(x0 + c * 0.80f), f.y(y0 - tU * 0.48f),
            f.x(x0 + c), f.y(yTE)
        )
        p.cubicTo(
            f.x(x0 + c * 0.74f), f.y(y0 + tL * 0.32f),
            f.x(x0 + c * 0.44f), f.y(y0 + tL * 0.86f),
            f.x(x0 + c * 0.24f), f.y(y0 + tL)
        )
        p.cubicTo(
            f.x(x0 + c * 0.11f), f.y(y0 + tL * 0.92f),
            f.x(x0 + c * 0.03f), f.y(y0 + tL * 0.55f),
            f.x(x0), f.y(y0)
        )
        p.close()
    }

    /** Motorgondol: buttet indløb, bredest lige bag læben, smallere mod udstødningen. */
    private fun nacelle(p: Path, f: Frame, x0: Float, c: Float, nl: Float, nw: Float) {
        p.moveTo(f.x(x0 + nl * 0.17f), f.y(c - nw * 0.50f))
        p.cubicTo(
            f.x(x0 - nl * 0.03f), f.y(c - nw * 0.43f),
            f.x(x0 - nl * 0.03f), f.y(c + nw * 0.43f),
            f.x(x0 + nl * 0.17f), f.y(c + nw * 0.50f)
        )
        p.cubicTo(
            f.x(x0 + nl * 0.46f), f.y(c + nw * 0.50f),
            f.x(x0 + nl * 0.76f), f.y(c + nw * 0.40f),
            f.x(x0 + nl), f.y(c + nw * 0.25f)
        )
        p.lineTo(f.x(x0 + nl), f.y(c - nw * 0.25f))
        p.cubicTo(
            f.x(x0 + nl * 0.76f), f.y(c - nw * 0.40f),
            f.x(x0 + nl * 0.46f), f.y(c - nw * 0.50f),
            f.x(x0 + nl * 0.17f), f.y(c - nw * 0.50f)
        )
        p.close()
    }

    // ------------------------------------------------------------------ visning

    /**
     * Tegner flyet i to visninger inden for [w] x [h] pixels med øverste
     * venstre hjørne i (left, top). Delene tegnes bagfra og frem og fyldes
     * med baggrundsfarven, så motorer gemmer sig bag vingen som de skal.
     */
    fun draw(
        canvas: Canvas, sp: Spec,
        left: Float, top: Float, w: Float, h: Float,
        stroke: Int, accent: Int, faint: Int, background: Int,
        strokeWidth: Float
    ) {
        val g = Geom(sp)
        val gap = sp.length * 0.10f
        val sideH = g.fw + g.finH + g.fw * 0.6f
        val modelW = max(sp.length, sp.span) * 1.06f
        val modelH = sp.span + gap + sideH
        val scale = min(w / modelW, h / modelH)

        val drawW = modelW * scale
        val drawH = modelH * scale
        val originX = left + (w - drawW) / 2f + (modelW - sp.length) / 2f * scale
        val originY = top + (h - drawH) / 2f

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = background
        }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = stroke
            this.strokeWidth = strokeWidth
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        val thin = Paint(line).apply {
            color = faint
            this.strokeWidth = strokeWidth * 0.62f
        }
        val hot = Paint(line).apply { color = accent }

        val topFrame = Frame(originX, originY + sp.span / 2f * scale, scale)
        val sideFrame = Frame(
            originX,
            originY + (sp.span + gap + g.finH + g.fw * 0.5f) * scale,
            scale
        )

        drawTop(canvas, sp, g, topFrame, fill, line, thin, hot)
        drawSide(canvas, sp, g, sideFrame, fill, line, thin, hot)
    }

    private fun stamp(canvas: Canvas, p: Path, fill: Paint, line: Paint) {
        canvas.drawPath(p, fill)
        canvas.drawPath(p, line)
    }

    private fun drawTop(
        c: Canvas, sp: Spec, g: Geom, f: Frame,
        fill: Paint, line: Paint, thin: Paint, hot: Paint
    ) {
        val l = sp.length
        val fw = g.fw

        // 1. motorer under vingen — tegnes først, så vingen dækker bagenden
        if (sp.mount == WING && sp.engines >= 2) {
            val positions = if (sp.engines >= 4) floatArrayOf(0.34f, 0.63f) else floatArrayOf(0.36f)
            for (pos in positions) {
                for (s in intArrayOf(-1, 1)) {
                    val yE = s * g.semi * pos
                    val xLE = g.wx + (abs(yE) - fw * 0.5f) * g.tanSweep
                    val x0 = xLE - g.nl * (if (g.prop) 0.50f else 0.62f)
                    val p = Path()
                    nacelle(p, f, x0, yE, g.nl, g.nw)
                    stamp(c, p, fill, line)
                    if (g.prop) {
                        val pd = sp.span * 0.15f
                        c.drawLine(
                            f.x(x0 - l * 0.004f), f.y(yE - pd / 2f),
                            f.x(x0 - l * 0.004f), f.y(yE + pd / 2f), hot
                        )
                    }
                }
            }
        }

        // 2. vingen, med knæk i bagkanten som rigtige trafikfly har
        val wingPaths = ArrayList<Path>(2)
        val raked = if (sp.tips == TIP_RAKED) g.semi * 0.035f else 0f
        val kinkY = g.semi * 0.35f
        for (s in intArrayOf(-1, 1)) {
            val yT = s * g.semi
            val yR = s * fw * 0.46f
            val yK = s * kinkY
            val leT = g.wx + (g.semi - fw * 0.5f) * g.tanSweep + raked
            val leK = g.wx + (kinkY - fw * 0.5f) * g.tanSweep
            val teR = g.wx + g.rootC
            val teK = leK + g.rootC * (if (sp.sweep > 0f) 0.62f else 0.86f)
            val p = Path()
            p.moveTo(f.x(g.wx), f.y(yR))
            p.lineTo(f.x(leT), f.y(yT))
            p.lineTo(f.x(leT + g.tipC * 0.55f), f.y(yT))
            p.lineTo(f.x(leT + g.tipC), f.y(yT - s * g.semi * 0.012f))
            p.lineTo(f.x(teK), f.y(yK))
            p.lineTo(f.x(teR), f.y(yR))
            p.close()
            wingPaths.add(p)
            if (sp.tips == TIP_WINGLET || sp.tips == TIP_SHARKLET || sp.tips == TIP_FENCE) {
                val t = if (sp.tips == TIP_FENCE) g.semi * 0.028f else g.semi * 0.045f
                c.drawLine(
                    f.x(leT), f.y(yT),
                    f.x(leT + g.tipC * 0.40f), f.y(yT + s * t), thin
                )
            }
        }

        val body = Path().apply {
            moveTo(f.x(0f), f.y(0f))
            cubicTo(
                f.x(l * 0.035f), f.y(-fw * 0.40f),
                f.x(l * 0.09f), f.y(-fw * 0.5f),
                f.x(l * 0.16f), f.y(-fw * 0.5f)
            )
            lineTo(f.x(l * 0.73f), f.y(-fw * 0.5f))
            cubicTo(
                f.x(l * 0.85f), f.y(-fw * 0.47f),
                f.x(l * 0.94f), f.y(-fw * 0.30f),
                f.x(l), f.y(-fw * 0.06f)
            )
            lineTo(f.x(l), f.y(fw * 0.06f))
            cubicTo(
                f.x(l * 0.94f), f.y(fw * 0.30f),
                f.x(l * 0.85f), f.y(fw * 0.47f),
                f.x(l * 0.73f), f.y(fw * 0.5f)
            )
            lineTo(f.x(l * 0.16f), f.y(fw * 0.5f))
            cubicTo(
                f.x(l * 0.09f), f.y(fw * 0.5f),
                f.x(l * 0.035f), f.y(fw * 0.40f),
                f.x(0f), f.y(0f)
            )
            close()
        }

        // højtvingede fly har vingen ovenpå kroppen
        if (sp.highWing) {
            stamp(c, body, fill, line)
            wingPaths.forEach { stamp(c, it, fill, line) }
        } else {
            wingPaths.forEach { stamp(c, it, fill, line) }
            stamp(c, body, fill, line)
        }

        // cockpitruder som paneler
        for (s in intArrayOf(-1, 1)) {
            val p = Path()
            p.moveTo(f.x(l * 0.066f), f.y(s * fw * 0.055f))
            p.lineTo(f.x(l * 0.090f), f.y(s * fw * 0.265f))
            p.lineTo(f.x(l * 0.158f), f.y(s * fw * 0.375f))
            p.lineTo(f.x(l * 0.162f), f.y(s * fw * 0.125f))
            p.close()
            c.drawPath(p, thin)
        }
        if (sp.deck) {
            c.drawLine(f.x(l * 0.11f), f.y(-fw * 0.30f), f.x(l * 0.44f), f.y(-fw * 0.30f), thin)
            c.drawLine(f.x(l * 0.11f), f.y(fw * 0.30f), f.x(l * 0.44f), f.y(fw * 0.30f), thin)
        }

        // halemotorer sidder på kroppen og ligger derfor øverst
        if (sp.mount == REAR) {
            val nwr = fw * 0.62f
            val nlr = fw * 0.62f * 2.0f
            for (s in intArrayOf(-1, 1)) {
                val yR = s * (fw * 0.46f + nwr * 0.52f)
                val p = Path()
                nacelle(p, f, l * 0.655f, yR, nlr, nwr)
                stamp(c, p, fill, line)
                c.drawLine(
                    f.x(l * 0.655f + nlr * 0.4f), f.y(yR - s * nwr * 0.5f),
                    f.x(l * 0.655f + nlr * 0.7f), f.y(s * fw * 0.44f), thin
                )
            }
        }
        if (sp.mount == NOSE) {
            val pd = sp.span * 0.085f
            c.drawLine(f.x(-l * 0.030f), f.y(-pd), f.x(-l * 0.030f), f.y(pd), hot)
        }

        // haleplan
        val hx = if (sp.tTail) l * 0.94f else l * 0.845f
        val hs = sp.span * 0.17f
        val hRoot = if (g.prop) l * 0.13f else l * 0.11f
        val hTip = hRoot * 0.48f
        val hSw = hs * tan((if (sp.sweep > 0f) sp.sweep + 6f else 10f) * PI.toFloat() / 180f)
        for (s in intArrayOf(-1, 1)) {
            val p = Path()
            p.moveTo(f.x(hx), f.y(s * fw * 0.24f))
            p.lineTo(f.x(hx + hSw), f.y(s * hs))
            p.lineTo(f.x(hx + hSw + hTip), f.y(s * hs))
            p.lineTo(f.x(hx + hRoot), f.y(s * fw * 0.24f))
            p.close()
            stamp(c, p, fill, line)
        }

        // finnen set ovenfra er også et bæreplan
        val fx0 = l * 0.80f
        val fx1 = l * (if (sp.tTail) 1.02f else 1.0f)
        val fin = Path()
        airfoil(fin, f, fx0, 0f, fx1 - fx0, fw * 0.20f, 0f)
        stamp(c, fin, fill, line)
    }

    private fun drawSide(
        c: Canvas, sp: Spec, g: Geom, f: Frame,
        fill: Paint, line: Paint, thin: Paint, hot: Paint
    ) {
        val l = sp.length
        val fw = g.fw

        val body = Path().apply {
            moveTo(f.x(0f), f.y(fw * 0.10f))
            cubicTo(
                f.x(l * 0.02f), f.y(-fw * 0.22f),
                f.x(l * 0.07f), f.y(-fw * 0.5f),
                f.x(l * 0.155f), f.y(-fw * 0.5f)
            )
            lineTo(f.x(l * 0.78f), f.y(-fw * 0.5f))
            cubicTo(
                f.x(l * 0.87f), f.y(-fw * 0.5f),
                f.x(l * 0.95f), f.y(-fw * 0.44f),
                f.x(l), f.y(-fw * 0.30f)
            )
            cubicTo(
                f.x(l * 0.92f), f.y(-fw * 0.02f),
                f.x(l * 0.85f), f.y(fw * 0.30f),
                f.x(l * 0.72f), f.y(fw * 0.5f)
            )
            lineTo(f.x(l * 0.15f), f.y(fw * 0.5f))
            cubicTo(
                f.x(l * 0.06f), f.y(fw * 0.5f),
                f.x(l * 0.015f), f.y(fw * 0.36f),
                f.x(0f), f.y(fw * 0.10f)
            )
            close()
        }
        stamp(c, body, fill, line)

        // cockpitrude med tre ruder
        val glass = Path().apply {
            moveTo(f.x(l * 0.062f), f.y(-fw * 0.175f))
            lineTo(f.x(l * 0.086f), f.y(-fw * 0.360f))
            lineTo(f.x(l * 0.158f), f.y(-fw * 0.400f))
            lineTo(f.x(l * 0.162f), f.y(-fw * 0.235f))
            close()
        }
        c.drawPath(glass, thin)
        c.drawLine(f.x(l * 0.104f), f.y(-fw * 0.370f), f.x(l * 0.109f), f.y(-fw * 0.205f), thin)
        c.drawLine(f.x(l * 0.131f), f.y(-fw * 0.385f), f.x(l * 0.135f), f.y(-fw * 0.220f), thin)

        // kabinevinduer
        if (l > 18f) {
            val step = l * 0.028f
            val r = fw * 0.035f * f.scale
            var x = l * 0.16f
            while (x < l * 0.76f) {
                c.drawCircle(f.x(x), f.y(-fw * 0.10f), r, thin)
                x += step
            }
            if (sp.deck) {
                x = l * 0.13f
                while (x < l * 0.44f) {
                    c.drawCircle(f.x(x), f.y(-fw * 0.34f), r, thin)
                    x += step
                }
            }
        }

        // finne
        val fin = Path().apply {
            moveTo(f.x(l * 0.745f), f.y(-fw * 0.48f))
            lineTo(f.x(l * 0.925f), f.y(-fw * 0.5f - g.finH))
            lineTo(f.x(l * 1.005f), f.y(-fw * 0.5f - g.finH * 0.95f))
            lineTo(f.x(l * 0.99f), f.y(-fw * 0.34f))
            close()
        }
        stamp(c, fin, fill, line)

        // haleplan
        val stab = Path()
        if (sp.tTail) {
            val ty = -fw * 0.5f - g.finH * 0.98f
            airfoil(stab, f, l * 0.885f, ty, l * 0.145f, l * 0.145f * 0.15f, 0.10f)
        } else {
            airfoil(stab, f, l * 0.845f, -fw * 0.20f, l * 0.155f, l * 0.155f * 0.15f, 0.10f)
        }
        stamp(c, stab, fill, line)

        // vingen som rigtigt profil, ikke en streg
        val wy: Float
        val wing = Path()
        if (sp.highWing) {
            wy = -fw * 0.50f
            airfoil(wing, f, g.wx - g.rootC * 0.05f, wy, g.rootC * 1.30f, g.rootC * 0.24f, 0.15f)
            stamp(c, wing, fill, line)
            c.drawLine(
                f.x(g.wx + g.rootC * 0.55f), f.y(wy),
                f.x(g.wx + g.rootC * 0.95f), f.y(fw * 0.45f), thin
            )
        } else {
            wy = fw * 0.44f
            airfoil(wing, f, g.wx, wy, g.rootC * 1.15f, g.rootC * 0.20f, 0.15f)
            stamp(c, wing, fill, line)
        }

        // motorer
        if (sp.mount == WING && sp.engines >= 2) {
            if (g.prop) {
                val ny = if (sp.highWing) -fw * 0.62f else fw * 0.10f
                val x0 = g.wx - g.nl * 0.45f
                val p = Path()
                nacelle(p, f, x0, ny, g.nl, g.nw * 0.92f)
                stamp(c, p, fill, line)
                val pr = sp.span * 0.075f
                c.drawLine(f.x(x0 - l * 0.008f), f.y(ny - pr), f.x(x0 - l * 0.008f), f.y(ny + pr), hot)
            } else {
                val tL = g.rootC * 0.20f * 0.35f
                val ny = fw * 0.72f
                val x0 = g.wx - g.nl * 0.62f
                // pylonen bærer gondolen op til vingens underside
                val pylon = Path().apply {
                    moveTo(f.x(x0 + g.nl * 0.52f), f.y(ny - g.nw * 0.44f))
                    lineTo(f.x(g.wx + g.rootC * 0.10f), f.y(wy + tL * 0.75f))
                    lineTo(f.x(g.wx + g.rootC * 0.42f), f.y(wy + tL * 0.55f))
                    lineTo(f.x(x0 + g.nl * 0.99f), f.y(ny - g.nw * 0.20f))
                    close()
                }
                stamp(c, pylon, fill, line)
                val p = Path()
                nacelle(p, f, x0, ny, g.nl, g.nw)
                stamp(c, p, fill, line)
            }
        }
        if (sp.mount == REAR) {
            val p = Path()
            nacelle(p, f, l * 0.655f, -fw * 0.40f, fw * 0.62f * 2.0f, fw * 0.62f)
            stamp(c, p, fill, line)
        }
        if (sp.mount == NOSE) {
            val spinner = Path().apply {
                moveTo(f.x(0f), f.y(-fw * 0.13f))
                lineTo(f.x(-l * 0.028f), f.y(0f))
                lineTo(f.x(0f), f.y(fw * 0.13f))
                close()
            }
            stamp(c, spinner, fill, line)
            val pr = sp.span * 0.085f
            c.drawLine(f.x(-l * 0.030f), f.y(-pr), f.x(-l * 0.030f), f.y(pr), hot)
        }
    }
}

package dk.flightster.app

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Widgeten på hjemmeskærmen.
 *
 * Android tillader ikke hyppigere baggrundsopdatering end 15 minutter, og et
 * fly er kun inden for synsvidde i et par minutter. Widgeten er derfor
 * bevidst designet som en logbog frem for et live-display: den viser det
 * seneste fly der var over dig, med et ærligt tidsstempel. Tryk åbner appen,
 * som kører rigtigt live.
 */
class FlightWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "dk.flightster.app.REFRESH"
        private const val WORK_NAME = "flightster-widget"

        fun scheduleUpdates(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun refreshNow(context: Context) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<WidgetWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
            )
        }

        fun cancelUpdates(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /** Tegner panelet og lægger det i widgetens ImageView. */
        fun renderInto(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = Prefs(context)
            val options = manager.getAppWidgetOptions(widgetId)
            val (w, h) = pixelSize(context, options)

            val flight = prefs.loadFlight()
            val ago = if (flight == null) 0L
                      else (System.currentTimeMillis() - flight.seenAtMillis) / 60_000L
            val offline = prefs.lastCheck > 0L &&
                System.currentTimeMillis() - prefs.lastCheck > 90 * 60_000L

            val photo = if (prefs.showPhotos && flight != null) PhotoStore.load(context, flight) else null

            val bitmap = WidgetRenderer.render(
                widthPx = w, heightPx = h,
                flight = flight, photo = photo,
                place = prefs.placeName,
                agoMinutes = ago,
                offline = offline
            )

            val views = RemoteViews(context.packageName, R.layout.widget)
            views.setImageViewBitmap(R.id.widget_image, bitmap)

            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)

            manager.updateAppWidget(widgetId, views)
        }

        /**
         * Widgetens størrelse i pixels. Der lægges lidt oveni for skarphed,
         * men med et loft: RemoteViews sendes over IPC med en grænse omkring
         * 1 MB, og et for stort bitmap får widgeten til at fejle tavst.
         */
        private fun pixelSize(context: Context, options: Bundle): Pair<Int, Int> {
            val dm = context.resources.displayMetrics
            fun dp(v: Int) = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), dm
            ).toInt()

            val wDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val hDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160)

            var w = dp(if (wDp > 0) wDp else 250)
            var h = dp(if (hDp > 0) hDp else 160)

            // ARGB_8888 er 4 bytes per pixel; hold os under ca. 750 kB
            val maxPixels = 240_000
            if (w * h > maxPixels) {
                val k = kotlin.math.sqrt(maxPixels.toDouble() / (w * h))
                w = (w * k).toInt()
                h = (h * k).toInt()
            }
            return w.coerceAtLeast(200) to h.coerceAtLeast(140)
        }
    }

    override fun onUpdate(
        context: Context, manager: AppWidgetManager, appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { renderInto(context, manager, it) }
        scheduleUpdates(context)
        refreshNow(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, widgetId: Int, newOptions: Bundle
    ) {
        renderInto(context, manager, widgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            refreshNow(context)
        }
    }

    override fun onEnabled(context: Context) {
        scheduleUpdates(context)
    }

    override fun onDisabled(context: Context) {
        cancelUpdates(context)
        PhotoStore.clear(context)
    }
}

/**
 * Baggrundsarbejdet: opdater position, hent seneste fly, gem det, gentegn.
 *
 * Positionen hentes som sidst kendte fra systemet i stedet for at bede om en
 * frisk måling. Det koster ingenting i batteri, og når intervallet alligevel
 * er en halv time, er en position fra for lidt siden rigelig præcis.
 */
class WidgetWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = Prefs(context)

        if (prefs.autoLocation) {
            lastKnownLocation()?.let { (lat, lon) ->
                prefs.latitude = lat
                prefs.longitude = lon
            }
        }

        val flight = FlightRepo.fetchNow(
            baseUrl = prefs.baseUrl,
            lat = prefs.latitude,
            lon = prefs.longitude,
            radiusKm = prefs.radiusKm,
            minElev = prefs.minElevation
        )

        prefs.lastCheck = System.currentTimeMillis()

        if (flight != null) {
            prefs.saveFlight(flight)
            if (prefs.showPhotos) {
                // Findes billedet allerede for netop dette fly, hentes det ikke igen
                val existing = PhotoStore.load(context, flight)
                if (existing == null) {
                    val photo = try {
                        FlightRepo.fetchPhoto(flight, maxWidth = 900)
                    } catch (_: Exception) {
                        null
                    }
                    PhotoStore.save(context, flight, photo)
                }
            } else {
                PhotoStore.clear(context)
            }
        }

        redrawAll()
        Result.success()
    }

    private fun redrawAll() {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, FlightWidget::class.java))
        ids.forEach { FlightWidget.renderInto(context, manager, it) }
    }

    private fun lastKnownLocation(): Pair<Double, Double>? {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        return try {
            val providers = lm.getProviders(true)
            var best: android.location.Location? = null
            for (p in providers) {
                val loc = lm.getLastKnownLocation(p) ?: continue
                if (best == null || loc.time > best!!.time) best = loc
            }
            best?.let { it.latitude to it.longitude }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }
}

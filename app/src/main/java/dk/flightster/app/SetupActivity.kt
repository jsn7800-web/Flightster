package dk.flightster.app

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Opsætning af widgeten.
 *
 * Vigtigt: en widget med `android:configure` bliver annulleret af systemet,
 * med mindre denne skærm afslutter med RESULT_OK og widgetens id. Derfor
 * sættes resultatet til CANCELED med det samme, så et tryk på tilbage rydder
 * ordentligt op, og først til OK når brugeren gemmer.
 */
class SetupActivity : AppCompatActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setResult(Activity.RESULT_CANCELED, resultIntent())

        val d = resources.displayMetrics.density
        val pad = (18 * d).toInt()

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        fun label(text: String) = TextView(this).apply {
            this.text = text
            setTextColor(0xFF6E6A62.toInt())
            textSize = 11f
            letterSpacing = 0.14f
            isAllCaps = true
            setPadding(0, (12 * d).toInt(), 0, (4 * d).toInt())
        }

        fun field(value: String, numeric: Boolean = false) = EditText(this).apply {
            setText(value)
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF141310.toInt())
            setPadding(pad / 2, pad / 2, pad / 2, pad / 2)
            textSize = 15f
            if (numeric) inputType = InputType.TYPE_CLASS_NUMBER
        }

        fun check(text: String, on: Boolean) = CheckBox(this).apply {
            this.text = text
            isChecked = on
            setTextColor(0xFFE6E2D9.toInt())
            setPadding(0, (10 * d).toInt(), 0, 0)
        }

        column.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(0xFFF2A63B.toInt())
            textSize = 26f
            letterSpacing = 0.10f
            isAllCaps = true
        })
        column.addView(TextView(this).apply {
            text = "Widgeten viser det seneste fly der var over dig. " +
                "Android opdaterer højst hvert kvarter, så tidsstemplet " +
                "fortæller hvor gammel observationen er."
            setTextColor(0xFF6E6A62.toInt())
            textSize = 12f
            setPadding(0, (6 * d).toInt(), 0, 0)
        })

        column.addView(label("Adresse på din Flightster"))
        val urlField = field(prefs.baseUrl)
        column.addView(urlField)

        column.addView(label("Stednavn"))
        val placeField = field(prefs.placeName)
        column.addView(placeField)

        column.addView(label("Radius i km"))
        val radiusField = field(prefs.radiusKm.toString(), numeric = true)
        column.addView(radiusField)

        column.addView(label("Mindste højdevinkel i grader"))
        val elevField = field(prefs.minElevation.toString(), numeric = true)
        column.addView(elevField)

        val auto = check("Følg min position automatisk", prefs.autoLocation)
        column.addView(auto)
        val photos = check("Vis foto af flyet når det findes", prefs.showPhotos)
        column.addView(photos)

        val save = Button(this).apply {
            text = "Gem og læg på skærmen"
            setOnClickListener {
                prefs.baseUrl = urlField.text.toString().ifBlank { Prefs.DEFAULT_BASE_URL }
                prefs.placeName = placeField.text.toString()
                prefs.radiusKm = radiusField.text.toString().toIntOrNull()?.coerceIn(2, 300) ?: 90
                prefs.minElevation = elevField.text.toString().toIntOrNull()?.coerceIn(0, 60) ?: 2
                prefs.autoLocation = auto.isChecked
                prefs.showPhotos = photos.isChecked

                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    FlightWidget.renderInto(
                        this@SetupActivity,
                        AppWidgetManager.getInstance(this@SetupActivity),
                        widgetId
                    )
                }
                FlightWidget.scheduleUpdates(this@SetupActivity)
                FlightWidget.refreshNow(this@SetupActivity)

                setResult(Activity.RESULT_OK, resultIntent())
                finish()
            }
        }
        column.addView(
            save,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            addView(column)
        }
        setContentView(scroll)
    }

    private fun resultIntent(): Intent =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}

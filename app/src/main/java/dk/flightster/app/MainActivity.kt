package dk.flightster.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Fuldskærmsudgaven af Flightster.
 *
 * Selve appen er den samme enkeltfils-webapp som kører på væggen — den
 * ligger i assets og indlæses fra det rigtige domæne, så alle kald går
 * gennem den samme opsætning. Widgeten er derimod tegnet nativt, fordi
 * Android ikke tillader en WebView i en widget.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var prefs: Prefs

    companion object {
        private const val REQ_LOCATION = 42
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        web = WebView(this)
        setContentView(web)
        goFullscreen()

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
        }
        web.setBackgroundColor(0xFF000000.toInt())
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                goFullscreen()
            }
        }

        web.loadUrl(prefs.baseUrl)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        askForLocationIfNeeded()
        FlightWidget.scheduleUpdates(this)
        FlightWidget.refreshNow(this)
    }

    /**
     * Widgeten kan kun følge med dig rundt hvis den må se din position.
     * Den beder kun om almindelig forgrundstilladelse — baggrundslokation
     * ville koste batteri uden at give noget, når intervallet er en halv time.
     */
    private fun askForLocationIfNeeded() {
        if (!prefs.autoLocation) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            REQ_LOCATION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION) {
            FlightWidget.refreshNow(this)
        }
    }

    private fun goFullscreen() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goFullscreen()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}

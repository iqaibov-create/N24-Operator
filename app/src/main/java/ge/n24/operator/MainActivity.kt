package ge.n24.operator

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.FirebaseApp
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var deviceToken: String = ""
    private val host = "n24.ge"
    private val adminUrl = "https://n24.ge/admin"
    private val operatorUrl = "https://n24.ge/destek/operator.php"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12, 52, 73)
        window.navigationBarColor = Color.rgb(12, 52, 73)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        val toolbar = LinearLayout(this).apply {
            setBackgroundColor(Color.rgb(16, 66, 91)); gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        val title = TextView(this).apply {
            text = "N24  •  Operator"; setTextColor(Color.WHITE); textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        toolbar.addView(title, LinearLayout.LayoutParams(0, dp(44), 1f))
        val login = actionButton("Admin") { web.loadUrl(adminUrl) }
        val chat = actionButton("Söhbətlər") { web.loadUrl(operatorUrl) }
        toolbar.addView(login)
        toolbar.addView(chat)
        root.addView(toolbar)

        web = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            setBackgroundColor(Color.rgb(244, 249, 251))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.setSupportMultipleWindows(false)
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val u = request?.url ?: return false
                    if (u.scheme == "https" && u.host == host) return false
                    if (u.scheme == "http" || u.scheme == "https") {
                        try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (_: Exception) {}
                    }
                    return true
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    sendTokenToOperatorPage()
                }
            }
        }
        root.addView(web)
        setContentView(root)
        deviceToken = getSharedPreferences("n24", MODE_PRIVATE).getString("push_token", "") ?: ""
        // Only request FCM token when the site owner has actually configured Firebase.
        if (FirebaseApp.initializeApp(this) != null) {
          FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            deviceToken = token
            getSharedPreferences("n24", MODE_PRIVATE).edit().putString("push_token", token).apply()
            sendTokenToOperatorPage()
          }
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 120)
        }
        val requestedChat = intent.getBooleanExtra("open_chat", false)
        web.loadUrl(if (requestedChat) operatorUrl else adminUrl)
    }
    private fun actionButton(label: String, click: () -> Unit): Button = Button(this).apply {
        text = label; isAllCaps = false; textSize = 12f
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(15, 126, 116))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38))
        lp.marginStart = dp(5); layoutParams = lp
        setOnClickListener { click() }
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun sendTokenToOperatorPage() {
        if (!::web.isInitialized || deviceToken.length < 30) return
        val uri = try { Uri.parse(web.url) } catch (_: Exception) { null } ?: return
        if (uri.scheme != "https" || uri.host != host || !uri.path.orEmpty().startsWith("/destek/operator.php")) return
        // Expose only the device FCM token on the trusted operator page, not a generic JS bridge.
        val escaped = JSONObject.quote(deviceToken)
        web.evaluateJavascript("window.N24NativePushToken=$escaped;window.dispatchEvent(new Event('n24-operator-token'));", null)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::web.isInitialized && intent.getBooleanExtra("open_chat", false)) web.loadUrl(operatorUrl)
    }
    @Deprecated("Legacy Android back callback")
    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack()
        else if (::web.isInitialized) web.loadUrl(adminUrl)
        else super.onBackPressed()
    }
    override fun onDestroy() {
        if (::web.isInitialized) { web.stopLoading(); web.destroy() }
        super.onDestroy()
    }
}

package com.example.mobileschedule

import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.ui.importentry.OnlineImportPreviewState
import com.example.mobileschedule.ui.importentry.ZhengfangOnlineReadScreen
import com.example.mobileschedule.ui.theme.MobileScheduleTheme
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SchoolLoginWebViewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val base = "https://jwglxt.fjnu.edu.cn/jwglxt/xtgl/login_slogin.html"
    private val auth = "http://org.app.fjnu.edu.cn/openplatform/oauth3/authorize?appId=67&redirectUri=http%3A%2F%2Fjwglxt.fjnu.edu.cn%2Fsso%2FoauthLogin&state=synthetic"

    private fun screen(): WebView {
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        compose.setContent { MobileScheduleTheme {
            Scaffold { padding -> Box(Modifier.padding(padding)) {
                ZhengfangOnlineReadScreen(7, {}, initialUrl = "about:blank", quickImport = true,
                    previewState = OnlineImportPreviewState.Idle, onPreparePreview = {}, onInvalidatePreview = {})
            } }
        } }
        lateinit var webView: WebView
        compose.runOnIdle {
            fun find(view: View): WebView? = when (view) {
                is WebView -> view
                is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) }
                else -> null
            }
            webView = requireNotNull(find(compose.activity.window.decorView))
        }
        return webView
    }

    private fun evaluate(webView: WebView, script: String): String {
        val ready = CountDownLatch(1)
        var result = ""
        compose.runOnIdle { webView.evaluateJavascript(script) { result = it; ready.countDown() } }
        check(ready.await(5, TimeUnit.SECONDS)) { "WebView JavaScript did not finish" }
        return result
    }

    private fun fixture(webView: WebView) {
        compose.runOnIdle {
            val original = webView.webViewClient
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                    original.shouldOverrideUrlLoading(view, request)
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) =
                    original.onPageStarted(view, url, favicon)
                override fun onPageFinished(view: WebView, url: String) = original.onPageFinished(view, url)
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                    WebResourceResponse("text/html", "UTF-8",
                        "<html><head><title>synthetic-auth</title></head><body>合成二维码登录页面</body></html>".byteInputStream())
            }
            webView.loadDataWithBaseURL(base, """
                <html><head><meta name="viewport" content="width=device-width, initial-scale=1"><title>synthetic-login</title>
                <style>body{margin:12px;min-height:600px}input{display:block;width:90%;height:40px;margin:16px 0;font-size:18px}</style></head>
                <body><div style="height:60px">合成学校登录表单</div><input id="account" autocomplete="off"><input id="password" type="password" autocomplete="off">
                <a id="auth" href="$auth">统一身份认证</a></body></html>
            """.trimIndent(), "text/html", "UTF-8", null)
        }
        compose.waitUntil(10_000) { compose.runOnIdle { webView.title == "synthetic-login" } }
    }

    @Test fun schoolIdentityLinkOpensItsHttpsPageWithOriginalQuery() {
        val webView = screen()
        fixture(webView)
        evaluate(webView, "document.getElementById('auth').click()")
        compose.waitUntil(5_000) { compose.runOnIdle { webView.title == "synthetic-auth" } }
        compose.runOnIdle { assertEquals(auth.replaceFirst("http:", "https:"), webView.url) }
        val rendered = CountDownLatch(1)
        compose.runOnIdle {
            webView.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) { rendered.countDown() }
            })
        }
        check(rendered.await(5, TimeUnit.SECONDS)) { "Authentication page did not reach the renderer" }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(200)
        saveSyntheticScreenshot("school-identity-https.png",
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!)
    }

    @Test fun untrustedHttpAndLocalLinksDoNotLeaveTheLoginPage() {
        val webView = screen()
        fixture(webView)
        val originalUrl = compose.runOnIdle { webView.url }
        listOf("http://untrusted.invalid/login", "file:///sdcard/test", "intent://test").forEach { url ->
            evaluate(webView, "location.href='$url'")
            compose.runOnIdle { assertEquals(originalUrl, webView.url); assertEquals("synthetic-login", webView.title) }
        }
    }

    @Test fun repeatedSchoolFieldTypingKeepsSettledKeyboardLayoutStable() {
        val webView = screen()
        fixture(webView)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = compose.activity.contentResolver
        val previous = Settings.Secure.getString(resolver, "show_ime_with_hard_keyboard")
        fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).close() }
        shell("settings put secure show_ime_with_hard_keyboard 1")
        try {
            listOf("account", "password").forEach { id ->
                evaluate(webView, "document.getElementById('$id').scrollIntoView({block:'center'})")
                compose.waitForIdle()
                SystemClock.sleep(300)
                val geometry = JSONObject(JSONTokener(evaluate(webView, """
                    (function(){var r=document.getElementById('$id').getBoundingClientRect();return JSON.stringify({x:r.left+r.width/2,y:r.top+r.height/2-(window.visualViewport?visualViewport.offsetTop:0),width:innerWidth});})()
                """.trimIndent())).nextValue() as String)
                val location = IntArray(2)
                var ratio = 1f
                compose.runOnIdle { webView.getLocationOnScreen(location); ratio = webView.width / geometry.getDouble("width").toFloat() }
                val x = location[0] + geometry.getDouble("x").toFloat() * ratio
                val y = location[1] + geometry.getDouble("y").toFloat() * ratio
                compose.runOnIdle {
                    assertTrue("$id must be visible above the keyboard before tapping",
                        y >= location[1] && y < location[1] + webView.height)
                }
                val downTime = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
                instrumentation.uiAutomation.injectInputEvent(down, true)
                down.recycle()
                SystemClock.sleep(80)
                val upTime = SystemClock.uptimeMillis()
                val up = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, x, y, 0)
                instrumentation.uiAutomation.injectInputEvent(up, true)
                up.recycle()
                compose.waitUntil(10_000) {
                    if (evaluate(webView, "document.activeElement.id") != "\"$id\"") {
                        evaluate(webView, "document.getElementById('$id').focus()")
                    }
                    evaluate(webView, "document.activeElement.id") == "\"$id\"" && compose.runOnIdle {
                        ViewCompat.getRootWindowInsets(webView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                    }
                }
                // Initial keyboard appearance can legitimately scroll the focused element into view.
                compose.waitForIdle()
                SystemClock.sleep(800)
                val ranges = mutableListOf<Double>()
                val nativeRanges = mutableListOf<Int>()
                val heightRanges = mutableListOf<Int>()
                val connection = compose.runOnIdle { requireNotNull(webView.onCreateInputConnection(EditorInfo())) }
                repeat(8) {
                    evaluate(webView, """
                        window.probe=[];window.probeDone=false;(function sample(n){probe.push(document.getElementById('$id').getBoundingClientRect().top-(window.visualViewport?visualViewport.offsetTop:0));if(n<20)requestAnimationFrame(function(){sample(n+1)});else probeDone=true;})(0)
                    """.trimIndent())
                    compose.runOnIdle { assertTrue(connection.commitText("1", 1)) }
                    val tops = mutableListOf<Int>()
                    val heights = mutableListOf<Int>()
                    compose.waitUntil(5_000) {
                        val done = evaluate(webView, "window.probeDone") == "true"
                        compose.runOnIdle { webView.getLocationOnScreen(location); tops += location[1]; heights += webView.height }
                        done
                    }
                    val values = org.json.JSONArray(evaluate(webView, "JSON.stringify(window.probe)").let { JSONTokener(it).nextValue() as String })
                    val positions = (0 until values.length()).map { values.getDouble(it) }
                    ranges += positions.max() - positions.min()
                    nativeRanges += tops.max() - tops.min()
                    heightRanges += heights.max() - heights.min()
                }
                val length = evaluate(webView, "document.getElementById('$id').value.length").toInt()
                assertEquals(8, length)
                Log.i("SchoolKeyboardProbe", JSONObject().put("field", id).put("typedCharacters", length)
                    .put("perCharacterMovementCssPx", org.json.JSONArray(ranges))
                    .put("webViewTopMovementPx", org.json.JSONArray(nativeRanges))
                    .put("webViewHeightMovementPx", org.json.JSONArray(heightRanges)).toString())
                assertTrue("Repeated typing must not oscillate after keyboard settles: $ranges", ranges.drop(1).all { it <= 2.0 })
                assertTrue("WebView position must stay stable: $nativeRanges", nativeRanges.all { it <= 2 })
                assertTrue("WebView height must stay stable: $heightRanges", heightRanges.all { it <= 2 })
            }
            saveSyntheticScreenshot("school-keyboard-probe.png", instrumentation.uiAutomation.takeScreenshot()!!)
        } finally {
            if (previous == null) shell("settings delete secure show_ime_with_hard_keyboard")
            else shell("settings put secure show_ime_with_hard_keyboard $previous")
        }
    }
}

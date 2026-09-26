package com.example.mobileschedule.ui.importentry

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mobileschedule.data.importer.ParsedZhengfangSchedule
import com.example.mobileschedule.data.importer.ZhengfangOnlineRead
import com.example.mobileschedule.data.importer.ZhengfangParseErrorCode
import com.example.mobileschedule.data.importer.ZhengfangParseException
import com.example.mobileschedule.data.importer.ZhengfangSourceTerm
import java.net.URI
import kotlinx.coroutines.delay

private sealed interface OnlineReadState {
    data object Idle : OnlineReadState
    data object Reading : OnlineReadState
    data object Canceled : OnlineReadState
    data class Failed(val message: String) : OnlineReadState
    data class Ready(val selected: ZhengfangSourceTerm, val parsed: ParsedZhengfangSchedule) : OnlineReadState
}

@Composable
fun ZhengfangOnlineReadRoute(
    targetSemesterId: Long,
    onExit: () -> Unit,
    viewModel: OnlineImportPreviewViewModel = hiltViewModel(),
) {
    val previewState by viewModel.state.collectAsStateWithLifecycle()
    ZhengfangOnlineReadScreen(targetSemesterId, onExit,
        previewState = previewState,
        onPreparePreview = viewModel::show,
        onInvalidatePreview = viewModel::invalidate)
}

/** The user enters credentials only in the school WebView. No JavaScript bridge or database write exists here. */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZhengfangOnlineReadScreen(
    targetSemesterId: Long,
    onExit: () -> Unit,
    initialUrl: String = ZhengfangOnlineRead.LOGIN_URL,
    previewState: OnlineImportPreviewState,
    onPreparePreview: (ParsedZhengfangSchedule) -> Unit,
    onInvalidatePreview: () -> Unit,
) {
    val context = LocalContext.current
    var yearText by remember { mutableStateOf("") }
    var termNumber by remember { mutableIntStateOf(1) }
    var pageUrl by remember { mutableStateOf("") }
    var pageLoading by remember { mutableStateOf(true) }
    var readState by remember { mutableStateOf<OnlineReadState>(OnlineReadState.Idle) }
    var generation by remember { mutableIntStateOf(0) }
    var pendingTerm by remember { mutableStateOf<ZhengfangSourceTerm?>(null) }
    var showingPreview by remember { mutableStateOf(false) }
    val selected = ZhengfangOnlineRead.selectTerm(yearText, termNumber)
    val reading = readState == OnlineReadState.Reading

    val webView = remember(context) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webChromeClient = object : WebChromeClient() {}
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.url.scheme != "https"

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    pageLoading = true
                    pageUrl = url
                    if (readState is OnlineReadState.Ready) readState = OnlineReadState.Idle
                }

                override fun onPageFinished(view: WebView, url: String) {
                    pageLoading = false
                    pageUrl = url
                    if (readState != OnlineReadState.Reading) return
                    if (!ZhengfangOnlineRead.isScheduleEndpoint(url)) {
                        readState = OnlineReadState.Failed(
                            if (isLoginPage(url)) "学校要求重新登录，请在页面完成认证后再读取。"
                            else "课表请求跳转到其他页面，请检查登录状态后重试。")
                        return
                    }
                    val requestedAt = generation
                    view.evaluateJavascript(
                        "(function(){return document.body ? document.body.innerText : null})()",
                    ) { result ->
                        if (requestedAt != generation || readState != OnlineReadState.Reading) return@evaluateJavascript
                        val body = ZhengfangOnlineRead.decodeWebViewText(result)
                        val requestedTerm = pendingTerm
                        readState = if (body == null || requestedTerm == null) OnlineReadState.Failed("课表响应为空或无法读取，请重试。")
                        else try {
                            val parsed = ZhengfangOnlineRead.parse(body, targetSemesterId, requestedTerm)
                            onPreparePreview(parsed)
                            showingPreview = true
                            OnlineReadState.Ready(requestedTerm, parsed)
                        } catch (error: ZhengfangParseException) {
                            OnlineReadState.Failed(parseErrorMessage(error.diagnostic.code))
                        } catch (_: IllegalArgumentException) {
                            OnlineReadState.Failed("课表响应无法识别，请返回学校页面后重试。")
                        }
                    }
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest,
                    error: WebResourceError) {
                    if (request.isForMainFrame) {
                        pageLoading = false
                        readState = OnlineReadState.Failed("学校页面加载失败，请检查网络后重试。")
                    }
                }

                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest,
                    errorResponse: WebResourceResponse) {
                    if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                        pageLoading = false
                        readState = OnlineReadState.Failed("学校页面返回 HTTP ${errorResponse.statusCode}，请稍后重试。")
                    }
                }

                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    handler.cancel()
                    pageLoading = false
                    readState = OnlineReadState.Failed("学校页面证书校验失败，已停止连接。")
                }
            }
            loadUrl(initialUrl)
        }
    }
    DisposableEffect(webView) {
        onDispose {
            generation++
            webView.stopLoading()
            webView.destroy()
        }
    }
    LaunchedEffect(readState, generation) {
        if (readState == OnlineReadState.Reading) {
            delay(45_000)
            if (readState == OnlineReadState.Reading) {
                generation++
                webView.stopLoading()
                readState = OnlineReadState.Failed("课表读取超时，请检查网络或重新登录后重试。")
            }
        }
    }

    fun cancelRead() {
        generation++
        webView.stopLoading()
        onInvalidatePreview()
        readState = OnlineReadState.Canceled
    }
    fun exit() {
        generation++
        webView.stopLoading()
        onInvalidatePreview()
        CookieManager.getInstance().removeAllCookies(null)
        onExit()
    }
    fun closePreview() {
        onInvalidatePreview()
        showingPreview = false
        readState = OnlineReadState.Idle
    }
    BackHandler {
        when {
            showingPreview -> closePreview()
            reading -> cancelRead()
            webView.canGoBack() -> webView.goBack()
            else -> exit()
        }
    }

    if (showingPreview) {
        OnlineImportPreviewScreen(previewState, onBack = ::closePreview, onReadAgain = ::closePreview)
        return
    }

    Scaffold(modifier = Modifier.fillMaxSize().testTag("online_read_page"),
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("福建师大课表读取") }, windowInsets = WindowInsets(0),
            navigationIcon = { TextButton(onClick = ::exit, modifier = Modifier.testTag("online_back")) {
                Text("退出")
            } }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("请在下方学校页面自行登录和完成验证。账号、密码与验证码只输入在学校页面。",
                style = MaterialTheme.typography.bodySmall)
            Text(if (pageUrl.isSchoolHost()) "当前为福建师大教务域名" else "当前为登录跳转页或尚未打开学校页面",
                style = MaterialTheme.typography.labelSmall)
            OutlinedTextField(value = yearText, onValueChange = {
                if (it != yearText) {
                    onInvalidatePreview()
                    readState = OnlineReadState.Idle
                }
                yearText = it
            },
                label = { Text("来源学年起始年份，例如 2026") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("online_year"))
            Row {
                listOf(1, 2).forEach { number ->
                    Row(modifier = Modifier.weight(1f)) {
                        RadioButton(selected = termNumber == number, onClick = {
                            if (termNumber != number) {
                                onInvalidatePreview()
                                readState = OnlineReadState.Idle
                                termNumber = number
                            }
                        },
                            modifier = Modifier.testTag("online_term_$number"))
                        Text("第 $number 学期", modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val sourceTerm = selected ?: return@Button
                    generation++
                    onInvalidatePreview()
                    pendingTerm = sourceTerm
                    readState = OnlineReadState.Reading
                    webView.postUrl(ZhengfangOnlineRead.SCHEDULE_URL,
                        ZhengfangOnlineRead.postBody(sourceTerm).toByteArray(Charsets.UTF_8))
                }, enabled = selected != null && pageUrl.isSchoolHost() && !pageLoading && !reading,
                    modifier = Modifier.testTag("online_read_button")) { Text("读取所选学期") }
                if (reading) TextButton(onClick = ::cancelRead,
                    modifier = Modifier.testTag("online_cancel")) { Text("取消读取") }
            }
            when (val state = readState) {
                OnlineReadState.Idle -> Text("读取前请确认学校网页已登录；学年和学期为请求参数，完整性尚待核实。")
                OnlineReadState.Reading -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    Text("正在读取并解析课表…")
                }
                OnlineReadState.Canceled -> Text("已取消读取，本地课表未改变。")
                is OnlineReadState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("online_read_error"))
                is OnlineReadState.Ready -> Text(
                    "${state.parsed.request.sourceTermLabel ?: state.selected.sourceLabel}：读取 ${state.parsed.request.sourceObservedCount ?: 0} 条课程安排，" +
                        "解析 ${state.parsed.parsedRowCount} 条，错误 ${state.parsed.diagnostics.size} 条。" +
                        if (state.parsed.request.sourceTermId == null) "来源学期身份与完整性仍未知，暂不保存。"
                        else "学校已回显来源学期；课表完整性仍未知，暂不保存。",
                    modifier = Modifier.testTag("online_read_result"),
                )
            }
            if (pageLoading) Text("学校页面加载中…", style = MaterialTheme.typography.labelSmall)
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxWidth().weight(1f))
        }
    }
}

private fun String.isSchoolHost(): Boolean = try {
    val uri = URI(this)
    uri.scheme.equals("https", ignoreCase = true) &&
        uri.host.equals("jwglxt.fjnu.edu.cn", ignoreCase = true)
} catch (_: IllegalArgumentException) {
    false
}

private fun isLoginPage(url: String): Boolean = try {
    URI(url).path.endsWith("/login_slogin.html")
} catch (_: IllegalArgumentException) {
    false
}

private fun parseErrorMessage(code: ZhengfangParseErrorCode): String = when (code) {
    ZhengfangParseErrorCode.LOGIN_REQUIRED -> "登录已失效，请在学校页面重新登录。"
    ZhengfangParseErrorCode.EMPTY_SCHEDULE -> "学校返回零条课程安排；不会覆盖本地课表。"
    ZhengfangParseErrorCode.UNSUPPORTED_EXTRA_COURSES -> "响应含有尚未支持的额外课程，已停止读取以避免漏课。"
    ZhengfangParseErrorCode.SOURCE_TERM_MISMATCH -> "学校返回的来源学期与所选学期不符，请重新选择后读取。"
    ZhengfangParseErrorCode.NON_SCHEDULE_RESPONSE -> "返回的不是已识别的课表，请检查登录与来源学期。"
    else -> "课表响应解析失败：$code。请检查来源页面后重试。"
}

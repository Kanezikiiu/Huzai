package com.java.myapplication.ui.pages

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.ConsoleMessage
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.material3.CircularProgressIndicator
import com.java.myapplication.ui.components.SecondaryPage
import com.java.myapplication.ui.components.tapGuard
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import com.java.myapplication.data.HupuAccount
import kotlinx.coroutines.launch

/**
 * 登录页（盖入式二级页）：WebView 加载虎扑官方登录页。
 * 用户在官方页面自行输入账号密码（本 App 全程不经手凭据）；
 * 登录成功跳回 bbs.hupu.com 后自动提取 cookie 摄取会话，提示条淡出、页面自动关闭。
 */
private const val LOGIN_URL = "https://passport.hupu.com/v2/login?pcPhone=1&jumpurl=https%3A%2F%2Fbbs.hupu.com&from=https%3A%2F%2Fbbs.hupu.com#/"

@Composable
fun LoginPage(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    // 1.192: 版本探测改为「随 WebView 一起延迟计算」——getDefaultUserAgent 首次调用
    // 会触发 WebView 内核初始化，放在首帧会阻塞入场动画。
    var webViewMajor by remember { mutableIntStateOf(0) }
    // 1.179(C): 加载失败可诊断状态（null = 无错误）
    var loadError by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var oldWarnDismissed by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var toastTick by remember { mutableStateOf(0) }
    var closing by remember { mutableStateOf(false) }
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    // 1.192: 先播「页面打开」动画，再挂载 WebView。WebView 构造 + 首次加载很重，
    // 若与首帧同帧创建会阻塞入场动画，表现为「先加载再打开」的明显延迟/卡顿。
    var webMounted by remember { mutableStateOf(false) }
    // 1.192: 计数 flag 门控——多页叠加 / 重挂载时不会多减，离开组合时兜底回收
    var pageEntered by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        pageEntered = true
        SecondaryPage.enter()
        progress.animateTo(1f, androidx.compose.animation.core.tween(280))
        webViewMajor = runCatching {
            val ua = android.webkit.WebSettings.getDefaultUserAgent(ctx)
            Regex("Chrome/(\\d+)").find(ua)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }.getOrDefault(0)
        webMounted = true
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { if (pageEntered) { pageEntered = false; SecondaryPage.exit() } }
    }
    LaunchedEffect(closing) {
        if (closing) {
            if (pageEntered) { pageEntered = false; SecondaryPage.exit() }
            progress.animateTo(0f, androidx.compose.animation.core.tween(280))
            onClose()
        }
    }
    LaunchedEffect(toastTick) {
        if (toastTick > 0) {
            kotlinx.coroutines.delay(2200)
            toast = null
        }
    }

    // 返回手势跟手：手势进度直推透明度，取消则弹回
    PredictiveBackHandler { events ->
        if (closing) {
            events.collect { }
            return@PredictiveBackHandler
        }
        try {
            events.collect { event -> progress.snapTo(1f - event.progress) }
            progress.animateTo(0f, androidx.compose.animation.core.tween(120))
            closing = true
        } catch (e: CancellationException) {
            progress.animateTo(1f, androidx.compose.animation.core.tween(200))
            throw e
        }
    }

    val loginHandled = remember { androidx.compose.runtime.mutableStateOf(false) }
    fun finishLogin() {
        if (loginHandled.value) return
        if (checking || closing) return
        loginHandled.value = true
        checking = true
        scope.launch {
            val err = HupuAccount.ingestFromWebView()
            checking = false
            if (err == null) {
                toast = "登录成功"
                toastTick++
                closing = true
            } else {
                toast = err
                toastTick++
            }
        }
    }

    // 出入场改用透明度渐变（不带 graphicsLayer 平移）：平移会把
    // WebView 的 Surface 拖进硬件图层链，部分机型上表现为持续闪烁
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(2f)
            .graphicsLayer { alpha = progress.value }
            .background(MaterialTheme.colorScheme.background)
            .tapGuard(),
    ) {
        Column(Modifier.fillMaxSize()) {
            // 顶栏：返回 + 标题（对齐 ThreadHeader 形态）
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 8.dp, bottom = 10.dp, start = 12.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { closing = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "\u8fd4\u56de",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "\u767b\u5f55\u864e\u6251\u8d26\u53f7",
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (webMounted) {
                HupuLoginWebView(
                    onLoginSuccess = { finishLogin() },
                    reloadTick = reloadTick,
                    onLoadError = { loadError = it },
                )
            } else {
                // WebView 挂载前的占位：居中转圈，明确「已打开、正在加载」
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        // 登录中遮罩
        if (checking) {
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(3f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        // 1.179(B/C): 登录页提示层（底部卡片）——失败可诊断，老内核可预警
        when {
            loadError != null -> Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(3f)
                    .navigationBarsPadding(),
            ) {
                LoginHintCard(
                    title = "登录页加载失败",
                    message = loadError!! +
                        "\n\n可尝试：更新「Android System WebView」、关闭代理/VPN、检查系统时间后重试。",
                    primaryText = "重试",
                    onPrimary = { loadError = null; reloadTick++ },
                    secondaryText = "去更新 WebView",
                    onSecondary = { openWebViewStore(ctx) },
                )
            }
            webViewMajor in 1..79 && !oldWarnDismissed -> Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(3f)
                    .navigationBarsPadding(),
            ) {
                LoginHintCard(
                    title = "系统 WebView 版本过低",
                    message = "检测到 WebView $webViewMajor（建议 80 及以上）。\n登录页可能无法加载验证码，请更新「Android System WebView」后重试。",
                    primaryText = "去更新",
                    onPrimary = { openWebViewStore(ctx) },
                    secondaryText = "仍然尝试",
                    onSecondary = { oldWarnDismissed = true },
                )
            }
        }

        // 结果提示条（顶部滑入淡出）
        toast?.let { msg ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .zIndex(4f)
                    .statusBarsPadding()
                    .padding(top = 64.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Text(
                    msg,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * 轮询 CookieManager 直接检查登录凭据（不依赖跳转 URL，
 * 因为官方登录成功后的跳转目标可能不是 bbs.hupu.com）。
 */
private fun hasLoginCookie(): Boolean {
    val ck = CookieManager.getInstance().getCookie("https://bbs.hupu.com") ?: return false
    return ck.contains("u=") && ck.contains("us=")
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HupuLoginWebView(
    onLoginSuccess: () -> Unit,
    reloadTick: Int,
    onLoadError: (String?) -> Unit,
) {
    // 登录凭据轮询：每 1s 直接查 cookie，不依赖页面跳转。
    // 注意：效果键必须是稳定的 Unit（回调用 rememberUpdatedState
    // 转发）——若键含会变状态（如 sessionVersion），成功摄取后的
    // 重组会重启轮询再次命中 cookie，与重挂键形成摄取循环。
    // 防退出残留由 logout 清除 cookie 保障，不靠键控。
    val cb by rememberUpdatedState(onLoginSuccess)
    // 1.180(C): 加载判定器（详见 LoginLoadMonitor 注释）——把"失败"收敛为确定信号，杜绝误判
    val loginScope = rememberCoroutineScope()
    val reportErr by rememberUpdatedState(onLoadError)
    val monitor = remember { LoginLoadMonitor(loginScope) { reportErr(it) } }
    LaunchedEffect(Unit) {
        while (true) {
            if (hasLoginCookie()) {
                cb()
                break
            }
            kotlinx.coroutines.delay(1000)
        }
    }
    // 1.179(C): reloadTick 变化 → 重建 WebView，实现「重试」重新加载
    androidx.compose.runtime.key(reloadTick) {
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                // 背景色对齐主题：避免 WebView 默认白底在加载间隙白闪
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setLayerType(android.webkit.WebView.LAYER_TYPE_HARDWARE, null)
                settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
                settings.domStorageEnabled = true
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        // 官方登录完成后会跳回 bbs.hupu.com —— 此时提取 cookie
                        return false
                    }

                    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                        // 1.180(C): 新导航开始 → 作废旧判定，并清除已显示的错误（自愈）
                        monitor.onNavigationStart()
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        // 快路径：任意页面加载完成时顺手检一次 cookie
                        if (hasLoginCookie()) { onLoginSuccess(); return }
                        // 1.180(C): 渲染完成后再判定"页面是否真的可用"（SPA 异步渲染，需留缓冲）
                        view.postDelayed({ probeUsable(view, monitor) }, 800)
                    }

                    // 1.180(C): 主文档错误 → 仅登记为"候选失败"（可能被随后的正常加载推翻）
                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        if (request.isForMainFrame) {
                            val host = request.url?.host ?: "?"
                            monitor.fail("加载失败（错误码 ${error.errorCode}）\n域名：$host")
                        }
                    }

                    // 1.180(C): HTTP 异常 → 仅登记为"候选失败"
                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        response: WebResourceResponse,
                    ) {
                        if (request.isForMainFrame && response.statusCode >= 400) {
                            val host = request.url?.host ?: "?"
                            monitor.fail("页面返回异常（HTTP ${response.statusCode}）\n域名：$host")
                        }
                    }
                }
                // 1.180(C): console 报错只登记为"补充信息"，绝不单独触发失败。
                // 旧版用 contains("Uncaught") 直接报错——页面上一句无害 JS 报错就会误判，
                // 这就是"有时会发生误判"的主因。onConsoleMessage 属于 WebChromeClient。
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                        val m = msg.message() ?: ""
                        if (m.contains("SyntaxError") || m.contains("Uncaught")) {
                            monitor.noteDetail("脚本报错：${m.take(80)}")
                        }
                        return true
                    }
                }
                loadUrl(LOGIN_URL)
            }
        },
        onRelease = { it.destroy() },
    )
    }
}

/** 提示卡片（底部）：标题居左 + 正文 + 两枚大圆角按钮（主题色 / 浅灰） */
@Composable
private fun LoginHintCard(
    title: String,
    message: String,
    primaryText: String,
    onPrimary: () -> Unit,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.97f))
            .padding(16.dp),
    ) {
        Column {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (secondaryText != null && onSecondary != null) {
                    HintButton(
                        text = secondaryText,
                        primary = false,
                        modifier = Modifier.weight(1f),
                        onClick = onSecondary,
                    )
                }
                HintButton(
                    text = primaryText,
                    primary = true,
                    modifier = Modifier.weight(1f),
                    onClick = onPrimary,
                )
            }
        }
    }
}

@Composable
private fun HintButton(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (primary) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 14.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = if (primary) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 1.179(B): 跳应用商店更新「Android System WebView」（market 优先，回落 Play 网页） */
private fun openWebViewStore(ctx: android.content.Context) {
    val pkg = "com.google.android.webview"
    val ok = runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess
    if (!ok) {
        runCatching {
            ctx.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/**
 * 1.180(C): 判定页面是否"真的可用"——用正文文本长度作为**保守**信号：
 * 只要渲染出任何正文就认为可用（宁可漏报不可误报）；正文为空（脚本崩溃/白屏）才判失败。
 * 首次探测为空时延迟复探一次，适配 SPA 异步渲染。
 */
private fun probeUsable(view: WebView, monitor: LoginLoadMonitor, retry: Boolean = false) {
    val js = "(function(){try{var b=document.body;var t=b?(b.innerText||''):'';" +
        "return (''+t).replace(/\\s+/g,'').length;}catch(e){return -1;}})()"
    view.evaluateJavascript(js) { raw ->
        val n = raw?.trim()?.trim('"')?.toIntOrNull() ?: -1
        when {
            n > 0 -> monitor.usable()
            retry -> monitor.fail("页面内容为空（脚本可能未执行）")
            else -> view.postDelayed({ probeUsable(view, monitor, retry = true) }, 1000)
        }
    }
}

/**
 * 1.180(C): 登录页加载判定器。
 *
 * 目标：**宁可漏报，不可误报**。旧版收到一次 console 报错或一次瞬时
 * onReceivedError 就弹"加载失败"，正常页面也会被误伤。现在：
 *  · [onNavigationStart] 新导航开始 → 作废旧判定 + 清除已显示错误；
 *  · [fail] 登记"候选失败"，延迟 [GRACE_MS] 确认——期间出现 [usable] 即撤销；
 *  · [usable] 页面判定可用 → 取消候选 + 清除已显示错误（自愈）；
 *  · [noteDetail] 只记录补充信息，不作为失败依据。
 */
private class LoginLoadMonitor(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val report: (String?) -> Unit,
) {
    companion object { private const val GRACE_MS = 1800L }

    private var gen = 0
    private var job: kotlinx.coroutines.Job? = null
    private var detail: String? = null
    private var reported = false

    fun onNavigationStart() {
        gen++
        job?.cancel(); job = null
        detail = null
        if (reported) { reported = false; report(null) }
    }

    fun noteDetail(msg: String) {
        if (detail == null) detail = msg
    }

    fun fail(reason: String) {
        if (reported) return
        if (job?.isActive == true) return
        val my = gen
        job = scope.launch {
            kotlinx.coroutines.delay(GRACE_MS)
            if (my != gen || reported) return@launch
            reported = true
            report(
                buildString {
                    append(reason)
                    detail?.let { append("\n").append(it) }
                }
            )
        }
    }

    fun usable() {
        gen++
        job?.cancel(); job = null
        detail = null
        if (reported) { reported = false; report(null) }
    }
}

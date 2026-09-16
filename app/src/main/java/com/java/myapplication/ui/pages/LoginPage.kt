package com.java.myapplication.ui.pages

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
    var checking by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var toastTick by remember { mutableStateOf(0) }
    var closing by remember { mutableStateOf(false) }
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        SecondaryPage.enter()
        progress.animateTo(1f, androidx.compose.animation.core.tween(280))
    }
    LaunchedEffect(closing) {
        if (closing) {
            SecondaryPage.exit()
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
            HupuLoginWebView(
                onLoginSuccess = { finishLogin() },
            )
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
private fun HupuLoginWebView(onLoginSuccess: () -> Unit) {
    // 登录凭据轮询：每 1s 直接查 cookie，不依赖页面跳转。
    // 注意：效果键必须是稳定的 Unit（回调用 rememberUpdatedState
    // 转发）——若键含会变状态（如 sessionVersion），成功摄取后的
    // 重组会重启轮询再次命中 cookie，与重挂键形成摄取循环。
    // 防退出残留由 logout 清除 cookie 保障，不靠键控。
    val cb by rememberUpdatedState(onLoginSuccess)
    LaunchedEffect(Unit) {
        while (true) {
            if (hasLoginCookie()) {
                cb()
                break
            }
            kotlinx.coroutines.delay(1000)
        }
    }
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

                    override fun onPageFinished(view: WebView, url: String) {
                        // 快路径：任意页面加载完成时顺手检一次 cookie
                        if (hasLoginCookie()) onLoginSuccess()
                    }
                }
                loadUrl(LOGIN_URL)
            }
        },
        onRelease = { it.destroy() },
    )
}
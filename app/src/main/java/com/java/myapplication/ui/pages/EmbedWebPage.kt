package com.java.myapplication.ui.pages

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.java.myapplication.data.HupuApi
import com.java.myapplication.data.HupuEmbed
import com.java.myapplication.data.HupuLinks
import com.java.myapplication.ui.components.HupuIcons
import com.java.myapplication.ui.components.SecondaryPage
import com.java.myapplication.ui.components.tapGuard
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import com.java.myapplication.ui.components.CenteredTopBar
import com.java.myapplication.ui.components.LiquidBackButton
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import com.java.myapplication.ui.glass.buttonBorder
import com.java.myapplication.ui.glass.buttonFill
import com.java.myapplication.ui.glass.liquidElevation
import com.java.myapplication.ui.glass.liquidPressTransform
import com.java.myapplication.ui.glass.rememberLiquidHighlight
import com.java.myapplication.ui.theme.isAppDarkTheme

/**
 * 1.178: 结构化正文（赛事战报等）的承载页——盖入式二级页内嵌 WebView。
 *
 * 官方对这类帖子存的是 JSON 描述符（type=iframe-match + url），网页端自己也靠 JS 挂
 * iframe 指向该 url。这里用 WebView 直接加载同一地址，等价于官方的 iframe 形态；
 * 把 WebView 隔离在「点开之后」，不打扰帖子列表的滚动性能。
 */
@Composable
internal fun EmbedWebPage(
    embed: HupuEmbed,
    /**
     * 1.224o：页面里的链接交给父级**在应用内**处理（开帖 / 评分页 / 用户主页）。
     * 返回 true 表示已消费；false 时按「网页就地加载 / 外链浏览器」处理。
     */
    onLink: ((HupuLinks.Target) -> Boolean)? = null,
    /**
     * 1.225g：本页是否接管系统返回。
     *
     * 战报页里点开的帖子 / 评分 / 用户主页是**叠在本页之上**的浮层，而它们不一定各自注册了
     * 返回处理（例如 `PmThreadViewer` 只有顶栏返回键）—— 如果不加这个开关，在上层按系统返回
     * 会命中本页的 `PredictiveBackHandler`，把**战报页**关掉。
     */
    backEnabled: Boolean = true,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        SecondaryPage.enter()
        progress.animateTo(1f, tween(280))
    }
    // 1.192: 计数兜底——页面被任何路径销毁（如被重挂载）都会 onDispose 回收，防 Tab 栏计数泄漏
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { SecondaryPage.exit() }
    }
    var closing by remember { mutableStateOf(false) }
    // 1.2xx（真机反馈「点战报要等一会儿才打开」）：外层外壳先渲染，WebView 延后挂载
    var mountWeb by remember { mutableStateOf(false) }
    var webLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // 入场动画 280ms；WebView 首次实例化会阻塞主线程，放到动画之后再挂
        delay(300)
        mountWeb = true
    }
    LaunchedEffect(closing) {
        if (closing) {
            progress.animateTo(0f, tween(280))
            onClose()
        }
    }
    PredictiveBackHandler(enabled = backEnabled) { events ->
        if (closing) {
            events.collect { }
            return@PredictiveBackHandler
        }
        try {
            events.collect { event -> progress.snapTo(1f - event.progress) }
            progress.animateTo(0f, tween(120))
            closing = true
        } catch (e: CancellationException) {
            progress.animateTo(1f, tween(200))
            throw e
        }
    }

    fun openInBrowser() {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(embed.url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(2f)
            .graphicsLayer { translationX = (1f - progress.value) * size.width }
            .background(MaterialTheme.colorScheme.background)
            .tapGuard(),
    ) {
        Column(Modifier.fillMaxSize()) {
            // 顶栏：返回 + 标题 + 浏览器打开
            CenteredTopBar(
                title = embed.title,
                onBack = { closing = true },
                actions = {
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { openInBrowser() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            HupuIcons.Globe,
                            contentDescription = "浏览器打开",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )
            // 1.2xx（真机反馈「点战报要等一会儿才打开」）：WebView 的**首次实例化会阻塞主线程**
//（几百毫秒），若在页面第一帧就创建，入场动画会被卡住 → 表现成"点了没反应，过一会儿才出现"。
// 做法：页面外壳（顶栏 + 转圈）**第一帧就显示**，WebView 等入场动画播完再挂载。
            if (mountWeb) {
                EmbedWebView(
                    url = embed.url,
                    onLoaded = { webLoaded = true },
                    onLink = onLink,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (!webLoaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * 1.225e：结构化正文入口卡（点击进入 EmbedWebPage）。
 *
 * 视觉升级为全站统一的**玻璃卡**：玻璃底 + 标准描边 + 圆角16，
 * 左侧主题色淡底徽标、右侧圆形箭头，标题 15sp SemiBold，副标题 13sp 次要色。
 */
@Composable
internal fun EmbedCard(embed: HupuEmbed, onClick: () -> Unit) {
    val dark = isAppDarkTheme()
    val shape = RoundedCornerShape(16.dp)
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val variant = MaterialTheme.colorScheme.onSurfaceVariant
    // 与全站玻璃按钮**同源**的按压手感：按下处流体高光 + 常量形变 + 落影
    val highlight = rememberLiquidHighlight()

    Row(
        Modifier
            .fillMaxWidth()
            .liquidPressTransform(highlight)
            .liquidElevation(shape, dark)
            .clip(shape)
            .background(buttonFill(dark))
            .border(0.6.dp, buttonBorder(dark), shape)
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .then(highlight.modifier)
            .then(highlight.gestureModifier)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 1.225f：去掉主题色底块（真机反馈"难看就难看在那个色块上"），只留放大的文稿线条图标
        Box(
            Modifier.size(30.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(26.dp)) {
                val w = size.width
                val h = size.height
                val stroke = w * 0.085f
                // 文稿外框（左下留白，右上折角）
                drawPath(
                    path = Path().apply {
                        moveTo(w * 0.14f, h * 0.10f)
                        lineTo(w * 0.66f, h * 0.10f)
                        lineTo(w * 0.86f, h * 0.30f)
                        lineTo(w * 0.86f, h * 0.90f)
                        lineTo(w * 0.14f, h * 0.90f)
                        close()
                    },
                    color = primary,
                    style = Stroke(width = stroke, join = StrokeJoin.Round),
                )
                // 三条内容线
                listOf(0.42f, 0.56f, 0.70f).forEachIndexed { i, y ->
                    drawLine(
                        color = primary,
                        start = Offset(w * 0.28f, h * y),
                        end = Offset(w * (if (i == 2) 0.62f else 0.72f), h * y),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                embed.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                embed.subtitle,
                fontSize = 12.5.sp,
                color = variant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = variant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 1.224o：页面美化 + 链接接管（只在页面加载完成后注入一次） */
private const val POLISH_JS = """(function(){
  try{
    if(!document.getElementById('huzai-polish')){
      var s=document.createElement('style'); s.id='huzai-polish';
      s.textContent='html,body{background:transparent!important;margin:0!important;padding:0!important;}'
        +'img,video,iframe{max-width:100%!important;height:auto!important;}'
        +'[class*=header],[class*=footer],[class*=navbar],[class*=banner],[class*=advert]'
        +',[class*=download],[id*=download],[class*=app-guide],[class*=openapp]{display:none!important;}';
      (document.head||document.documentElement).appendChild(s);
    }
    if(!window.__huzaiClickHook){
      window.__huzaiClickHook=1;
      document.addEventListener('click',function(e){
        var a=e.target&&e.target.closest?e.target.closest('a'):null;
        if(!a) return;
        var h=a.getAttribute('href')||a.href||'';
        if(!h||h==='#'||h.indexOf('javascript:')===0) return;
        e.preventDefault(); e.stopPropagation();
        try{ HuzaiLink.open(h); }catch(err){}
      },true);
    }
  }catch(err){}
})();"""

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun EmbedWebView(
    url: String,
    onLoaded: () -> Unit,
    /**
     * 1.224o：页面内链接的应用内处理入口。
     * 返回 true = 父级已消费（例如开帖 / 开评分页 / 开用户主页），我们不再管；
     * 返回 false = 父级不处理，则按「网页→就地加载，外链→浏览器」处理。
     */
    onLink: ((HupuLinks.Target) -> Boolean)? = null,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val bgColor = MaterialTheme.colorScheme.background
    // 页面内所有链接统一走这里。注意：**自定义 scheme 绝不再外抛** ——
    // 之前评分/用户类链接落进 startActivity，系统又交给了虎扑官方客户端（真机反馈"还是外抛了"）。
    fun handleLink(u: String, wv: WebView?) {
        val target = HupuLinks.resolve(u)
        if (target == null) {
            // 裸外链（http/https）才交给浏览器；其它 scheme 一律吞掉，不外抛
            if (u.startsWith("http://", true) || u.startsWith("https://", true)) {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(u))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            return
        }
        if (onLink?.invoke(target) == true) return
        when (target) {
            is HupuLinks.Target.Web -> wv?.loadUrl(target.url)
            is HupuLinks.Target.Thread -> wv?.loadUrl("https://bbs.hupu.com/${target.tid}.html")
            // 其余类型父级没接管（理论上不会发生）→ 吞掉，避免外抛到官方客户端
            else -> Unit
        }
    }

    AndroidView(
        modifier = modifier.background(bgColor),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // 该小组件是移动端页面（games.mobileapi.hupu.com），用移动 UA
                settings.userAgentString = HupuApi.MOBILE_UA
                // 背景透明 + 硬件层：避免加载间隙白闪（同登录页 WebView 的处理）
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setLayerType(android.webkit.WebView.LAYER_TYPE_HARDWARE, null)
                // 1.224o：JS 桥上抛「被点中的 href」—— 页面自己的路由在 WebView 里经常是死的，
                // 由我们在原生侧统一决定打开方式（帖子/评分在应用内，其余走浏览器）。
                addJavascriptInterface(
                    object {
                        @android.webkit.JavascriptInterface
                        fun open(u: String) {
                            // 回调发生在 WebView 的 JS 线程 → 切回主线程再操作 UI/Compose 状态
                            this@apply.post { handleLink(u, this@apply) }
                        }
                    },
                    "HuzaiLink",
                )
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        onLoaded()
                        // 1.224o：页面美化（背景透明 / 隐藏它自己的顶栏底栏与引导下载条 / 图片自适应）
                        view?.evaluateJavascript(POLISH_JS, null)
                    }

                    /**
                     * 1.224o：**绝不把自定义 scheme 抛给系统**。
                     *
                     * 官方这些页面（评分详情等）里带 JS，会自己 `location.href = "huputiyu://…"`；
                     * 一旦由 WebView 外抛，系统就把它交给**虎扑官方客户端**。
                     * 这里改为：能还原成网页的就地加载 / 帖子交回父级原生打开，其余吞掉。
                     */
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean = interceptNonHttp(request?.url?.toString(), view)

                    @Deprecated("兼容只走旧回调的页面/机型")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                        interceptNonHttp(url, view)

                    /** 返回 true = 由我们消费（不外抛）；false = 让 WebView 继续按网页加载 */
                    fun interceptNonHttp(url: String?, view: WebView?): Boolean {
                        val u = url ?: return false
                        if (u.startsWith("http://", true) || u.startsWith("https://", true)) return false
                        handleLink(u, view)
                        return true
                    }
                }
                loadUrl(url)
            }
        },
        onRelease = { it.destroy() },
    )
}
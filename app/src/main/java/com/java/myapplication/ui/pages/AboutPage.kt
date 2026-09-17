package com.java.myapplication.ui.pages

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.java.myapplication.BuildConfig
import com.java.myapplication.R
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Dialog
import com.java.myapplication.ui.components.HupuIcons
import com.java.myapplication.ui.components.SecondaryPage
import com.java.myapplication.ui.components.tapGuard
import com.java.myapplication.data.HupuUpdate
import com.java.myapplication.data.HupuUpdateInfo
import com.java.myapplication.data.HupuUpdateResult
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 1.175 开源仓库地址（关于页「开发人员」与「开源仓库」快捷入口跳转目标）。 */
private const val HUPU_REPO = "https://github.com/Kanezikiiu/Huzai"

private enum class AboutSub(val title: String) {
    PRIVACY("用户隐私与协议"),
    LICENSE("开源许可"),
    DISCLAIMER("免责声明"),
    CRASH("崩溃日志"),
}

/** 读取崩溃取证文件（内部私有目录优先，其次外部专属目录）；不存在返回空串。 */
private fun readCrashLog(context: Context): String {
    val dirs = listOfNotNull(context.filesDir, context.getExternalFilesDir(null))
    for (d in dirs) {
        val f = java.io.File(d, "crash_last.txt")
        if (f.exists()) return runCatching { f.readText() }.getOrDefault("")
    }
    return ""
}

/**
 * 1.134 关于页（盖入式二级页）：应用图标/名称/版本/简介 + 三条快捷 + 协议/许可/免责/日志列表。
 * 参考样式：图标带主题色圆环、版本胶囊、三枚圆形按钮、圆角分组列表（行间细分割线、无行图标）。
 */
@Composable
fun AboutPage(onClose: () -> Unit) {
    val context = LocalContext.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        SecondaryPage.enter()
        progress.animateTo(1f, tween(280))
    }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(closing) {
        if (closing) {
            SecondaryPage.exit()
            progress.animateTo(0f, tween(280))
            onClose()
        }
    }
    // 子页打开时，返回手势交给子页处理（子页后组合，PredictiveBackHandler 优先）
    var sub by remember { mutableStateOf<AboutSub?>(null) }
    PredictiveBackHandler { events ->
        if (closing || sub != null) {
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

    var toast by remember { mutableStateOf<String?>(null) }
    // 1.177: 检查更新（手动触发）
    val scope = rememberCoroutineScope()
    var updateChecking by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<HupuUpdateInfo?>(null) }
    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(2000)
            toast = null
        }
    }

    // 保存日志：SAF 让用户选择保存位置（无需任何存储权限）
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val log = readCrashLog(context)
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(log.toByteArray()) }
            }.isSuccess
            toast = if (ok) "日志已保存" else "保存失败"
        }
    }
    fun saveLog() {
        if (readCrashLog(context).isBlank()) {
            toast = "暂无日志可保存"
            return
        }
        val name = "huzai_log_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".txt"
        saveLauncher.launch(name)
    }
    fun openUrl(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { toast = "无法打开链接" }
    }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(2f)
            .graphicsLayer { translationX = (1f - progress.value) * size.width }
            .background(MaterialTheme.colorScheme.background)
            .tapGuard(),
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // 顶栏
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
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
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "关于",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // 头部：图标（主题色圆环） + 名称 + 版本胶囊 + 简介 + 三枚圆形按钮
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 应用图标：不用 painterResource 直接解码 mipmap——ic_launcher_round 在
                // API26+ 会命中 mipmap-anydpi-v26 的 <adaptive-icon>，painterResource 无法
                // 解码自适应图标 XML（"Only VectorDrawables and rasterized asset types…"），
                // 组合期抛异常导致打开即闪退。这里统一把 Drawable 绘制成位图，自适应/位图
                // 两种形态都安全；失败则回退到主题色字母块。
                val appIcon = remember(context) {
                    runCatching {
                        ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round)
                            ?.toBitmap(width = 192, height = 192)
                            ?.asImageBitmap()
                    }.getOrNull()
                }
                Box(
                    Modifier
                        .size(98.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon,
                            contentDescription = "应用图标",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "虎",
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "虎仔",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "v" + BuildConfig.VERSION_NAME,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "虎扑网页版第三方客户端",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircleIconButton(HupuIcons.Globe, "开源仓库") { openUrl(HUPU_REPO) }
                    CircleIconButton(HupuIcons.Code, "开源许可") { sub = AboutSub.LICENSE }
                    CircleIconButton(HupuIcons.CloudDownload, "检查更新") {
                        // 1.177: 拉取 version.json 比对 versionCode（手动触发，失败静默提示）
                        if (!updateChecking) {
                            updateChecking = true
                            scope.launch {
                                when (val r = HupuUpdate.check(BuildConfig.VERSION_CODE)) {
                                    is HupuUpdateResult.Available -> updateInfo = r.info
                                    HupuUpdateResult.Latest ->
                                        toast = "当前已是最新版本 v" + BuildConfig.VERSION_NAME
                                    HupuUpdateResult.Failed ->
                                        toast = "检查更新失败，请稍后重试"
                                }
                                updateChecking = false
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // 列表分组
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    // 开发人员：点击跳转开源仓库（1.175）
                    AboutRow("开发人员") { openUrl(HUPU_REPO) }
                    AboutDivider()
                    AboutRow("用户隐私与协议") { sub = AboutSub.PRIVACY }
                    AboutDivider()
                    AboutRow("开源许可") { sub = AboutSub.LICENSE }
                    AboutDivider()
                    AboutRow("免责声明") { sub = AboutSub.DISCLAIMER }
                    AboutDivider()
                    AboutRow("崩溃日志") { sub = AboutSub.CRASH }
                    AboutDivider()
                    AboutRow("保存日志") { saveLog() }
                }
                Spacer(Modifier.height(28.dp))
            }
        }

        // 子页（三级页）：盖在关于页之上
        sub?.let { s -> AboutSubPage(s, context, onClose = { sub = null }) }

        // 顶部提示
        toast?.let { msg ->
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)) {
                Text(
                    msg,
                    fontSize = 13.sp,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xE6303030))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        // 1.177: 发现新版本对话框
        updateInfo?.let { info ->
            UpdateDialog(
                info = info,
                onDismiss = { updateInfo = null },
                onDownload = {
                    openUrl(info.apkUrl.ifEmpty { info.releaseUrl })
                    updateInfo = null
                },
            )
        }
    }
}

/**
 * 1.177/1.179: 检查更新弹窗（关于页手动检查 + 打开软件自动检查共用）（iOS 风格：大圆角卡片、标题/正文居左、取消浅灰 + 确认蓝色大圆角按钮）。
 */
@Composable
fun UpdateDialog(
    info: HupuUpdateInfo,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
        ) {
            Text(
                "发现新版本 v" + info.versionName,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "当前版本 v" + BuildConfig.VERSION_NAME,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (info.changelog.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    info.changelog,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogAction("稍后", primary = false, modifier = Modifier.weight(1f), onClick = onDismiss)
                DialogAction("去下载", primary = true, modifier = Modifier.weight(1f), onClick = onDownload)
            }
        }
    }
}

@Composable
private fun DialogAction(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (primary) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 15.sp,
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal,
            color = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CircleIconButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = desc,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun AboutRow(title: String, onClick: () -> Unit) {
    Text(
        title,
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    )
}

@Composable
private fun AboutDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
    )
}

/** 关于页内的三级子页：盖入式，返回逐层弹出。 */
@Composable
private fun AboutSubPage(kind: AboutSub, context: Context, onClose: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        SecondaryPage.enter()
        progress.animateTo(1f, tween(280))
    }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(closing) {
        if (closing) {
            SecondaryPage.exit()
            progress.animateTo(0f, tween(280))
            onClose()
        }
    }
    PredictiveBackHandler { events ->
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
    val body = remember(kind) { subBody(kind, context) }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(3f)
            .graphicsLayer { translationX = (1f - progress.value) * size.width }
            .background(MaterialTheme.colorScheme.background)
            .tapGuard(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
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
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    kind.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            ) {
                // 段落化渲染：按空行分段，段间给统一间距（避免一段话被硬折行拆成好几段的观感）
                val paragraphs = remember(body) {
                    body.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
                }
                paragraphs.forEachIndexed { i, para ->
                    if (i > 0) Spacer(Modifier.height(14.dp))
                    Text(
                        para,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun subBody(kind: AboutSub, context: Context): String = when (kind) {
    AboutSub.PRIVACY -> PRIVACY_TEXT
    AboutSub.DISCLAIMER -> DISCLAIMER_TEXT
    AboutSub.CRASH -> {
        val log = readCrashLog(context)
        if (log.isBlank()) "暂无崩溃日志。\n\n崩溃取证仅在 debug 构建启用。" else log
    }
    AboutSub.LICENSE -> LICENSE_NOTICE + "\n\n" + THIRD_PARTY_NOTICE + "\n\n" +
        "────────────\n\n完整许可证文本（GNU GPLv3）\n\n" +
        runCatching {
            context.assets.open("gpl-3.0.txt").bufferedReader().use { it.readText() }
        }.getOrDefault("（未能加载 GPLv3 文本，请访问 https://www.gnu.org/licenses/gpl-3.0.txt）")
}

private const val PRIVACY_TEXT = """
一、应用性质

本应用（虎仔）是虎扑网页版的第三方客户端，非虎扑官方应用，与虎扑官方无任何隶属或合作关系。

二、我们收集的信息

本应用没有自建服务器，不收集、不上传你的个人信息。以下数据全部保存在你的设备本地：
· 登录凭据：你在应用内通过虎扑官方网页完成登录，登录 Cookie 仅保存在本机应用私有存储，用于以你的身份访问虎扑接口；退出登录即清除。应用全程不接触你的账号密码。
· 使用偏好：阅读字号、主题模式、频道自定义、关键词过滤等，保存在本机。
· 浏览记录、本地关注集合等，保存在本机。

三、网络请求

应用会直接访问虎扑的公开网页与接口以获取内容，这些请求由你的设备发起。相关数据在虎扑服务器上的处理，适用虎扑平台的规则与政策，本应用无法控制。

四、第三方组件

应用使用了若干开源组件（见「开源许可」），它们不会额外收集你的个人信息。

五、你的选择

你可在「我的」页面随时退出登录以清除本机登录凭据；卸载应用会删除全部本地数据。

使用本应用即表示你已阅读并理解上述说明。
"""

internal const val DISCLAIMER_TEXT = """虎仔（Huzai）免责声明

最后更新：随应用版本发布，请以本页最新内容为准。

────────────────────

一、非官方声明

1. 本应用是虎扑网页版的第三方非官方客户端，由个人开发者出于技术学习目的独立开发，与虎扑（上海）文化传播股份有限公司及其关联公司、网站（含 hupu.com、m.hupu.com、bbs.hupu.com 等）无任何隶属、代理、合作、赞助或授权关系，亦未获得其认可、支持或背书。

2. 应用中提及的「虎扑」及相关的名称、商标、标识、图标、域名等，权利均归其各自权利人所有。本应用使用这些称谓仅出于客观描述数据来源之目的，不构成任何形式的权利主张或授权暗示。

3. 本应用不冒充、不代表、不代替虎扑官方应用，不以虎扑官方名义从事任何活动。

二、内容与权利归属

4. 应用内展示的全部内容，包括但不限于帖子正文与回复、评论与楼中楼、用户头像与昵称、图片、评分与榜单、赛事与赛程数据、话题与专区信息等，其著作权、商标权及其他相关权利均归虎扑及相应原作者、原始权利人所有。

5. 本应用仅以技术手段请求并呈现来自虎扑公开网页与公开接口的信息，不对内容进行再创作、汇编发行或商业利用，也不主张对上述内容的任何权利。

6. 内容的真实性、准确性、完整性、时效性与合法性由内容发布者与原始平台负责，本应用无法核实、编辑或担保。请以虎扑官方渠道发布的信息为准。

三、使用范围与禁止行为

7. 本应用仅供个人学习、技术研究与日常交流使用，严禁用于任何商业用途，包括但不限于商业运营、广告变现、付费服务、二次分发、打包出售或以本应用为基础提供商业服务。

8. 严禁利用本应用进行下列行为：高频批量抓取或爬取数据、压力测试或干扰虎扑服务的正常运行、绕过或破坏其访问控制与风控机制、批量注册或批量发布、任何形式的自动化刷量（刷评、刷亮、刷分等）、侵害他人合法权益的其他行为。

9. 使用者应自行确保其使用行为符合所在国家或地区的法律法规，以及虎扑平台的用户协议与社区规范。因使用本应用而产生的任何法律后果，由使用者自行承担。

四、账号与登录风险

10. 本应用通过内嵌虎扑官方网页完成登录，登录 Cookie 仅保存在本机应用私有存储中，用于以使用者本人的身份访问虎扑接口；应用不接触、不存储使用者的账号与密码，也不将其上传至任何第三方服务器。

11. 需要特别提示：使用第三方客户端访问或操作虎扑账号，可能触发虎扑平台的风控策略，存在账号被限制功能、被临时封禁或被永久封禁的风险。该风险由使用者自行评估与承担，开发者不对账号状态、积分、内容或数据的任何损失负责。

12. 使用者在应用内进行的发布、回复、点赞（点亮）、推荐、打分、收藏等操作，均以其本人虎扑账号的真实身份生效，由使用者本人对操作内容与后果负责。

五、服务可用性

13. 本应用依赖虎扑的公开网页结构与接口，这些内容并非面向第三方客户端的稳定公共 API，可能随时在无预告的情况下变更、加密、限制访问或停止提供；因此应用的相应功能可能随时失效、显示异常或不可用。

14. 本应用按「现状（AS IS）」与「现有（AS AVAILABLE）」提供，开发者不承诺功能持续可用、不承诺数据实时或准确、不承诺缺陷会被修复，也不承诺提供长期维护、更新或技术支持。

15. 开发者有权在不预先通知的情况下调整、暂停或终止本应用的开发与发布。

六、免责与责任限制

16. 在适用法律允许的最大范围内，开发者不对使用或无法使用本应用所导致的任何直接、间接、附带、特殊或后果性损失承担责任，包括但不限于数据丢失、内容丢失、账号损失、设备损坏、流量费用、时间损失或预期利益损失。

17. 本应用不对任何第三方内容的合法性、安全性、准确性作出担保；使用者因信任应用内展示的内容而作出的任何判断或行为，后果自负。

18. 使用者应自行承担使用本应用产生的网络流量费用与设备资源消耗。

七、隐私

19. 本应用没有自建服务器，不收集、不上传使用者的个人信息。相关说明详见《用户隐私与协议》。

八、侵权处理与联系

20. 若任何权利人认为本应用的存在或某项功能侵犯了其合法权益（包括著作权、商标权等），请通过项目仓库的 Issue 或其他公开渠道联系开发者，并提供必要的权属证明与具体说明。开发者将在核实后及时采取删除、屏蔽、调整或下架等必要措施。

21. 若本应用的部分内容或功能被认定存在问题，开发者愿意以善意方式沟通解决，而非对抗。

九、其他

22. 本应用以 GNU General Public License v3.0 发布，源码可获取；许可条款详见「开源许可」。

23. 使用者开始使用本应用，即视为已阅读、理解并同意本免责声明的全部内容。若不同意其中任何条款，请立即停止使用并卸载本应用。

24. 本声明可能随应用版本更新而调整，调整后的内容自发布之时起生效。"""

private const val LICENSE_NOTICE = """
虎仔（Huzai）以 GNU General Public License v3.0（GPLv3）发布，遵循自由软件精神。

本程序是自由软件：你可以依据自由软件基金会发布的 GNU 通用公共许可证（第 3 版或你选择的任何更高版本）条款，重新发布和/或修改它。

本程序的分发是希望它有用，但不提供任何担保，甚至没有对适销性或特定用途适用性的默示担保。详见 GNU 通用公共许可证。

你应当已随本程序收到一份 GNU 通用公共许可证副本；若没有，请访问 https://www.gnu.org/licenses/。

源代码获取：完整源码随本应用一并提供（见项目仓库/LICENSE）。
"""

private const val THIRD_PARTY_NOTICE = """
本应用使用了以下开源项目，感谢它们的作者：

· AndroidX / Jetpack Compose / Material3 —— Apache License 2.0
· Kotlin 标准库与 kotlinx.coroutines —— Apache License 2.0
· OkHttp —— Apache License 2.0
· Coil —— Apache License 2.0
· AndroidX Media3 (ExoPlayer) —— Apache License 2.0
· kyant backdrop / capsule / shapes —— Apache License 2.0

以上组件的完整许可文本随其发布页提供。
"""

/** 供「关于」页复用：版本展示文案。 */
internal fun aboutVersionLabel(): String = "v" + BuildConfig.VERSION_NAME

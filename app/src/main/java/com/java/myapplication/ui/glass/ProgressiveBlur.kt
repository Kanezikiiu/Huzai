package com.java.myapplication.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.java.myapplication.data.HupuPrefs
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect

/**
 * 1.198：**顶栏渐进模糊**（progressive blur）—— 移植自官方示例
 * `catalog/destinations/ProgressiveBlurContent.kt`（Kyant0/AndroidLiquidGlass）。
 *
 * 要解决的问题（真机反馈：「顶栏不透明，上去的条目被死死挡住」）：
 * 原先顶栏坐在页面流里，可滚动内容是**在顶栏下缘被硬切一刀**——条目走到那里直接消失。
 * 现在顶栏浮在内容之上、自身背景换成这块渐进模糊：内容滚到栏下时**越靠上越模糊、
 * 并逐渐被页面底色压住**，于是「消失」变成「渐隐没入玻璃」，与 iOS 导航栏一致。
 *
 * 着色器的衰减曲线（1.198f 定稿）：
 *   1.198d 用官方曲线 `smoothstep(size.y, size.y*0.5, coord.y)` —— 下半部几乎全透，
 *     话题条/排序条坐在那里，chips 与背后滚动的内容糊在一起，可读性极差；
 *   1.198e 改成「满强度到接近底边、只在 16dp 窄带羽化」—— 可读性好了，
 *     但真机反馈「直接满强度缺少渐进模糊的质感」；
 *   1.198f 采用「**栏位高度的 70% 作为衰减行程**」：
 *     `alpha = smoothstep(0, 0.7 * size.y, size.y - coord.y)`
 *   即最上 30% 满强度、向下平滑衰减到 0（比官方缓得多，整条栏仍有底色，
 *   底边依旧没有硬线），保留质感；**同时**由 [chipBarGlassBoost] 把
 *   话题条/排序条这些玻璃胶囊自身的不透明度提上去，保证 chips 可读。
 *
 * @param backdrop 采样源：**内容层**（必须是本组件的兄弟节点打出来的层，见下）
 * @param tint 压在上面的底色，默认页面背景色（深色主题自动跟随）
 * @param tintIntensity 底色强度：0 = 只模糊不压色，1 = 完全变成纯色
 * @param fadeBandFraction 衰减行程占栏位高度的比例：越小越接近实心导航栏，越大越「渐隐」
 */
@Composable
internal fun ProgressiveBlurBackground(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.background,
    tintIntensity: Float = 0.85f,
    fadeBandFraction: Float = 0.7f,
) {
    Box(
        modifier.drawPlainBackdrop(
            backdrop = backdrop,
            shape = { RectangleShape },
            effects = {
                // 模糊取 8dp（官方 demo 是 4dp）：栏位下半部仍有 15% 的内容透出来，
                // 糊得更狠一点才能让底下的 chips 不被内容「抢」走可读性
                blur(8f.dp.toPx())
                runtimeShaderEffect("AlphaMask", ALPHA_MASK_SHADER, "content") {
                    setFloatUniform("size", size.width, size.height)
                    setColorUniform("tint", tint)
                    setFloatUniform("tintIntensity", tintIntensity)
                    setFloatUniform("fadeBand", size.height * fadeBandFraction)
                }
            },
        ),
    )
}

/** AlphaMask 着色器（曲线见上注：整条栏位都有底色，只在底边收干净） */
private const val ALPHA_MASK_SHADER = """
uniform shader content;

uniform float2 size; 
layout(color) uniform half4 tint;
uniform float tintIntensity;
uniform float fadeBand;

half4 main(float2 coord) {
    float distToBottom = max(size.y - coord.y, 0.0);
    float alpha = smoothstep(0.0, fadeBand, distToBottom);
    return mix(content.eval(coord) * alpha, tint * alpha, tintIntensity);
}
"""

/** 1.198：本组件所在页面是否为「当前 Tab」。
 *  四个主页常驻组合，只有当前 Tab 才值得真正开模糊（否则要同时养四层全屏离屏层）。
 *  非 Tab 场景（二级页）默认 true。 */
internal val LocalTabActive = compositionLocalOf { true }

/**
 * 1.198：**浮空顶栏 + 内容滚动到其下方**的统一骨架（四个主页 / 二级页共用）。
 *
 * 结构（关键是「顶栏与内容层互为兄弟」）：
 * ```
 * Box
 * ├── Box(.layerBackdrop(bd)) ← 内容层：全屏，列表用 topInset 让首项从栏下开始
 * │   └── content(topInset)
 * └── Box(.onSizeChanged{ headerPx = it.height }) ← 顶栏：先画背景（模糊 or 底色），再画自身内容
 *     ├── ProgressiveBlurBackground(bd) / 不透明底色
 *     └── Column { header() }
 * ```
 *
 * · 采样安全：顶栏是内容层的**兄弟节点**，先记录、后采样 —— 不会出现
 *   「层读取自己」的自采样递归（本项目为此崩过一次，见 GlassSwitch 注释）。
 * · 栏高自测量：`header()` 的真实高度回灌给 `content` 作为 `topInset`，
 *   所以排序条「有/无」「借用/就位」导致的高度变化都会被自动跟上，不需要写死常量。
 * · **开关只切背景**：开启/关闭共用同一套布局，只有顶栏背景在「渐进模糊」与「不透明页面底色」
 *   之间切换 —— 关闭时的观感与改造前一致（内容同样从栏下开始，多出的那截被底色盖住）。
 *   之所以不切布局：四个主页常驻组合，一旦切结构就是四棵子树同时重排，真机上就是
 *   「点一下开关卡一下」（1.198 真机反馈）。
 *
 * @param header 顶栏内容（标题行 / 话题条 / 排序条……），本体不用管背景
 * @param content 页面内容（列表）；**必须**把 [Dp] 作为顶部内边距，否则首项会被栏盖住
 */
@Composable
internal fun FrostedHeaderLayout(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    content: @Composable (topInset: Dp) -> Unit,
) {
    val optionVersion = HupuPrefs.progressiveHeaderVersion
    val enabled = remember(optionVersion) { HupuPrefs.loadProgressiveHeader() }
    // 只有「开启 且 本页是当前 Tab」时才真的挂记录层 + 跑着色器；否则零额外开销
    val active = LocalTabActive.current
    val useBlur = enabled && active

    val density = LocalDensity.current
    val backdrop = rememberLayerBackdrop()
    var headerPx by remember { mutableIntStateOf(0) }
    val topInset = with(density) { headerPx.toDp() }

    Box(modifier) {
        // ① 内容层：全屏，供顶栏采样（顶栏是它的兄弟节点 → 不会自采样）
        Box(
            Modifier
                .fillMaxSize()
                .then(if (useBlur) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            content(topInset)
        }
        // ② 顶栏：背景 + 自身内容
        Box(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { headerPx = it.height },
        ) {
            if (useBlur) {
                Box(Modifier.matchParentSize()) {
                    ProgressiveBlurBackground(
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                // 关闭（或本页不是当前 Tab）= 原有观感：顶栏位置铺不透明页面底色。
                // 内容同样从栏下开始，只是「从栏下穿过去」的那一截被这层底色盖住，
                // 与改造前的「栏在流里、内容在栏下缘截断」在视觉上等价。
                Box(
                    Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.background),
                )
            }
            // 必须用 Column 包住：header 里通常是「标题栏 + 话题条 + 排序条」多个组件，
            // 直接放进 Box 会变成互相重叠的兄弟节点（真机表现为三行糊在一起）。
            Column { header() }
        }
    }
}
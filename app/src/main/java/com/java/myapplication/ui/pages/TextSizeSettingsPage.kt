package com.java.myapplication.ui.pages

import androidx.compose.foundation.background
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.ui.components.SecondaryPage
import com.java.myapplication.ui.components.tapGuard
import kotlin.coroutines.cancellation.CancellationException

/** \u9605\u8bfb\u5b57\u53f7\u8bbe\u7f6e\u9875\uff08\u76d6\u5165\u5f0f\u4e8c\u7ea7\u9875\uff09\uff1a\u793a\u4f8b\u5b9e\u65f6\u9884\u89c8 + \u6ed1\u6761\u8c03\u8282\uff0c\u4ec5\u5e94\u7528\u4e8e\u5e16\u5b50\u8be6\u60c5\u6b63\u6587\u533a\u3002 */
@Composable
fun TextSizeSettingsPage(onClose: () -> Unit) {
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
    var scale by remember { mutableFloatStateOf(HupuPrefs.loadFontScale()) }
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(2f)
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
                        contentDescription = "\u8fd4\u56de",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "\u9605\u8bfb\u5b57\u53f7",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // \u5b9e\u65f6\u9884\u89c8\u5361\uff1a\u6309\u5f53\u524d\u7f29\u653e\u6e32\u67d3\u6837\u4f8b
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "\u793a\u4f8b\uff1a\u8fd9\u662f\u5e16\u5b50\u6807\u9898",
                        fontSize = (17 * scale).sp,
                        lineHeight = (24 * scale).sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size((36 * scale).dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "\u732a",
                                fontSize = (13 * scale).sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "\u732a\u732a\u4fa0 GGBond",
                            fontSize = (13 * scale).sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "10\u5206\u949f\u524d",
                            fontSize = (12 * scale).sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "\u8fd9\u662f\u6b63\u6587\u793a\u4f8b\u6587\u5b57\u3002\u62d6\u52a8\u4e0b\u65b9\u6ed1\u5757\u5b9e\u65f6\u9884\u89c8\uff0c\u677e\u624b\u540e\u81ea\u52a8\u4fdd\u5b58\uff0c\u4ec5\u5e94\u7528\u4e8e\u5e16\u5b50\u8be6\u60c5\u9875\u3002",
                        fontSize = (15 * scale).sp,
                        lineHeight = (24 * scale).sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("12\u697c", fontSize = (11 * scale).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "\u4eae 56",
                            fontSize = (12 * scale).sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // \u6ed1\u6761\u5361
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "\u5c0f",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            (scale * 100).toInt().toString() + "%",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "\u5927",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Slider(
                        value = scale,
                        onValueChange = { scale = it },
                        onValueChangeFinished = { HupuPrefs.saveFontScale(scale) },
                        valueRange = HupuPrefs.FONT_SCALE_MIN..HupuPrefs.FONT_SCALE_MAX,
                    )
                    Row {
                        Spacer(Modifier.weight(1f))
                        Text(
                            "\u6062\u590d\u9ed8\u8ba4",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    scale = 1.0f
                                    HupuPrefs.saveFontScale(1.0f)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
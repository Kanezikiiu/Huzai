package com.java.myapplication.ui.pages

import android.net.Uri
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.java.myapplication.data.HupuAccount
import com.java.myapplication.data.HupuBlocks
import com.java.myapplication.data.HupuDraft
import com.java.myapplication.data.HupuImage
import com.java.myapplication.data.HupuPostApi
import com.java.myapplication.ui.components.HupuIcons
import com.java.myapplication.ui.components.SecondaryPage
import com.java.myapplication.ui.components.tapGuard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** 取 content Uri 的显示名（文件名），失败返回空串 */
internal fun queryDisplayName(ctx: android.content.Context, uri: Uri): String {
    return try {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: ""
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) {
                val v = c.getString(idx)
                if (!v.isNullOrBlank()) name = v
            }
        }
        name
    } catch (e: Exception) {
        ""
    }
}

/** 1.158: 图片上传结果。url 失败为 null；animatedWebp 供发送后提示官方端不支持 */
internal data class UploadedImage(val url: String?, val animatedWebp: Boolean = false)

/** 读取 + 上传一张图片，返回图床 URL（失败 null） */
internal suspend fun uploadImage(ctx: android.content.Context, uri: Uri): UploadedImage {
    return try {
        // 1.170: 带上限读取（>32MB 或读取失败 → null）
        val bytes = HupuImage.readCapped(ctx.contentResolver, uri)
        if (bytes == null) return UploadedImage(null)
        // 1.157: 统一走魔数嗅探（GIF/动画 WebP 原样上传）
        val img = HupuImage.prepareForUpload(
            bytes,
            ctx.contentResolver.getType(uri) ?: "",
            uri.lastPathSegment ?: "",
        ) ?: return UploadedImage(null)
        val url = HupuAccount.uploadReplyImage(img.bytes, img.ext, img.width, img.height).url
        UploadedImage(url, HupuImage.isAnimatedWebp(img.bytes))
    } catch (e: Exception) {
        UploadedImage(null)
    }
}

/**
 * 1.113: 正文里的投票块卡片（编辑器内展示，仅单选）。
 * 展示投票标题与选项；voteId 沿用既有值（编辑已有投票帖不会重建投票）。
 */
/**
 * 1.118: 附件缩略图（省空间的方形小图，右下角序号与正文〔图片N〕标签一一对应）。
 */
@Composable
internal fun AttachmentThumb(url: String, index: Int, onRemove: () -> Unit) {
    Box(
        Modifier
            .size(76.dp)
            .clip(RoundedCornerShape(10.dp)),
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color(0x99000000))
                .clickable { onRemove() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "移除图片",
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
        }
        if (index > 0) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(Color(0xB3000000))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text("$index", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

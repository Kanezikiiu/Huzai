package com.java.myapplication.ui.pages

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.java.myapplication.ui.theme.CategoryColors

@Composable
fun StatsPage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text("统计", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        // 月度趋势占位（Canvas 柱状图）
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            BarChart(
                values = listOf(0.4f, 0.7f, 0.3f, 0.9f, 0.5f, 0.8f, 0.6f),
                barColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(16.dp))

        // 分类占比
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column {
                Text("本月分类占比", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PieChart(
                        slices = listOf(0.35f, 0.25f, 0.2f, 0.12f, 0.08f),
                        modifier = Modifier.size(120.dp)
                    )
                    Spacer(Modifier.width(20.dp))
                    Column {
                        listOf("餐饮 35%", "购物 25%", "交通 20%", "娱乐 12%", "其他 8%").forEachIndexed { i, label ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .background(CategoryColors[i], CircleShape)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun BarChart(values: List<Float>, barColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val gap = size.width / (values.size * 2)
        val barWidth = gap
        values.forEachIndexed { i, v ->
            val h = size.height * v
            drawRoundRect(
                color = barColor.copy(alpha = 0.25f + 0.75f * v),
                topLeft = Offset(gap + i * gap * 2, size.height - h),
                size = Size(barWidth, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2)
            )
        }
    }
}

@Composable
private fun PieChart(slices: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        var startAngle = -90f
        slices.forEachIndexed { i, v ->
            val sweep = v * 360f
            drawArc(
                color = CategoryColors[i % CategoryColors.size],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = true
            )
            startAngle += sweep
        }
    }
}
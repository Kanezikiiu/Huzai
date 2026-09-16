package com.java.myapplication.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.java.myapplication.ui.theme.ExpenseRed
import com.java.myapplication.ui.theme.IncomeGreen

private data class DemoTx(val title: String, val category: String, val amount: String, val income: Boolean)

private val demoList = listOf(
    DemoTx("午餐-公司食堂", "餐饮", "-¥15.00", false),
    DemoTx("地铁通勤", "交通", "-¥4.00", false),
    DemoTx("奶茶", "餐饮", "-¥18.50", false),
    DemoTx("兼职工资", "收入", "+¥200.00", true),
    DemoTx("超市采购", "购物", "-¥86.20", false),
    DemoTx("电影票", "娱乐", "-¥45.00", false)
)

@Composable
fun DetailPage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text("明细", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        LazyColumn {
            items(demoList) { tx ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .width(44.dp)
                            .height(44.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tx.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(tx.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        tx.amount,
                        color = if (tx.income) IncomeGreen else ExpenseRed,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            item { Spacer(Modifier.height(120.dp)) }
        }
    }
}
package de.wortschatz.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wortschatz.app.AppViewModel
import de.wortschatz.app.ReviewDay
import de.wortschatz.app.data.WordWithProgress

/**
 * 复习安排：把未来的复习任务按天摊开。
 *
 * 数据来自遗忘曲线算出的 due 时间，逾期未复习的并入「今天」——
 * 对用户来说它们此刻就是要补的，单独列一个「逾期」区反而要先想一下才明白。
 */
@Composable
fun ReviewPlanScreen(vm: AppViewModel, onBack: () -> Unit) {
    BackHandler { onBack() }
    LaunchedEffect(Unit) { vm.loadReviewPlan() }

    val plan = vm.reviewPlan
    val total = plan.sumOf { it.count }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(21.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "复习安排",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (total == 0) "接下来 ${AppViewModel.REVIEW_PLAN_DAYS} 天没有复习任务"
                        else "接下来 ${AppViewModel.REVIEW_PLAN_DAYS} 天共 $total 个词",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
            }

            if (plan.isEmpty()) {
                EmptyPlan()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)
                ) {
                    plan.forEach { day ->
                        item(key = "day-${day.daysFromToday}") {
                            DayHeader(day)
                        }
                        items(day.words, key = { "${day.daysFromToday}-${it.word.id}" }) { item ->
                            PlanWordRow(item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: ReviewDay) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = day.title,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            // 今天用主色，一眼看出「这批现在就得复习」
            color = if (day.daysFromToday == 0) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = day.dateText,
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "${day.count} 个",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlanWordRow(item: WordWithProgress) {
    val word = item.word
    val progress = item.progress
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = word.word,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = word.zh,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LevelBadge(word.level)
                Text(
                    // state 1 = 学习中（还没走完一轮，间隔为 0），state 2 = 复习中
                    text = when {
                        progress.state >= 2 && progress.interval > 0 -> "隔 ${progress.interval} 天"
                        progress.state >= 2 -> "复习中"
                        else -> "学习中"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun EmptyPlan() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "暂无复习安排",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "学过的词会按遗忘曲线自动排进这里，到期才会出现",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

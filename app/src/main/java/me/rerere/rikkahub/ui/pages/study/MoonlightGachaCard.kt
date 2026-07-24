package me.rerere.rikkahub.ui.pages.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.reflect.KFunction0
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.study.StudyRules
import me.rerere.rikkahub.data.study.StudyState

/**
 * Callable-reference overload selected by the existing vm::drawPurpleTicket call.
 * This keeps the oversized StudyPage.kt untouched while replacing its gacha skin.
 */
@Composable
internal fun GachaCard(
    state: StudyState,
    onSingle: () -> Unit,
    onTen: () -> Unit,
    onPurple: KFunction0<Unit>,
) {
    val singleCost = if (StudyRules.hasSingleDrawDiscount(state)) {
        StudyRules.DISCOUNT_SINGLE_DRAW_COST
    } else {
        StudyRules.SINGLE_DRAW_COST
    }
    val ticketDraws = state.wallet.singleDrawTickets + state.wallet.tenDrawTickets * 10
    val pityUsed = state.drawsSinceNonNormal
        .coerceIn(0, StudyRules.NON_NORMAL_PITY_DRAW_COUNT - 1)
    val pityRemaining = StudyRules.NON_NORMAL_PITY_DRAW_COUNT - pityUsed
    val pityProgress = pityUsed.toFloat() / StudyRules.NON_NORMAL_PITY_DRAW_COUNT

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17132F)),
        border = BorderStroke(1.dp, Color(0x66E9DCFF)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(840.dp)
                .clip(RoundedCornerShape(30.dp)),
        ) {
            MoonlightBackdrop(Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MoonlightPill("✦ 常驻卡池", Color(0xFFD7BBFF))
                    Spacer(Modifier.weight(1f))
                    Text("券可抽 $ticketDraws 次", color = Color.White.copy(alpha = 0.78f))
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("❀ 当前夸夸值", color = Color(0xFFECE3FF))
                        Spacer(Modifier.weight(1f))
                        Text(
                            state.wallet.kudos.toString(),
                            color = Color(0xFFFFE6AA),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("✦", color = Color(0xFFFFE6AA))
                    }
                }

                Text(
                    "月光花匣",
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFFF5FF),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "把今天认真学习的心意，藏进会发光的花瓣里。",
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFE8DFF5),
                    textAlign = TextAlign.Center,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MoonlightPill("紫 4.8%", Color(0xFFD2AEFF), Modifier.weight(1f))
                    MoonlightPill("金 1.0%", Color(0xFFFFD98C), Modifier.weight(1f))
                    MoonlightPill("彩 0.2%", Color(0xFFFFC9EE), Modifier.weight(1f))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(226.dp)
                        .clip(RoundedCornerShape(24.dp)),
                ) {
                    Image(
                        painter = painterResource(R.drawable.moonlight_flower_box),
                        contentDescription = "月光花匣",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.72f to Color.Transparent,
                                    1f to Color(0xB0191434),
                                ),
                            ),
                    )
                    MoonlightPill(
                        text = "每一次抽卡，都是你努力的回响",
                        color = Color(0xFFF2DFFF),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InventoryPill("抖音20分", state.inventory.douyinFragments, Color(0xFFD2AEFF))
                    InventoryPill("剧场", state.inventory.theaterFragments, Color(0xFFE4C0FF))
                    InventoryPill("游戏120分", state.inventory.gameFragments, Color(0xFFFFD98C))
                    InventoryPill("视频卡", state.inventory.videoFragments, Color(0xFFFFE4AE))
                    InventoryPill("番剧3小时", state.inventory.animeFragments, Color(0xFFFFC9EE))
                }

                Text(
                    "紫色：抖音20分钟 4.0% / 剧场碎片 0.8% · 金色 1.0% · 彩色 0.2%",
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White.copy(alpha = 0.68f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(17.dp),
                    color = Color.White.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, Color(0x55E8D9FF)),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("特殊奖励保底", color = Color(0xFFF4ECFF), fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            Text("还差 $pityRemaining 抽", color = Color(0xFFFFE2A5))
                        }
                        LinearProgressIndicator(
                            progress = { pityProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "连续全蓝第30抽保底紫色；紫、金、彩任一出现后重新计算。",
                            color = Color.White.copy(alpha = 0.62f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                if (state.wallet.purpleDrawTickets > 0) {
                    Button(
                        onClick = onPurple,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8A63C7)),
                    ) {
                        Text("♡ 今日安全抽 · ${state.wallet.purpleDrawTickets}张", fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onSingle,
                        modifier = Modifier.weight(1f).height(54.dp),
                        border = BorderStroke(1.dp, Color(0xFFE5D6FF)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    ) {
                        Text("单抽 · $singleCost", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onTen,
                        modifier = Modifier.weight(1f).height(54.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE5B963),
                            contentColor = Color(0xFF2A1836),
                        ),
                    ) {
                        Text("十连 · ${StudyRules.TEN_DRAW_COST}", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun MoonlightBackdrop(modifier: Modifier = Modifier) {
    Canvas(
        modifier.background(
            Brush.verticalGradient(
                listOf(Color(0xFF111A42), Color(0xFF39285F), Color(0xFF211A43), Color(0xFF100F27)),
            ),
        ),
    ) {
        drawCircle(
            color = Color(0x33FFF5D8),
            radius = size.minDimension * 0.28f,
            center = Offset(size.width * 0.88f, size.height * 0.08f),
        )
        listOf(0.12f to 0.15f, 0.78f to 0.22f, 0.20f to 0.48f, 0.88f to 0.66f, 0.34f to 0.84f)
            .forEachIndexed { index, (x, y) ->
                drawCircle(
                    color = if (index % 2 == 0) Color(0x66FFD8F3) else Color(0x66D9C6FF),
                    radius = 3f + index,
                    center = Offset(size.width * x, size.height * y),
                )
            }
    }
}

@Composable
private fun MoonlightPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.58f)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun InventoryPill(label: String, count: Int, color: Color) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = 0.19f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.38f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.labelSmall)
            Text(count.toString(), color = color, fontWeight = FontWeight.Black)
        }
    }
}

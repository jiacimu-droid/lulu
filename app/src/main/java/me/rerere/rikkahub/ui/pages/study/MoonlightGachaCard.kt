package me.rerere.rikkahub.ui.pages.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.reflect.KFunction0
import me.rerere.rikkahub.data.study.StudyRules
import me.rerere.rikkahub.data.study.StudyState

/**
 * Bright, fixed-height anime gacha screen.
 *
 * The probability, pity and inventory details still exist in the rules and guide,
 * but this main screen intentionally shows only the current kudos and draw actions.
 */
@Composable
internal fun GachaCard(
    state: StudyState,
    onSingle: () -> Unit,
    onTen: () -> Unit,
    onPurple: KFunction0<Unit>,
) {
    val configuration = LocalConfiguration.current
    val cardHeight = (configuration.screenHeightDp - 205)
        .coerceIn(430, 590)
        .dp
    val singleCost = if (StudyRules.hasSingleDrawDiscount(state)) {
        StudyRules.DISCOUNT_SINGLE_DRAW_COST
    } else {
        StudyRules.SINGLE_DRAW_COST
    }
    val hasSafetyDraw = state.wallet.purpleDrawTickets > 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7D7)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = BorderStroke(2.dp, Color(0xFFFFE8A7)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimeCandyBackdrop(Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                KudosPanel(kudos = state.wallet.kudos)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "星糖扭蛋机",
                        color = Color(0xFF7B4B57),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "把今天的努力，摇成一颗好运。",
                        color = Color(0xFF9A6A70),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }

                AnimeGachaMachine(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = if (hasSafetyDraw) onPurple else onSingle,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFBFE7FF),
                            contentColor = Color(0xFF31536D),
                        ),
                        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.92f)),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                    ) {
                        Text(
                            text = if (hasSafetyDraw) "单抽 · 免费" else "单抽 · $singleCost",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    Button(
                        onClick = onTen,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFC857),
                            contentColor = Color(0xFF684018),
                        ),
                        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.92f)),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp),
                    ) {
                        Text(
                            text = "十连 · ${StudyRules.TEN_DRAW_COST}",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KudosPanel(kudos: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.86f),
        border = BorderStroke(2.dp, Color(0xFFFFD79B)),
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = Color(0xFFFFE48E),
                border = BorderStroke(2.dp, Color.White),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "★",
                        color = Color(0xFFFF8A77),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "我的夸夸值",
                color = Color(0xFF815B60),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = kudos.toString(),
                color = Color(0xFFFF8A62),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun AnimeGachaMachine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color(0x55FFFFFF),
                radius = size.minDimension * 0.44f,
                center = Offset(size.width * 0.50f, size.height * 0.48f),
            )
            drawCircle(
                color = Color(0x66FFD2DB),
                radius = size.minDimension * 0.10f,
                center = Offset(size.width * 0.13f, size.height * 0.25f),
            )
            drawCircle(
                color = Color(0x667CCBFF),
                radius = size.minDimension * 0.075f,
                center = Offset(size.width * 0.87f, size.height * 0.22f),
            )
            drawCircle(
                color = Color(0x77FFE27A),
                radius = size.minDimension * 0.06f,
                center = Offset(size.width * 0.83f, size.height * 0.78f),
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(38.dp),
            color = Color(0xFFFFAFA6),
            border = BorderStroke(4.dp, Color.White.copy(alpha = 0.96f)),
            shadowElevation = 8.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFFC4AE),
                                Color(0xFFFF9FAD),
                            ),
                        ),
                    )
                    .padding(14.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.64f)
                        .align(Alignment.TopCenter),
                    shape = RoundedCornerShape(32.dp),
                    color = Color(0xFFBFE9FF),
                    border = BorderStroke(4.dp, Color.White.copy(alpha = 0.94f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFFFFF7B4),
                                        Color(0xFFD8F2FF),
                                        Color(0xFFB9E2FF),
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            modifier = Modifier.size(108.dp),
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.80f),
                            border = BorderStroke(3.dp, Color(0xFFFFE58E)),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "★",
                                    color = Color(0xFFFFB83E),
                                    style = MaterialTheme.typography.displayLarge,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .size(72.dp)
                        .align(Alignment.BottomCenter),
                    shape = CircleShape,
                    color = Color(0xFFFFE278),
                    border = BorderStroke(4.dp, Color.White),
                    shadowElevation = 4.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "GO!",
                            color = Color(0xFF87514A),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }

                Text(
                    text = "LUCKY!",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 4.dp, bottom = 6.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun AnimeCandyBackdrop(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFFF4BD),
                    Color(0xFFFFD8C7),
                    Color(0xFFD7ECFF),
                ),
            ),
        ),
    ) {
        drawCircle(
            color = Color.White.copy(alpha = 0.48f),
            radius = size.minDimension * 0.30f,
            center = Offset(size.width * 0.92f, size.height * 0.06f),
        )
        drawCircle(
            color = Color(0x55FF9CAF),
            radius = size.minDimension * 0.16f,
            center = Offset(size.width * 0.06f, size.height * 0.62f),
        )
        drawCircle(
            color = Color(0x5577C9FF),
            radius = size.minDimension * 0.13f,
            center = Offset(size.width * 0.94f, size.height * 0.84f),
        )
        listOf(
            0.12f to 0.12f,
            0.78f to 0.18f,
            0.22f to 0.40f,
            0.86f to 0.52f,
            0.16f to 0.86f,
        ).forEachIndexed { index, (x, y) ->
            drawCircle(
                color = if (index % 2 == 0) {
                    Color(0xAAFFFFFF)
                } else {
                    Color(0x99FFE96B)
                },
                radius = 3.5f + index,
                center = Offset(size.width * x, size.height * y),
            )
        }
    }
}

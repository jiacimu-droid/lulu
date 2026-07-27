package me.rerere.rikkahub.ui.pages.study

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.widget.VideoView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.study.StudyDrawResult
import me.rerere.rikkahub.data.study.StudyMysteryBoxReward
import me.rerere.rikkahub.data.study.StudyRarity
import me.rerere.rikkahub.utils.resolveAppVideoUri

private const val STUDY_RAINBOW_DRAW_VIDEO_URI = "raw:star_wish_rainbow_draw"
private const val STUDY_EPIC_DRAW_VIDEO_URI = "raw:star_wish_epic_draw"
private const val STUDY_RARE_DRAW_VIDEO_URI = "raw:star_wish_rare_draw"

@Composable
internal fun StudyDrawResultCelebration(
    results: List<StudyDrawReveal>,
    onDismissRequest: () -> Unit,
) {
    val drawResults = remember(results) { results.map { it.result } }
    val isSingleDraw = drawResults.size == 1
    val best = drawResults.maxByOrNull { it.rarity.studyWeight }?.rarity ?: StudyRarity.Normal
    var revealState by remember(results) { mutableStateOf(DrawRevealFlow.start(drawResults)) }
    var playedRewardVideoIndexes by remember(results) { mutableStateOf(emptySet<Int>()) }
    var skipAllRequested by remember(results) { mutableStateOf(false) }
    val currentReveal = results.getOrNull(revealState.index)
    val current = currentReveal?.result
    val haptic = LocalHapticFeedback.current
    var cardRevealReady by remember(results) { mutableStateOf(true) }
    val hasOpeningVideo = remember(drawResults) {
        drawResults.any {
            it.rarity == StudyRarity.Rainbow ||
                it.rarity == StudyRarity.Epic ||
                it.rarity == StudyRarity.Rare
        }
    }
    val openingVideoUri = when (revealState.phase) {
        DrawRevealPhase.RainbowOpeningVideo -> STUDY_RAINBOW_DRAW_VIDEO_URI
        DrawRevealPhase.EpicOpeningVideo -> STUDY_EPIC_DRAW_VIDEO_URI
        DrawRevealPhase.RareOpeningVideo -> STUDY_RARE_DRAW_VIDEO_URI
        else -> when {
            drawResults.any { it.rarity == StudyRarity.Rainbow } -> STUDY_RAINBOW_DRAW_VIDEO_URI
            drawResults.any { it.rarity == StudyRarity.Epic } -> STUDY_EPIC_DRAW_VIDEO_URI
            drawResults.any { it.rarity == StudyRarity.Rare } -> STUDY_RARE_DRAW_VIDEO_URI
            else -> null
        }
    }
    val rewardVideoPending = revealState.phase == DrawRevealPhase.Card &&
        currentReveal?.video != null &&
        revealState.index !in playedRewardVideoIndexes
    val showOpeningBackdrop = hasOpeningVideo &&
        revealState.phase != DrawRevealPhase.Summary &&
        revealState.phase != DrawRevealPhase.Done
    val rewardVideoUri = currentReveal?.video?.uri
        ?.takeIf { revealState.phase == DrawRevealPhase.RewardVideo }
    val transition = rememberInfiniteTransition(label = "draw-result")
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (current?.rarity == StudyRarity.Epic) 520 else 780),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "draw-pulse",
    )

    LaunchedEffect(revealState.index, revealState.phase, current?.rarity) {
        if (revealState.phase != DrawRevealPhase.Card || current == null) {
            cardRevealReady = true
            return@LaunchedEffect
        }
        cardRevealReady = false
        when (current.rarity) {
            StudyRarity.Normal -> {
                haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                delay(120)
            }
            StudyRarity.Rare -> {
                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                delay(360)
            }
            StudyRarity.Epic -> {
                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                delay(120)
                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                delay(480)
            }
            StudyRarity.Rainbow -> {
                repeat(3) {
                    haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    delay(150)
                }
                delay(420)
            }
        }
        cardRevealReady = true
    }

    fun nextPendingRewardVideoIndex(): Int? {
        val startIndex = revealState.index.coerceAtLeast(0)
        return results
            .withIndex()
            .firstOrNull { (index, reveal) ->
                index >= startIndex &&
                    reveal.video != null &&
                    index !in playedRewardVideoIndexes
            }
            ?.index
    }

    fun skipAll() {
        skipAllRequested = true
        val nextRewardVideoIndex = nextPendingRewardVideoIndex()
        revealState = if (nextRewardVideoIndex != null) {
            revealState.copy(index = nextRewardVideoIndex, phase = DrawRevealPhase.RewardVideo)
        } else {
            DrawRevealFlow.skip(revealState, drawResults)
        }
    }

    fun finishRewardVideo() {
        playedRewardVideoIndexes = playedRewardVideoIndexes + revealState.index
        val remaining = results.indices.firstOrNull { index ->
            results[index].video != null && index !in playedRewardVideoIndexes
        }
        revealState = if (skipAllRequested && remaining != null) {
            revealState.copy(index = remaining, phase = DrawRevealPhase.RewardVideo)
        } else if (skipAllRequested) {
            DrawRevealFlow.summary(revealState)
        } else {
            revealState.copy(phase = DrawRevealPhase.Card)
        }
    }

    fun closeCurrentVideo() {
        if (revealState.phase == DrawRevealPhase.RewardVideo) {
            finishRewardVideo()
            return
        }
        revealState = when (revealState.phase) {
            DrawRevealPhase.RainbowOpeningVideo -> DrawRevealFlow.videoFinished(revealState, drawResults)
            DrawRevealPhase.EpicOpeningVideo -> DrawRevealFlow.videoFinished(revealState, drawResults)
            DrawRevealPhase.RareOpeningVideo -> DrawRevealFlow.videoFinished(revealState, drawResults)
            DrawRevealPhase.RewardVideo -> revealState
            else -> DrawRevealFlow.skip(revealState, drawResults)
        }
    }

    LaunchedEffect(
        revealState.index,
        revealState.phase,
        currentReveal?.video?.uri,
        playedRewardVideoIndexes,
    ) {
        if (rewardVideoPending) {
            delay(420)
            revealState = revealState.copy(phase = DrawRevealPhase.RewardVideo)
        }
    }
    LaunchedEffect(revealState.phase) {
        if (revealState.phase == DrawRevealPhase.Done) {
            if (isSingleDraw) {
                onDismissRequest()
            } else {
                revealState = DrawRevealFlow.summary(revealState)
            }
        }
    }

    Dialog(
        onDismissRequest = { skipAll() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (showOpeningBackdrop) {
                        Modifier
                    } else {
                        Modifier.background(studyDrawFullscreenBrush(current?.rarity ?: best))
                    },
                ),
        ) {
            if (showOpeningBackdrop && openingVideoUri != null) {
                StudyDrawOpeningVideoLayer(
                    videoUri = openingVideoUri,
                    shouldPlay = revealState.phase == DrawRevealPhase.RainbowOpeningVideo ||
                        revealState.phase == DrawRevealPhase.EpicOpeningVideo ||
                        revealState.phase == DrawRevealPhase.RareOpeningVideo,
                    onFinished = {
                        if (revealState.phase == DrawRevealPhase.RainbowOpeningVideo ||
                            revealState.phase == DrawRevealPhase.EpicOpeningVideo ||
                            revealState.phase == DrawRevealPhase.RareOpeningVideo
                        ) {
                            revealState = DrawRevealFlow.videoFinished(revealState, drawResults)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (revealState.phase == DrawRevealPhase.RainbowOpeningVideo ||
                    revealState.phase == DrawRevealPhase.EpicOpeningVideo ||
                    revealState.phase == DrawRevealPhase.RareOpeningVideo
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.04f)),
                    )
                }
            }
            if (rewardVideoUri != null) {
                StudyDrawRewardVideoLayer(
                    videoUri = rewardVideoUri,
                    playbackKey = "${revealState.index}:reward",
                    onFinished = { finishRewardVideo() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (revealState.phase == DrawRevealPhase.Summary) {
                StudyDrawResultSummary(
                    results = drawResults,
                    onDismissRequest = onDismissRequest,
                    modifier = Modifier.align(Alignment.Center),
                )
                return@Box
            }
            IconButton(
                onClick = { closeCurrentVideo() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 18.dp, end = 14.dp)
                    .background(Color.Black.copy(alpha = 0.42f), CircleShape),
            ) {
                Text("×", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            }
            if (revealState.phase != DrawRevealPhase.RainbowOpeningVideo &&
                revealState.phase != DrawRevealPhase.EpicOpeningVideo &&
                revealState.phase != DrawRevealPhase.RareOpeningVideo &&
                revealState.phase != DrawRevealPhase.RewardVideo
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 28.dp)
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Spacer(Modifier.weight(1f))
                    AnimatedContent(
                        targetState = revealState.index to revealState.phase,
                        transitionSpec = {
                            val direction = if (targetState.first >= initialState.first) 1 else -1
                            val enter = fadeIn(tween(220)) +
                                slideInHorizontally(tween(360)) { width -> width * direction }
                            val exit = fadeOut(tween(180)) +
                                slideOutHorizontally(tween(300)) { width -> -width * direction }
                            enter togetherWith exit
                        },
                        label = "draw-card-fade",
                    ) { (index, phase) ->
                        val cardResult = if (phase == DrawRevealPhase.Card) {
                            drawResults.getOrNull(index)
                        } else {
                            null
                        }
                        if (cardResult == null) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.size(width = 236.dp, height = 316.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "愿光正在汇聚",
                                        color = Color.White.copy(alpha = 0.86f),
                                        fontWeight = FontWeight.Black,
                                    )
                                }
                            }
                        } else {
                            StudyDrawRevealCard(result = cardResult, pulse = pulse)
                        }
                    }
                    Spacer(Modifier.weight(0.8f))
                    if (revealState.index < drawResults.lastIndex) {
                        Button(
                            onClick = { revealState = DrawRevealFlow.next(revealState, drawResults) },
                            enabled = revealState.phase == DrawRevealPhase.Card &&
                                !rewardVideoPending &&
                                cardRevealReady,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("下一个")
                        }
                        OutlinedButton(onClick = { skipAll() }, modifier = Modifier.fillMaxWidth()) {
                            Text("跳过全部")
                        }
                    } else {
                        Button(
                            onClick = {
                                if (isSingleDraw) {
                                    onDismissRequest()
                                } else {
                                    revealState = DrawRevealFlow.summary(revealState)
                                }
                            },
                            enabled = revealState.phase == DrawRevealPhase.Card &&
                                !rewardVideoPending &&
                                cardRevealReady,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (isSingleDraw) "收下" else "查看结果")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyDrawOpeningVideoLayer(
    videoUri: String,
    shouldPlay: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (shouldPlay) {
        StudyDrawVideoLayer(
            videoUri = videoUri,
            playbackKey = "draw-opening:$videoUri",
            shouldPlay = true,
            freezeAtEnd = true,
            onFinished = onFinished,
            modifier = modifier,
        )
    } else {
        StudyDrawVideoFrozenFrame(videoUri = videoUri, modifier = modifier)
    }
}

@Composable
private fun StudyDrawRewardVideoLayer(
    videoUri: String,
    playbackKey: String,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StudyDrawVideoLayer(
        videoUri = videoUri,
        playbackKey = playbackKey,
        shouldPlay = true,
        freezeAtEnd = false,
        onFinished = onFinished,
        modifier = modifier,
    )
}

@Composable
private fun StudyDrawVideoLayer(
    videoUri: String,
    playbackKey: String,
    shouldPlay: Boolean,
    freezeAtEnd: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var videoView by remember(videoUri, playbackKey) { mutableStateOf<VideoView?>(null) }
    var completed by remember(videoUri, playbackKey) { mutableStateOf(false) }

    fun VideoView.freezeAtEnd() {
        seekTo((duration - 80).coerceAtLeast(0))
        pause()
    }

    fun VideoView.loadDrawVideo() {
        tag = "$videoUri#$playbackKey"
        completed = false
        setVideoURI(resolveAppVideoUri(context, videoUri))
        setOnPreparedListener { player ->
            player.isLooping = false
            runCatching {
                player.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            }
            if (shouldPlay) start() else freezeAtEnd()
        }
        setOnCompletionListener {
            if (!completed) {
                completed = true
                if (freezeAtEnd) freezeAtEnd() else pause()
                onFinished()
            }
        }
        setOnErrorListener { _, _, _ ->
            if (!completed) {
                completed = true
                onFinished()
            }
            true
        }
    }

    DisposableEffect(videoUri, playbackKey) {
        onDispose {
            videoView?.stopPlayback()
            videoView = null
        }
    }
    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { viewContext ->
            VideoView(viewContext).apply {
                videoView = this
                loadDrawVideo()
            }
        },
        update = { view ->
            if (videoView !== view) videoView = view
            if (view.tag != "$videoUri#$playbackKey") {
                view.stopPlayback()
                view.loadDrawVideo()
            } else if (shouldPlay && !completed && !view.isPlaying) {
                view.start()
            } else if (!shouldPlay && !completed && view.duration > 0) {
                view.freezeAtEnd()
            }
        },
    )
}

@Composable
private fun StudyDrawVideoFrozenFrame(
    videoUri: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var bitmap by remember(videoUri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(videoUri) {
        bitmap = withContext(Dispatchers.IO) {
            loadStudyVideoLastFrame(context, videoUri)
        }
    }
    val frame = bitmap
    if (frame != null) {
        Image(
            bitmap = frame.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.background(Color.Black),
        )
    } else {
        Box(modifier = modifier.background(studyDrawFullscreenBrush(StudyRarity.Rainbow)))
    }
}

private fun loadStudyVideoLastFrame(context: Context, videoUri: String): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, resolveAppVideoUri(context, videoUri))
        val durationMs = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?: 0L
        val frameTimeUs = (durationMs - 80L).coerceAtLeast(0L) * 1_000L
        retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.getFrameAtTime()
    } catch (_: Throwable) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

@Composable
private fun StudyDrawResultSummary(
    results: List<StudyDrawResult>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.White.copy(alpha = 0.9f),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 10.dp,
        modifier = modifier.fillMaxWidth().padding(18.dp),
    ) {
        Column(
            modifier = Modifier
                .background(studyDrawCardBrush(results.maxByOrNull { it.rarity.studyWeight }?.rarity ?: StudyRarity.Normal))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "抽卡结果",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = Color.White,
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                results.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { result ->
                            StudyDrawResultSquare(result = result, modifier = Modifier.weight(1f))
                        }
                        repeat(5 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Button(onClick = onDismissRequest, modifier = Modifier.fillMaxWidth()) {
                Text("收下")
            }
        }
    }
}

@Composable
private fun StudyDrawResultSquare(
    result: StudyDrawResult,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.White.copy(alpha = 0.88f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.aspectRatio(1f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(studyDrawCardBrush(result.rarity))
                .padding(6.dp),
        ) {
            Text(
                result.title,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
            if (result.alreadyFull) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Text(
                        text = "碎片已满",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyDrawRevealCard(result: StudyDrawResult, pulse: Float) {
    val borderColor = when (result.rarity) {
        StudyRarity.Normal -> Color.White.copy(alpha = 0.42f)
        StudyRarity.Rare -> Color(0xFFE0C7FF)
        StudyRarity.Epic -> Color(0xFFFFE4A3)
        StudyRarity.Rainbow -> Color(0xFFE8FFFF)
    }
    val borderWidth = when (result.rarity) {
        StudyRarity.Normal -> 1.dp
        StudyRarity.Rare -> 2.dp
        StudyRarity.Epic -> 3.dp
        StudyRarity.Rainbow -> 4.dp
    }
    Surface(
        color = Color.White.copy(alpha = 0.9f),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 8.dp,
        border = BorderStroke(borderWidth, borderColor),
        modifier = Modifier.size(width = 236.dp, height = 316.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(studyDrawCardBrush(result.rarity))
                .padding(18.dp),
        ) {
            if (result.rarity != StudyRarity.Normal) {
                Canvas(Modifier.fillMaxSize()) {
                    val points = listOf(
                        0.12f to 0.16f,
                        0.78f to 0.12f,
                        0.34f to 0.30f,
                        0.86f to 0.44f,
                        0.18f to 0.58f,
                        0.70f to 0.70f,
                        0.42f to 0.86f,
                    )
                    val visiblePoints = when (result.rarity) {
                        StudyRarity.Rare -> points.take(3)
                        StudyRarity.Epic -> points.take(5)
                        StudyRarity.Rainbow -> points
                        StudyRarity.Normal -> emptyList()
                    }
                    visiblePoints.forEachIndexed { index, (x, y) ->
                        drawCircle(
                            color = Color.White.copy(alpha = 0.38f + (index % 3) * 0.14f),
                            radius = (2.5f + (index % 3) * 2f) * pulse,
                            center = Offset(size.width * x, size.height * y),
                        )
                    }
                }
            }
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.82f),
                modifier = Modifier.align(Alignment.TopEnd).size((62 * pulse).dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        when (result.rarity) {
                            StudyRarity.Epic -> "金"
                            StudyRarity.Rainbow -> "彩"
                            else -> result.rarity.label.take(1)
                        },
                        color = studyRarityColor(result.rarity),
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Column(
                modifier = Modifier.align(Alignment.BottomStart),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    result.rarity.label,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    result.title,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                )
                if (result.alreadyFull) {
                    Text(
                        text = "该蓝色碎片已集满，已为你展示本次抽取",
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
internal fun StudyMysteryBoxCelebration(
    reward: StudyMysteryBoxReward,
    onDismissRequest: () -> Unit,
) {
    val rarity = when (reward.kudos) {
        15, 25 -> StudyRarity.Normal
        50 -> StudyRarity.Rare
        else -> StudyRarity.Epic
    }
    val transition = rememberInfiniteTransition(label = "mystery-box")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { TextButton(onClick = onDismissRequest) { Text("收下") } },
        title = { Text("盲盒打开啦") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(studyDrawBrush(rarity), RoundedCornerShape(18.dp))
                    .padding(18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.78f),
                        modifier = Modifier.size((78 * pulse).dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "+${reward.kudos}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = studyRarityColor(rarity),
                            )
                        }
                    }
                    Text(studyMysteryBoxText(reward.kudos), color = Color.White, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(7) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.55f),
                                modifier = Modifier.size(((7 + it % 3 * 4) * pulse).dp),
                            ) {}
                        }
                    }
                }
            }
        },
    )
}

@Composable
internal fun StudyMysteryBoxPendingDialog(
    onOpen: () -> Unit,
    onKeep: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text("盲盒待开启") },
        text = {
            Text("番茄钟奖励已经放进收藏背包。现在开启就能看到奖励；不想开的话，之后也可以在收藏背包里打开。")
        },
        confirmButton = {
            Button(onClick = onOpen) {
                Text("开启盲盒")
            }
        },
        dismissButton = {
            TextButton(onClick = onKeep) {
                Text("先放背包")
            }
        },
    )
}

private fun studyDrawFullscreenBrush(rarity: StudyRarity): Brush = when (rarity) {
    StudyRarity.Normal -> Brush.verticalGradient(listOf(Color(0xFF1F3D54), Color(0xFF5A8296), Color(0xFF0F1B2B)))
    StudyRarity.Rare -> Brush.verticalGradient(listOf(Color(0xFF251D52), Color(0xFF8067B7), Color(0xFF120D2C)))
    StudyRarity.Epic -> Brush.verticalGradient(listOf(Color(0xFF3A2400), Color(0xFFFFB938), Color(0xFF6F2E00)))
    StudyRarity.Rainbow -> Brush.verticalGradient(listOf(Color(0xFF07111F), Color(0xFF182B40), Color(0xFF05070D)))
}

private fun studyRarityColor(rarity: StudyRarity): Color = when (rarity) {
    StudyRarity.Normal -> Color(0xFF3D7EA6)
    StudyRarity.Rare -> Color(0xFF8067B7)
    StudyRarity.Epic -> Color(0xFF9B6B10)
    StudyRarity.Rainbow -> Color(0xFF23C8B8)
}

private val StudyRarity.studyWeight: Int
    get() = when (this) {
        StudyRarity.Normal -> 1
        StudyRarity.Rare -> 2
        StudyRarity.Epic -> 3
        StudyRarity.Rainbow -> 4
    }

private fun studyDrawBrush(rarity: StudyRarity): Brush = when (rarity) {
    StudyRarity.Normal -> Brush.linearGradient(listOf(Color(0xFF8CC7D8), Color(0xFF6F8FA6)))
    StudyRarity.Rare -> Brush.linearGradient(listOf(Color(0xFF8067B7), Color(0xFFB88BCE)))
    StudyRarity.Epic -> Brush.linearGradient(listOf(Color(0xFFFFC857), Color(0xFFFF8F5A), Color(0xFFFFF2B3)))
    StudyRarity.Rainbow -> Brush.linearGradient(
        listOf(Color(0xFF5DE0E6), Color(0xFFFF6B9A), Color(0xFFFFD166), Color(0xFF9B5DE5)),
    )
}

private fun studyDrawCardBrush(rarity: StudyRarity): Brush = when (rarity) {
    StudyRarity.Rainbow -> Brush.linearGradient(
        listOf(Color(0xFF163B52), Color(0xFF7D4A91), Color(0xFFC06B78), Color(0xFF1E5B63)),
    )
    else -> studyDrawBrush(rarity)
}

private fun studyMysteryBoxText(kudos: Int): String = when (kudos) {
    15 -> "柔光蓝，星点飘浮。获得 15 夸夸值。"
    25 -> "流光蓝，光带环绕。获得 25 夸夸值。"
    50 -> "幽雅紫，花瓣光晕。获得 50 夸夸值。"
    100 -> "暖金亮起来了。获得 100 夸夸值。"
    else -> "璨金粒子炸开。获得 200 夸夸值。"
}

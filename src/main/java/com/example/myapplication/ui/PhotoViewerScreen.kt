package com.example.myapplication.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.myapplication.data.GalleryItem
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(
    items: List<GalleryItem>,
    startIndex: Int,
    autoPlayVideos: Boolean = true,
    onShare: (GalleryItem) -> Unit,
    onDelete: (GalleryItem) -> Unit,
    onBack: () -> Unit,
) {
    if (items.isEmpty()) {
        onBack()
        return
    }

    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size }
    )
    val current = items.getOrNull(pagerState.currentPage) ?: items.first()

    // Remembers each video's playback position so swiping away and back resumes where you left off.
    val savedPositions = remember { mutableMapOf<Long, Long>() }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "${pagerState.currentPage + 1} / ${items.size}",
                        color = Color.White,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onShare(current) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share", tint = Color.White)
                    }
                    IconButton(onClick = { onDelete(current) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.4f),
                ),
            )
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) { page ->
            val item = items[page]
            when {
                item.isVideo && page == pagerState.currentPage ->
                    VideoPage(
                        uri = item.uri,
                        autoPlay = autoPlayVideos,
                        initialPositionMs = savedPositions[item.id] ?: 0L,
                        onSavePosition = { savedPositions[item.id] = it },
                        onDismiss = onBack,
                    )

                item.isVideo ->
                    // Off-screen video page: show its thumbnail until it becomes current.
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )

                else ->
                    ZoomableBox(key = item.id, onDismiss = onBack) {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = item.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
            }
        }
    }
}

/**
 * Wraps [content] with pinch-to-zoom, drag-to-pan, and double-tap-to-zoom.
 * Works for any content (images and the video surface alike). When not zoomed, dragging down
 * triggers [onDismiss] (swipe-to-dismiss), shrinking and fading the content as it is dragged.
 */
@Composable
private fun ZoomableBox(
    key: Any,
    onDismiss: (() -> Unit)? = null,
    onTap: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val scale = remember(key) { Animatable(1f) }
    val offset = remember(key) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val dragY = remember(key) { Animatable(0f) }
    var layoutSize by remember(key) { mutableStateOf(IntSize.Zero) }

    val springScale = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy)
    val springOffset = spring<Offset>(dampingRatio = Spring.DampingRatioLowBouncy)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { layoutSize = it }
            // Custom transform gesture: only consume the touch when actually zoomed (or pinching),
            // so at scale 1 the horizontal swipe still reaches the HorizontalPager and a downward
            // drag can dismiss the viewer.
            .pointerInput(key) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var dismissing = false
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        val pointers = event.changes.count { it.pressed }
                        val pinching = pointers >= 2
                        if (pinching || scale.value > 1f) {
                            val newScale = (scale.value * zoom).coerceIn(1f, 5f)
                            val newOffset = if (newScale > 1f) offset.value + pan else Offset.Zero
                            scope.launch { scale.snapTo(newScale) }
                            scope.launch { offset.snapTo(newOffset) }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (onDismiss != null && pointers == 1) {
                            // Decide once: a predominantly-downward drag at scale 1 dismisses.
                            if (!dismissing && pan.y > 0f && abs(pan.y) > abs(pan.x)) {
                                dismissing = true
                            }
                            if (dismissing) {
                                val ny = (dragY.value + pan.y).coerceAtLeast(0f)
                                scope.launch { dragY.snapTo(ny) }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (dismissing) {
                        val threshold = layoutSize.height * 0.18f
                        if (dragY.value > threshold) {
                            onDismiss?.invoke()
                        } else {
                            scope.launch { dragY.animateTo(0f, springScale) }
                        }
                    }
                }
            }
            .pointerInput(key) {
                detectTapGestures(
                    onTap = onTap?.let { tapCallback -> { _ -> tapCallback() } },
                    onDoubleTap = { tap ->
                        if (scale.value > 1f) {
                            scope.launch { scale.animateTo(1f, springScale) }
                            scope.launch { offset.animateTo(Offset.Zero, springOffset) }
                        } else {
                            val target = 3f
                            val center = Offset(layoutSize.width / 2f, layoutSize.height / 2f)
                            // Keep the tapped content point fixed under the finger while zooming in.
                            val targetOffset = (tap - center) * (1f - target)
                            scope.launch { scale.animateTo(target, springScale) }
                            scope.launch { offset.animateTo(targetOffset, springOffset) }
                        }
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val dismissFrac =
                        if (layoutSize.height > 0) {
                            (dragY.value / layoutSize.height).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    val dismissScale = 1f - dismissFrac * 0.3f
                    scaleX = scale.value * dismissScale
                    scaleY = scale.value * dismissScale
                    translationX = offset.value.x
                    translationY = offset.value.y + dragY.value
                    alpha = 1f - dismissFrac * 0.5f
                }
        ) {
            content()
        }
    }
}

/**
 * The current video page: a zoomable video surface with a minimal controls overlay
 * (play/pause + seek bar). The controls sit outside the zoom transform so they stay fixed.
 */
@Composable
private fun VideoPage(
    uri: Uri,
    autoPlay: Boolean,
    initialPositionMs: Long,
    onSavePosition: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            if (initialPositionMs > 0) seekTo(initialPositionMs)
            playWhenReady = autoPlay
        }
    }
    DisposableEffect(uri) {
        onDispose {
            onSavePosition(player.currentPosition)
            player.release()
        }
    }

    var controlsVisible by remember(uri) { mutableStateOf(true) }
    var revealTick by remember(uri) { mutableLongStateOf(0L) }
    fun reveal() {
        controlsVisible = true
        revealTick += 1
    }
    // Auto-hide the controls a few seconds after the last interaction.
    LaunchedEffect(controlsVisible, revealTick) {
        if (controlsVisible) {
            delay(3500)
            controlsVisible = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ZoomableBox(
            key = uri,
            onDismiss = onDismiss,
            onTap = { if (controlsVisible) controlsVisible = false else reveal() },
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        ) {
            VideoControls(player = player, onInteraction = { reveal() })
        }
    }
}

@Composable
private fun VideoControls(
    player: ExoPlayer,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            if (!scrubbing) {
                position = player.currentPosition
                duration = player.duration.coerceAtLeast(0L)
            }
            delay(250)
        }
    }

    fun skip(deltaMs: Long) {
        val target = (player.currentPosition + deltaMs)
            .coerceIn(0L, player.duration.coerceAtLeast(0L))
        player.seekTo(target)
        position = target
        onInteraction()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatDuration(if (scrubbing) scrubValue.toLong() else position),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
            Slider(
                value = if (scrubbing) scrubValue else position.toFloat(),
                onValueChange = { scrubbing = true; scrubValue = it; onInteraction() },
                onValueChangeFinished = {
                    player.seekTo(scrubValue.toLong())
                    position = scrubValue.toLong()
                    scrubbing = false
                },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            Text(
                text = formatDuration(duration),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { skip(-10_000) }) {
                Icon(Icons.Filled.Replay10, contentDescription = "Back 10 seconds", tint = Color.White)
            }
            IconButton(onClick = {
                if (player.isPlaying) player.pause() else player.play()
                onInteraction()
            }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                )
            }
            IconButton(onClick = { skip(10_000) }) {
                Icon(Icons.Filled.Forward10, contentDescription = "Forward 10 seconds", tint = Color.White)
            }
        }
    }
}

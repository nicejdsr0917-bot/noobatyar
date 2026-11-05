package xyz.sattar.javid.proqueue.feature.stories

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.composables.icons.lucide.Store
import org.jetbrains.compose.resources.painterResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.main_icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.layout.width
import com.composables.icons.lucide.ArrowLeft
import xyz.sattar.javid.proqueue.feature.businessDetail.FollowButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Tag
import com.composables.icons.lucide.X
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits

private const val STORY_MILLIS = 5_000L
private const val TICK_MILLIS = 50L

/** Put once at the app root: shows the viewer whenever [StoryCenter.open] was called. */
@Composable
fun StoryViewerHost() {
    val center = koinInject<StoryCenter>()
    val businessId by center.viewing.collectAsState()
    businessId?.let { id -> StoryViewer(id, center) }
}

private fun discountText(story: StoryDto): String? {
    val value = story.discountValue ?: return null
    if (story.button != "discount") return null
    return if (story.discountType == "percent") "${value.toString().toPersianDigits()}٪ تخفیف"
    else "${value.toString().reversed().chunked(3).joinToString(",").reversed().toPersianDigits()} تومان تخفیف"
}

@Composable
private fun StoryViewer(businessId: Long, center: StoryCenter) {
    var data by remember(businessId) { mutableStateOf<BusinessStoriesDto?>(null) }
    var failed by remember(businessId) { mutableStateOf(false) }
    var index by remember(businessId) { mutableIntStateOf(0) }
    var progress by remember(businessId) { mutableFloatStateOf(0f) }
    var followState by remember(businessId) { mutableStateOf<Boolean?>(null) }
    var following by remember(businessId) { mutableStateOf(false) }
    var followedHere by remember(businessId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(businessId) {
        when (val response = center.load(businessId)) {
            is ApiResponse.Success -> {
                data = response.data
                followState = response.data.isFollowing
                if (response.data.stories.isEmpty()) center.markGone(businessId)
            }
            is ApiResponse.Error -> failed = true
        }
    }

    val stories = data?.stories.orEmpty()
    fun next() {
        if (index < stories.lastIndex) { index++; progress = 0f }
        else center.finished(businessId)
    }
    fun previous() {
        if (index > 0) index--
        progress = 0f
    }

    // Held down: the story stops where it is, like Instagram, and the chrome fades.
    var paused by remember(businessId) { mutableStateOf(false) }
    LaunchedEffect(index, stories.size) {
        if (stories.isEmpty()) return@LaunchedEffect
        progress = 0f
        while (progress < 1f) {
            delay(TICK_MILLIS)
            if (!paused) progress += TICK_MILLIS.toFloat() / STORY_MILLIS
        }
        next()
    }
    val chrome by animateFloatAsState(if (paused) 0f else 1f, tween(180), label = "storyChrome")

    Dialog(onDismissRequest = { center.close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val story = stories.getOrNull(index)
            when {
                story != null -> AsyncImage(
                    model = story.image,
                    contentDescription = story.caption,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                failed || (data != null && stories.isEmpty()) -> Text(
                    "استوری در دسترس نیست",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }

            // Tap zones: the start half goes back, the end half forward (follows RTL).
            // Pressing anywhere pauses until the finger lifts; a quick tap still navigates.
            // Keyed on the zone, not on `::next`: a function reference is a new object
            // on every recomposition (progress ticks every 50 ms), which restarted the
            // detector mid-tap so no tap ever landed. The latest index is read through
            // rememberUpdatedState instead.
            val goBack by rememberUpdatedState(::previous)
            val goForward by rememberUpdatedState(::next)
            Row(Modifier.fillMaxSize()) {
                listOf(false, true).forEach { forward ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight().pointerInput(forward) {
                            detectTapGestures(
                                onPress = {
                                    paused = true
                                    tryAwaitRelease()
                                    paused = false
                                },
                                onTap = { if (forward) goForward() else goBack() },
                            )
                        }
                    )
                }
            }

            Column(Modifier.fillMaxWidth().graphicsLayer { alpha = chrome }.safeDrawingPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    stories.forEachIndexed { i, _ ->
                        LinearProgressIndicator(
                            progress = { if (i < index) 1f else if (i == index) progress else 0f },
                            modifier = Modifier.weight(1f).height(3.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.35f),
                            drawStopIndicator = {},
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StoryHeader(
                        data = data,
                        isPlatform = businessId == PLATFORM_STORY_ID,
                        onOpen = { center.openBusinessPage(businessId) },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { center.close() }) {
                        Icon(Lucide.X, contentDescription = "بستن", tint = Color.White)
                    }
                }
                story?.let(::discountText)?.let { label ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Lucide.Tag, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(label, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .graphicsLayer { alpha = chrome }
                    .background(Color.Black.copy(alpha = 0.4f))
                    .safeDrawingPadding().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!story?.caption.isNullOrBlank()) {
                    Text(story!!.caption, color = Color.White, textAlign = TextAlign.Center)
                }
                story?.takeIf { it.button == "link" && it.linkLabel.isNotBlank() }?.let { s ->
                    val uri = LocalUriHandler.current
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val target = s.linkUrl.removePrefix("business:")
                            if (target != s.linkUrl) target.toLongOrNull()?.let(center::openBusinessPage)
                            else runCatching { uri.openUri(s.linkUrl) }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    ) {
                        Text(s.linkLabel, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Icon(Lucide.ArrowLeft, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
                // Only for a signed-in visitor who did not follow when the story opened;
                // it stays after the tap so the "following" pop is seen.
                if (followState == false || followedHere) {
                    Spacer(Modifier.height(12.dp))
                    FollowButton(
                        following = followState == true,
                        busy = following || followState == true,
                        onToggle = {
                            following = true
                            scope.launch {
                                if (center.follow(businessId) == true) {
                                    followState = true
                                    followedHere = true
                                }
                                following = false
                            }
                        },
                        onImage = true,
                    )
                }
            }
        }
    }
}

/**
 * Who posted it: photo, name and specialty. A business's header opens its
 * page (and closes the viewer); Noobatyar's has nowhere to go.
 */
@Composable
private fun StoryHeader(data: BusinessStoriesDto?, isPlatform: Boolean, onOpen: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .then(if (!isPlatform && data != null) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(shape).border(1.5.dp, Color.White.copy(alpha = 0.85f), shape)
                .background(Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isPlatform -> Image(
                    painterResource(Res.drawable.main_icon), contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
                data?.logo != null -> AsyncImage(
                    model = data.logo, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
                else -> Icon(Lucide.Store, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                data?.title.orEmpty(), color = Color.White, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            data?.subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

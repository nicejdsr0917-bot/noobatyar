package xyz.sattar.javid.proqueue.feature.stories

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
    val scope = rememberCoroutineScope()

    LaunchedEffect(businessId) {
        when (val response = center.load(businessId)) {
            is ApiResponse.Success -> {
                data = response.data
                followState = response.data.isFollowing
                if (response.data.stories.isEmpty()) center.markSeen(businessId)
            }
            is ApiResponse.Error -> failed = true
        }
    }

    val stories = data?.stories.orEmpty()
    fun next() {
        if (index < stories.lastIndex) { index++; progress = 0f }
        else { center.markSeen(businessId); center.close() }
    }
    fun previous() {
        if (index > 0) index--
        progress = 0f
    }

    LaunchedEffect(index, stories.size) {
        if (stories.isEmpty()) return@LaunchedEffect
        progress = 0f
        while (progress < 1f) {
            delay(TICK_MILLIS)
            progress += TICK_MILLIS.toFloat() / STORY_MILLIS
        }
        next()
    }

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
            Row(Modifier.fillMaxSize()) {
                val none = remember { MutableInteractionSource() }
                Box(Modifier.weight(1f).fillMaxHeight().clickable(none, null) { previous() })
                Box(Modifier.weight(1f).fillMaxHeight().clickable(none, null) { next() })
            }

            Column(Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
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
                    Text(
                        data?.title.orEmpty(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
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
                    .background(Color.Black.copy(alpha = 0.4f))
                    .safeDrawingPadding().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!story?.caption.isNullOrBlank()) {
                    Text(story!!.caption, color = Color.White, textAlign = TextAlign.Center)
                }
                // Only for a signed-in visitor who does not follow yet.
                if (followState == false) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            following = true
                            scope.launch {
                                if (center.follow(businessId) == true) followState = true
                                following = false
                            }
                        },
                        enabled = !following,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                    ) { Text("دنبال کردن") }
                }
            }
        }
    }
}

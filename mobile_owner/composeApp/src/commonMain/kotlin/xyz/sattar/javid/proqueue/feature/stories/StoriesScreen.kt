package xyz.sattar.javid.proqueue.feature.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.billing.formatToman
import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.ui.components.EmptyState
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost
import xyz.sattar.javid.proqueue.core.ui.components.adaptivePagePadding
import xyz.sattar.javid.proqueue.core.ui.components.showToasty
import xyz.sattar.javid.proqueue.core.utils.cropImageToRect
import xyz.sattar.javid.proqueue.core.utils.rememberImagePicker
import xyz.sattar.javid.proqueue.core.utils.toImageBitmapOrNull
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.StoryDraft
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.StoryDto

private const val CAPTION_MAX = 100
private const val MAX_WIDTH = 1080
private const val MAX_HEIGHT = 1920
private const val PERCENT = "percent"
private const val AMOUNT = "amount"

/** A picked photo, decoded once: [bitmap] for the preview, [jpeg] for the upload. */
private class PickedPhoto(val bitmap: ImageBitmap, val jpeg: ByteArray)

/**
 * Gallery photo as is (no crop), scaled down to fit a phone screen and
 * re-encoded as JPEG, so a 12 MP photo is not uploaded whole.
 */
private fun compress(raw: ByteArray): PickedPhoto? {
    val bitmap = runCatching { raw.toImageBitmapOrNull() }.getOrNull() ?: return null
    val scale = minOf(1f, MAX_WIDTH.toFloat() / bitmap.width, MAX_HEIGHT.toFloat() / bitmap.height)
    val jpeg = cropImageToRect(
        raw, 0f, 0f, 1f, 1f,
        outputWidth = (bitmap.width * scale).toInt().coerceAtLeast(1),
        outputHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
    ) ?: return null
    return PickedPhoto(bitmap, jpeg)
}

fun discountLabel(type: String, value: Long): String =
    if (type == PERCENT) Str.story_discount_percent(value.toString().toPersianDigits())
    else Str.story_discount_amount(formatToman(value).toPersianDigits())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoriesScreen(
    viewModel: StoriesViewModel = koinViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var picked by remember { mutableStateOf<PickedPhoto?>(null) }
    var deleting by remember { mutableStateOf<StoryDto?>(null) }

    LaunchedEffect(Unit) { viewModel.sendIntent(StoriesIntent.Load) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showToasty(it)
            viewModel.sendIntent(StoriesIntent.ClearMessage)
        }
    }
    LaunchedEffect(state.postedCount) { picked = null }

    val picker = rememberImagePicker(onSingleImagePicked = { raw -> picked = compress(raw) })

    picked?.let { photo ->
        StoryComposer(
            photo = photo,
            posting = state.isPosting,
            onDismiss = { if (!state.isPosting) picked = null },
            onPost = { caption, type, value ->
                viewModel.sendIntent(StoriesIntent.Post(StoryDraft(photo.jpeg, caption, type, value)))
            }
        )
    }

    deleting?.let { story ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(Str.story_delete_title) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    viewModel.sendIntent(StoriesIntent.Delete(story.id))
                }) { Text(Str.delete, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(Str.cancel) } }
        )
    }

    Scaffold(
        snackbarHost = { ToastyHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(Str.stories_title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = Str.back)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picker.launch() },
                icon = { Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null) },
                text = { Text(Str.story_new) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && state.stories.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.stories.isEmpty() -> EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    icon = Icons.Rounded.AutoStories,
                    title = Str.stories_empty,
                    subtitle = Str.stories_empty_subtitle
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    contentPadding = PaddingValues(
                        start = adaptivePagePadding(), end = adaptivePagePadding(), top = 4.dp, bottom = 96.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.stories, key = { it.id }) { story ->
                        StoryTile(story, onDelete = { deleting = story })
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryTile(story: StoryDto, onDelete: () -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(14.dp))) {
        AsyncImage(
            model = story.image,
            contentDescription = story.caption,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        val type = story.discountType
        val value = story.discountValue
        if (story.button == "discount" && value != null) {
            DiscountBadge(discountLabel(type, value), Modifier.align(Alignment.TopCenter).padding(top = 8.dp), compact = true)
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(36.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
        ) {
            Icon(Icons.Rounded.Delete, contentDescription = Str.delete, tint = Color.White)
        }
    }
}

@Composable
private fun DiscountBadge(text: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary, modifier = modifier) {
        Row(
            Modifier.padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 4.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
        ) {
            if (!compact) Icon(Icons.Rounded.LocalOffer, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                text,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/** Full-screen preview with a caption field, the «افزودن» sheet and «انتشار». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoryComposer(
    photo: PickedPhoto,
    posting: Boolean,
    onDismiss: () -> Unit,
    onPost: (caption: String, discountType: String?, discountValue: Long?) -> Unit
) {
    var caption by remember { mutableStateOf("") }
    var discountType by remember { mutableStateOf<String?>(null) }
    var discountValue by remember { mutableStateOf<Long?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showDiscountDialog by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = Str.cancel, tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showAddSheet = true }) {
                        Icon(Icons.Rounded.AddCircle, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.size(6.dp))
                        Text(Str.story_add_button, color = Color.White)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Image(
                        bitmap = photo.bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    val type = discountType
                    val value = discountValue
                    if (type != null && value != null) {
                        Row(
                            Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DiscountBadge(discountLabel(type, value))
                            IconButton(onClick = { discountType = null; discountValue = null }) {
                                Icon(Icons.Rounded.Close, contentDescription = Str.delete, tint = Color.White)
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = it.take(CAPTION_MAX) },
                        placeholder = { Text(Str.story_caption_hint) },
                        supportingText = {
                            Text("${caption.length}/$CAPTION_MAX".toPersianDigits())
                        },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onPost(caption.trim(), discountType, discountValue) },
                        enabled = !posting,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (posting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(Str.story_publish)
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            Text(
                Str.story_add_button,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            // More buttons come later; each is one row here.
            ListItem(
                headlineContent = { Text(Str.story_discount) },
                supportingContent = { Text(Str.story_discount_hint) },
                leadingContent = { Icon(Icons.Rounded.LocalOffer, contentDescription = null) },
                modifier = Modifier.padding(bottom = 24.dp).clickable {
                    showAddSheet = false
                    showDiscountDialog = true
                }
            )
        }
    }

    if (showDiscountDialog) {
        DiscountDialog(
            initialType = discountType ?: PERCENT,
            initialValue = discountValue,
            onDismiss = { showDiscountDialog = false },
            onConfirm = { type, value ->
                discountType = type
                discountValue = value
                showDiscountDialog = false
            }
        )
    }
}

@Composable
private fun DiscountDialog(
    initialType: String,
    initialValue: Long?,
    onDismiss: () -> Unit,
    onConfirm: (type: String, value: Long) -> Unit
) {
    var type by remember { mutableStateOf(initialType) }
    var text by remember { mutableStateOf(initialValue?.toString() ?: "") }
    val value = text.toLongOrNull()
    val valid = when (type) {
        PERCENT -> value != null && value in 1..100
        else -> value != null && value >= 1000
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Str.story_discount) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == PERCENT, onClick = { type = PERCENT }, label = { Text(Str.story_discount_type_percent) })
                    FilterChip(selected = type == AMOUNT, onClick = { type = AMOUNT }, label = { Text(Str.story_discount_type_amount) })
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter { it.isDigit() }.take(9) },
                    label = { Text(if (type == PERCENT) Str.story_discount_percent_label else Str.story_discount_amount_label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = text.isNotEmpty() && !valid,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { value?.let { onConfirm(type, it) } }, enabled = valid) { Text(Str.confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Str.cancel) } }
    )
}

package xyz.sattar.javid.proqueue.feature.showcase

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.core.ui.components.EmptyState
import xyz.sattar.javid.proqueue.core.ui.components.RectImageCropperDialog
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost
import xyz.sattar.javid.proqueue.core.ui.components.adaptivePagePadding
import xyz.sattar.javid.proqueue.core.ui.components.showToasty
import xyz.sattar.javid.proqueue.core.utils.mediaUrl
import xyz.sattar.javid.proqueue.core.utils.rememberImagePicker
import xyz.sattar.javid.proqueue.core.utils.toEnglishDigits
import xyz.sattar.javid.proqueue.core.utils.toImageBitmapOrNull
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.ShowcaseItemDto
import xyz.sattar.javid.proqueue.domain.model.showcase.ShowcaseFields
import xyz.sattar.javid.proqueue.domain.model.showcase.ShowcaseKind
import xyz.sattar.javid.proqueue.domain.model.showcase.ShowcaseShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import xyz.sattar.javid.proqueue.domain.model.showcase.ShowcaseStatus
import xyz.sattar.javid.proqueue.domain.model.showcase.statusEnum

/** What the item dialog is doing: adding a freshly cropped photo or editing a row. */
private sealed interface ItemDialog {
    data class Add(val kind: ShowcaseKind, val image: ByteArray) : ItemDialog
    data class Edit(val kind: ShowcaseKind, val item: ShowcaseItemDto) : ItemDialog
}

/** Portfolio photos and certificates the owner shows on their public page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowcaseScreen(
    viewModel: ShowcaseViewModel = koinViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val kind = state.selectedKind

    var rawPicked by remember { mutableStateOf<ByteArray?>(null) }
    var dialog by remember { mutableStateOf<ItemDialog?>(null) }
    var deleting by remember { mutableStateOf<ShowcaseItemDto?>(null) }

    LaunchedEffect(Unit) { viewModel.sendIntent(ShowcaseIntent.Load) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showToasty(it)
            viewModel.sendIntent(ShowcaseIntent.ClearMessage)
        }
    }

    val picker = rememberImagePicker(onSingleImagePicked = { rawPicked = it })

    rawPicked?.let { raw ->
        val bitmap = remember(raw) { try { raw.toImageBitmapOrNull() } catch (e: Exception) { null } }
        var shape by remember(raw) { mutableStateOf(kind.defaultShape) }
        if (bitmap != null) {
            RectImageCropperDialog(
                source = raw,
                bitmap = bitmap,
                aspectRatio = shape.aspectRatio,
                outputWidth = shape.outputWidth,
                outputHeight = shape.outputHeight,
                onDismiss = { rawPicked = null },
                onCropped = { cropped ->
                    rawPicked = null
                    dialog = ItemDialog.Add(kind, cropped)
                },
                controls = { ShapePicker(shape) { shape = it } }
            )
        } else {
            // Undecodable file: nothing to crop, so drop it rather than leave a dialog that can't open.
            LaunchedEffect(raw) { rawPicked = null }
        }
    }

    dialog?.let { current ->
        ItemFormDialog(
            dialog = current,
            onDismiss = { dialog = null },
            onConfirm = { fields ->
                dialog = null
                when (current) {
                    is ItemDialog.Add -> viewModel.sendIntent(ShowcaseIntent.Add(current.kind, current.image, fields))
                    is ItemDialog.Edit -> viewModel.sendIntent(ShowcaseIntent.Edit(current.kind, current.item.id, fields))
                }
            }
        )
    }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(Str.showcase_delete_confirm_title) },
            text = { Text(Str.showcase_delete_confirm_message) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    viewModel.sendIntent(ShowcaseIntent.Delete(kind, item.id))
                }) { Text(Str.delete, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(Str.cancel) } }
        )
    }

    Scaffold(
        snackbarHost = { ToastyHost(hostState = snackbarHostState) },
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                TopAppBar(
                    title = {
                        Text(
                            Str.showcase_title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = Str.back)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
                PrimaryTabRow(
                    selectedTabIndex = kind.ordinal,
                    containerColor = MaterialTheme.colorScheme.background
                ) {
                    ShowcaseKind.entries.forEach { tabKind ->
                        Tab(
                            selected = tabKind == kind,
                            onClick = { viewModel.sendIntent(ShowcaseIntent.SelectKind(tabKind)) },
                            text = {
                                Text(
                                    if (tabKind == ShowcaseKind.PORTFOLIO) Str.showcase_tab_portfolio
                                    else Str.showcase_tab_certificates
                                )
                            }
                        )
                    }
                }
                if (state.isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val items = state.items
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(168.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = adaptivePagePadding(), end = adaptivePagePadding(), top = 12.dp, bottom = 24.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "add", span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(
                            text = if (kind == ShowcaseKind.PORTFOLIO) Str.showcase_add_portfolio
                            else Str.showcase_add_certificate,
                            onClick = { picker.launch() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isBusy
                        )
                        // The reason an added photo doesn't show up straight away.
                        Text(
                            text = Str.showcase_approval_note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                when {
                    state.isLoading && items.isEmpty() -> item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    state.errorMessage != null && items.isEmpty() -> item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            EmptyState(
                                icon = Icons.Rounded.ErrorOutline,
                                title = Str.showcase_load_failed,
                                subtitle = state.errorMessage
                            )
                            TextButton(onClick = { viewModel.sendIntent(ShowcaseIntent.Load) }) {
                                Text(Str.retry_action)
                            }
                        }
                    }

                    items.isEmpty() -> item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Rounded.PhotoLibrary,
                            title = if (kind == ShowcaseKind.PORTFOLIO) Str.showcase_empty_portfolio
                            else Str.showcase_empty_certificates
                        )
                    }

                    else -> items(items, key = { it.id }) { item ->
                        val index = items.indexOf(item)
                        ShowcaseItemCard(
                            kind = kind,
                            item = item,
                            canMoveEarlier = index > 0,
                            canMoveLater = index < items.lastIndex,
                            enabled = !state.isBusy,
                            onMoveEarlier = { viewModel.sendIntent(ShowcaseIntent.Move(kind, item.id, -1)) },
                            onMoveLater = { viewModel.sendIntent(ShowcaseIntent.Move(kind, item.id, +1)) },
                            onEdit = { dialog = ItemDialog.Edit(kind, item) },
                            onDelete = { deleting = item }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShowcaseItemCard(
    kind: ShowcaseKind,
    item: ShowcaseItemDto,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    enabled: Boolean,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            AsyncImage(
                model = mediaUrl(item.thumbnail?.takeIf { it.isNotBlank() } ?: item.image),
                contentDescription = null,
                // Fit: items may be square, portrait or landscape; show all of each.
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(kind.aspectRatio)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip(item.statusEnum)
                if (item.statusEnum == ShowcaseStatus.REJECTED && item.rejectReason.isNotBlank()) {
                    Text(
                        text = Str.showcase_reject_reason(item.rejectReason),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                val primary = if (kind == ShowcaseKind.PORTFOLIO) item.caption else item.title
                if (primary.isNotBlank()) {
                    Text(
                        text = primary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (kind == ShowcaseKind.CERTIFICATES) {
                    val secondary = listOfNotNull(
                        item.issuer.takeIf { it.isNotBlank() },
                        item.year?.toString()?.toPersianDigits()
                    ).joinToString(" - ")
                    if (secondary.isNotBlank()) {
                        Text(
                            text = secondary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Row {
                        // Mirrored arrows: "earlier" points toward the start edge in both directions.
                        SmallIcon(Icons.AutoMirrored.Rounded.ArrowBack, Str.showcase_move_earlier, enabled && canMoveEarlier, onMoveEarlier)
                        SmallIcon(Icons.AutoMirrored.Rounded.ArrowForward, Str.showcase_move_later, enabled && canMoveLater, onMoveLater)
                    }
                    Row {
                        SmallIcon(Icons.Rounded.Edit, Str.showcase_edit_item, enabled, onEdit)
                        SmallIcon(Icons.Rounded.Delete, Str.showcase_delete_item, enabled, onDelete, MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) tint else tint.copy(alpha = 0.35f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun StatusChip(status: ShowcaseStatus) {
    val (container, content) = when (status) {
        ShowcaseStatus.APPROVED -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
        ShowcaseStatus.REJECTED -> Color(0xFFFFEBEE) to Color(0xFFC62828)
        ShowcaseStatus.PENDING -> Color(0xFFFFF3E0) to Color(0xFFE65100)
    }
    Surface(shape = RoundedCornerShape(8.dp), color = container) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/** Caption (portfolio) or title / issuer / year (certificate) form, for both add and edit. */
@Composable
private fun ItemFormDialog(
    dialog: ItemDialog,
    onDismiss: () -> Unit,
    onConfirm: (ShowcaseFields) -> Unit
) {
    val kind = when (dialog) {
        is ItemDialog.Add -> dialog.kind
        is ItemDialog.Edit -> dialog.kind
    }
    val initial = (dialog as? ItemDialog.Edit)?.item
    var caption by remember { mutableStateOf(initial?.caption.orEmpty()) }
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var issuer by remember { mutableStateOf(initial?.issuer.orEmpty()) }
    var year by remember { mutableStateOf(initial?.year?.toString().orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (dialog is ItemDialog.Edit) Str.showcase_edit_item
                else if (kind == ShowcaseKind.PORTFOLIO) Str.showcase_add_portfolio
                else Str.showcase_add_certificate
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (kind == ShowcaseKind.PORTFOLIO) {
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = it.take(300) },
                        label = { Text(Str.showcase_caption_label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(150) },
                        label = { Text(Str.showcase_cert_title_label) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = issuer,
                        onValueChange = { issuer = it.take(150) },
                        label = { Text(Str.showcase_cert_issuer_label) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it.toEnglishDigits().filter { c -> c in '0'..'9' }.take(4) },
                        label = { Text(Str.showcase_cert_year_label) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(ShowcaseFields(caption = caption, title = title, issuer = issuer, year = year.toIntOrNull()))
            }) { Text(Str.confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Str.cancel) } }
    )
}

/** Square, portrait or landscape: the frame the photo is cropped to. */
@Composable
private fun ShapePicker(selected: ShowcaseShape, onSelect: (ShowcaseShape) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 12.dp)
    ) {
        ShowcaseShape.entries.forEach { shape ->
            FilterChip(
                selected = shape == selected,
                onClick = { onSelect(shape) },
                label = { Text(shape.label) },
                colors = FilterChipDefaults.filterChipColors(
                    labelColor = Color.White,
                    selectedContainerColor = Color.White,
                    selectedLabelColor = Color.Black,
                ),
            )
        }
    }
}

package xyz.sattar.javid.proqueue.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.theme_appearance
import proqueue.composeapp.generated.resources.theme_dark
import proqueue.composeapp.generated.resources.theme_dark_hint
import proqueue.composeapp.generated.resources.theme_light
import proqueue.composeapp.generated.resources.theme_light_hint
import proqueue.composeapp.generated.resources.theme_preview_caption
import proqueue.composeapp.generated.resources.theme_system
import proqueue.composeapp.generated.resources.theme_system_hint
import xyz.sattar.javid.proqueue.core.prefs.PreferencesManager
import xyz.sattar.javid.proqueue.core.state.AppThemeMode
import xyz.sattar.javid.proqueue.core.state.ThemeStateHolder
import xyz.sattar.javid.proqueue.ui.theme.DarkBackground
import xyz.sattar.javid.proqueue.ui.theme.DarkBorder
import xyz.sattar.javid.proqueue.ui.theme.DarkBottomBar
import xyz.sattar.javid.proqueue.ui.theme.DarkOnSurfaceVariant
import xyz.sattar.javid.proqueue.ui.theme.DarkSurface
import xyz.sattar.javid.proqueue.ui.theme.LightBackground
import xyz.sattar.javid.proqueue.ui.theme.LightBorder
import xyz.sattar.javid.proqueue.ui.theme.LightBottomBar
import xyz.sattar.javid.proqueue.ui.theme.LightOnSurfaceVariant
import xyz.sattar.javid.proqueue.ui.theme.LightSurface
import xyz.sattar.javid.proqueue.ui.theme.PrimaryPurple

/**
 * The colours a schematic phone is painted with. Taken from the real theme
 * constants (ui/theme/Color.kt), never from MaterialTheme: the mockups must
 * show the *target* theme, not whichever one happens to be active right now.
 */
private data class MockPalette(
    val page: Color,
    val card: Color,
    val line: Color,
    val muted: Color,
    val bar: Color,
)

private val LightMock = MockPalette(
    page = LightBackground,
    card = LightSurface,
    line = LightBorder,
    muted = LightOnSurfaceVariant.copy(alpha = 0.35f),
    bar = LightBottomBar,
)

private val DarkMock = MockPalette(
    page = DarkBackground,
    card = DarkSurface,
    line = DarkBorder,
    muted = DarkOnSurfaceVariant.copy(alpha = 0.35f),
    bar = DarkBottomBar,
)

@Composable
fun ThemeScreen(onNavigateBack: () -> Unit) {
    val themeMode by ThemeStateHolder.themeMode.collectAsState()
    val scope = rememberCoroutineScope()

    // Applied the moment it's tapped (the whole app recolours behind the
    // mockup, which is itself the most honest preview), and persisted so the
    // choice survives a restart — same two calls the old bottom sheet made.
    fun select(mode: AppThemeMode) {
        ThemeStateHolder.setThemeMode(mode)
        scope.launch { PreferencesManager.setThemeMode(mode) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.theme_appearance),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Large live preview ──
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedContent(
                        targetState = themeMode,
                        transitionSpec = {
                            (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.94f))
                                .togetherWith(fadeOut(tween(180)))
                        },
                        label = "theme-preview"
                    ) { mode ->
                        ThemedPhone(mode = mode, width = 168.dp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(Res.string.theme_preview_caption),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }

            // ── The three choices ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThemeOption(
                    mode = AppThemeMode.LIGHT,
                    title = stringResource(Res.string.theme_light),
                    selected = themeMode == AppThemeMode.LIGHT,
                    onClick = { select(AppThemeMode.LIGHT) },
                    modifier = Modifier.weight(1f)
                )
                ThemeOption(
                    mode = AppThemeMode.DARK,
                    title = stringResource(Res.string.theme_dark),
                    selected = themeMode == AppThemeMode.DARK,
                    onClick = { select(AppThemeMode.DARK) },
                    modifier = Modifier.weight(1f)
                )
                ThemeOption(
                    mode = AppThemeMode.SYSTEM,
                    title = stringResource(Res.string.theme_system),
                    selected = themeMode == AppThemeMode.SYSTEM,
                    onClick = { select(AppThemeMode.SYSTEM) },
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = stringResource(
                    when (themeMode) {
                        AppThemeMode.LIGHT -> Res.string.theme_light_hint
                        AppThemeMode.DARK -> Res.string.theme_dark_hint
                        AppThemeMode.SYSTEM -> Res.string.theme_system_hint
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ThemeOption(
    mode: AppThemeMode,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val borderWidth by animateDpAsState(if (selected) 2.dp else 1.dp, label = "option-border")

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) primary.copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .border(
                borderWidth,
                if (selected) primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ThemedPhone(mode = mode, width = 64.dp)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (selected) primary else Color.Transparent)
                    .border(1.5.dp, if (selected) primary else MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

/**
 * A phone painted in [mode]'s colours. "System" is drawn as light on one half
 * and dark on the other — it genuinely is either, depending on the device.
 */
@Composable
private fun ThemedPhone(mode: AppThemeMode, width: Dp) {
    when (mode) {
        AppThemeMode.LIGHT -> PhoneMockup(LightMock, width)
        AppThemeMode.DARK -> PhoneMockup(DarkMock, width)
        AppThemeMode.SYSTEM -> Box {
            PhoneMockup(LightMock, width)
            Box(
                modifier = Modifier.drawWithContent {
                    // Physical left/right, not start/end: the split is a
                    // picture, it should look the same in either direction.
                    clipRect(left = size.width / 2f) { this@drawWithContent.drawContent() }
                }
            ) {
                PhoneMockup(DarkMock, width)
            }
        }
    }
}

/**
 * A schematic of the app's home screen — status bar, title, the purple hero
 * card, a few list rows and the floating bottom bar — built from plain boxes
 * so it scales cleanly from a 64dp thumbnail to the large preview.
 */
@Composable
private fun PhoneMockup(palette: MockPalette, width: Dp) {
    val unit = width / 64f // every measurement below is in 64dp-phone units
    val bezel = Color(0xFF16161A)

    Box(
        modifier = Modifier
            .width(width)
            .aspectRatio(0.5f)
            .clip(RoundedCornerShape(unit * 11f))
            .background(bezel)
            .padding(unit * 2.4f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(unit * 9f))
                .background(palette.page)
                .padding(horizontal = unit * 4f)
        ) {
            // notch + status bar
            Box(Modifier.fillMaxWidth().height(unit * 7f), contentAlignment = Alignment.Center) {
                Pill(color = bezel, w = unit * 14f, h = unit * 3f)
            }
            // title
            Pill(color = palette.muted, w = unit * 22f, h = unit * 3f)
            Spacer(Modifier.height(unit * 4f))

            // hero card in brand purple
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(unit * 20f)
                    .clip(RoundedCornerShape(unit * 4f))
                    .background(PrimaryPurple)
                    .padding(unit * 3.5f)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(unit * 2.5f)) {
                    Pill(color = Color.White.copy(alpha = 0.85f), w = unit * 18f, h = unit * 3f)
                    Pill(color = Color.White.copy(alpha = 0.5f), w = unit * 28f, h = unit * 2.4f)
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(unit * 7f)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.35f))
                )
            }
            Spacer(Modifier.height(unit * 4f))

            // list rows
            repeat(4) { i ->
                MockRow(palette, unit, accent = i == 0)
                Spacer(Modifier.height(unit * 2.5f))
            }

            Spacer(Modifier.weight(1f))

            // floating bottom bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = unit * 4f)
                    .height(unit * 9f)
                    .clip(RoundedCornerShape(unit * 4.5f))
                    .background(palette.bar),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { i ->
                    Box(
                        Modifier
                            .size(unit * 3.2f)
                            .clip(CircleShape)
                            .background(if (i == 0) PrimaryPurple else palette.muted)
                    )
                }
            }
        }
    }
}

@Composable
private fun MockRow(palette: MockPalette, unit: Dp, accent: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(unit * 3f))
            .background(palette.card)
            .border(unit * 0.3f, palette.line, RoundedCornerShape(unit * 3f))
            .padding(unit * 2.4f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(unit * 6f)
                .clip(CircleShape)
                .background(if (accent) PrimaryPurple.copy(alpha = 0.7f) else palette.muted)
        )
        Spacer(Modifier.width(unit * 2.5f))
        Column(verticalArrangement = Arrangement.spacedBy(unit * 1.5f)) {
            Pill(color = palette.muted.copy(alpha = 0.9f), w = unit * 20f, h = unit * 2.2f)
            Pill(color = palette.muted.copy(alpha = 0.5f), w = unit * 13f, h = unit * 1.8f)
        }
    }
}

@Composable
private fun Pill(color: Color, w: Dp, h: Dp) {
    Box(
        Modifier
            .width(w)
            .height(h)
            .clip(RoundedCornerShape(50))
            .background(color)
    )
}

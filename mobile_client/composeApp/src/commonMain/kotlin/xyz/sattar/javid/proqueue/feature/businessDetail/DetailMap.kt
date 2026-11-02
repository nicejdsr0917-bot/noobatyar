package xyz.sattar.javid.proqueue.feature.businessDetail

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Navigation
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ovh.plrapps.mapcompose.api.addMarker
import ovh.plrapps.mapcompose.api.scale
import ovh.plrapps.mapcompose.api.scrollTo
import ovh.plrapps.mapcompose.ui.MapUI
import xyz.sattar.javid.proqueue.core.ui.components.map.DisposeMapState
import xyz.sattar.javid.proqueue.core.ui.components.map.GeoPoint
import xyz.sattar.javid.proqueue.core.ui.components.map.MapAttribution
import xyz.sattar.javid.proqueue.core.ui.components.map.applyTileTint
import xyz.sattar.javid.proqueue.core.ui.components.map.newTileMapState
import xyz.sattar.javid.proqueue.core.ui.components.map.toNormalized
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.feature.booking.pressScale
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.Space
import xyz.sattar.javid.proqueue.feature.explore.BusinessPin
import xyz.sattar.javid.proqueue.ui.theme.LocalIsDarkTheme

/** Street level: a few blocks around the business, readable street names. */
private const val PREVIEW_ZOOM = 16.0

/**
 * A still map of where the business is, with the same pin as the explore
 * map. It does not pan or zoom inside the scrolling page: a tap anywhere
 * opens the full map screen with the user's distance.
 */
@Composable
internal fun MapPreview(business: Business, latitude: Double, longitude: Double, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val dark = LocalIsDarkTheme.current
    val state = remember(latitude, longitude) {
        newTileMapState(GeoPoint(latitude, longitude), PREVIEW_ZOOM, density)
    }
    DisposeMapState(state)
    LaunchedEffect(dark) { state.applyTileTint(dark) }
    LaunchedEffect(state) {
        val (x, y) = GeoPoint(latitude, longitude).toNormalized()
        state.addMarker("place", x, y, relativeOffset = Offset(-0.5f, -1f), clickable = false) {
            BusinessPin(business, selected = true)
        }
        // Tiles start loading on the first camera move after layout, as on the
        // explore map; the bubble sits above its point, so centre a bit lower.
        state.scrollTo(x, y, destScale = state.scale, animationSpec = snap(), screenOffset = Offset(-0.5f, -0.62f))
    }
    val interaction = remember { MutableInteractionSource() }
    val open = onOpen
    Box(
        modifier
            .fillMaxWidth()
            .height(176.dp)
            .pressScale(interaction, 0.985f)
            .clip(Radius.md)
            .semantics { contentDescription = "نقشه محل ${business.title}؛ برای باز کردن لمس کنید" }
    ) {
        MapUI(modifier = Modifier.fillMaxSize(), state = state)
        // Swallows every touch, so the map never steals the page's scroll.
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = open)
        )
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(Space.sm)
                .shadow(4.dp, CircleShape)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .padding(horizontal = Space.md, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.Navigation, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("مسیریابی", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        MapAttribution(Modifier.align(Alignment.BottomStart).padding(Space.sm))
    }
}

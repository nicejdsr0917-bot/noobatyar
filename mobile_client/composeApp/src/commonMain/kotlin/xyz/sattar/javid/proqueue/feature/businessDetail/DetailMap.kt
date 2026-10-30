package xyz.sattar.javid.proqueue.feature.businessDetail

import androidx.compose.foundation.layout.PaddingValues
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.map.GestureOptions
import org.maplibre.spatialk.geojson.Position
import xyz.sattar.javid.proqueue.core.ui.components.map.VectorMap
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Directions
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
import xyz.sattar.javid.proqueue.core.ui.components.map.GeoPoint
import xyz.sattar.javid.proqueue.core.ui.components.map.MapAttribution
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.feature.booking.pressScale
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.Space
import xyz.sattar.javid.proqueue.feature.explore.BusinessPin
import xyz.sattar.javid.proqueue.ui.theme.LocalIsDarkTheme

/** Street level: a few blocks around the business, readable street names. */
private const val PREVIEW_ZOOM = 15.5

/**
 * A still map of where the business is, with the same pin as the explore
 * map. It does not pan or zoom inside the scrolling page: a tap anywhere
 * opens the full map screen with the user's distance.
 */
@Composable
internal fun MapPreview(business: Business, latitude: Double, longitude: Double, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val dark = LocalIsDarkTheme.current
    val place = remember(latitude, longitude) { GeoPoint(latitude, longitude) }
    // A little below the centre, so the pin's bubble sits mid-frame.
    val camera = rememberCameraState(
        CameraPosition(target = Position(longitude, latitude), zoom = PREVIEW_ZOOM, padding = PaddingValues(top = 56.dp))
    )
    val interaction = remember { MutableInteractionSource() }
    val open = onOpen
    Box(
        modifier
            .fillMaxWidth()
            .height(176.dp)
            .pressScale(interaction, 0.985f)
            .clip(Radius.md)
            .semantics { contentDescription = "نقشهٔ محل ${business.title}؛ برای دیدن نقشه و فاصله لمس کنید" }
    ) {
        VectorMap(camera, dark, Modifier.fillMaxSize(), gestures = GestureOptions.AllDisabled)
        PinnedAt(camera, place, PinAnchor.Bottom) { BusinessPin(business, selected = true) }
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
            Icon(Icons.Outlined.Directions, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("مسیریابی", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        MapAttribution(Modifier.align(Alignment.BottomStart).padding(Space.sm))
    }
}

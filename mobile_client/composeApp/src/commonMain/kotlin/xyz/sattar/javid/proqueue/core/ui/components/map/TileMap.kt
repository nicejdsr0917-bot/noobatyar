package xyz.sattar.javid.proqueue.core.ui.components.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

data class GeoPoint(val latitude: Double, val longitude: Double)

/** Where maps open without a location, and the OSM fallback's credit (see [VectorMapStyle]). */
object MapTiles {
    const val ATTRIBUTION = "© OpenStreetMap · HOT"

    /** Where the map opens with no location: Tehran, at city level. */
    val DEFAULT_CENTER = GeoPoint(35.6997, 51.3380)
    const val DEFAULT_ZOOM = 12.0
}

/** Web Mercator, normalized to the [0..1] square: for drawing maths that wants flat space. */
fun GeoPoint.toNormalized(): Pair<Double, Double> {
    val x = (longitude + 180.0) / 360.0
    val latRad = latitude * PI / 180.0
    val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0
    return x.coerceIn(0.0, 1.0) to y.coerceIn(0.0, 1.0)
}

fun geoPointFromNormalized(x: Double, y: Double): GeoPoint {
    val longitude = x * 360.0 - 180.0
    val latitude = atan(sinh(PI * (1.0 - 2.0 * y))) * 180.0 / PI
    return GeoPoint(latitude, longitude)
}

/** Great-circle distance in km. */
fun GeoPoint.distanceKmTo(other: GeoPoint): Double {
    val r = 6371.0
    val dLat = (other.latitude - latitude) * PI / 180.0
    val dLng = (other.longitude - longitude) * PI / 180.0
    val a = sin(dLat / 2).pow(2) +
        cos(latitude * PI / 180.0) * cos(other.latitude * PI / 180.0) * sin(dLng / 2).pow(2)
    return 2 * r * asin(sqrt(a))
}

/** What shows behind the map before its style loads. */
fun mapBackground(dark: Boolean): Color = if (dark) Color(0xFF1F1F26) else Color(0xFFECEDE9)

/** Our address, signed in the map's corner next to the map provider's credit. */
const val MAP_SIGNATURE = "noobatyar.ir"

/**
 * The corner label of every map: our address on top in the accent colour,
 * the map provider's required credit under it. One chip, so the credit is
 * never hidden behind the signature.
 */
@Composable
fun MapAttribution(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = MAP_SIGNATURE,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = VectorMapStyle.attribution,
            // Starts with "©", a neutral character: in the app's RTL layout it
            // would otherwise jump to the end of the line.
            style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

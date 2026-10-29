package xyz.sattar.javid.proqueue.core.location

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import proqueue.composeapp.generated.resources.Res

@Serializable
data class IranCity(
    val id: Int,
    val name: String,
    val lat: Double,
    val lng: Double,
    val radiusKm: Double,
)

@Serializable
data class IranProvince(val id: Int, val name: String, val cities: List<IranCity>)

@Serializable
private data class IranCitiesFile(val provinces: List<IranProvince>)

/**
 * Iran's provinces and cities exactly as Divar lists them (ids, names, order),
 * with each city's centre. `files/iran_cities.json` is the same file the owner
 * app picks from and the backend validates `city_code` against.
 */
object IranCities {
    private var cache: List<IranProvince>? = null
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalResourceApi::class)
    suspend fun provinces(): List<IranProvince> = cache ?: json
        .decodeFromString<IranCitiesFile>(Res.readBytes("files/iran_cities.json").decodeToString())
        .provinces
        .also { cache = it }

    suspend fun city(id: Int): Pair<IranCity, IranProvince>? =
        provinces().firstNotNullOfOrNull { p -> p.cities.firstOrNull { it.id == id }?.let { it to p } }
}

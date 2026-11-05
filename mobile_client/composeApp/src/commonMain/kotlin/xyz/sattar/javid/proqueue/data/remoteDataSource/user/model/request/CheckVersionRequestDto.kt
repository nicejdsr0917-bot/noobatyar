package xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CheckVersionRequestDto(
    @SerialName("version_code") val versionCode: Int,
    // Without it the server compared this app with the owner app's versions
    // and asked people to update (versions/views.py _requested_app).
    @SerialName("app") val app: String = "CLIENT"
)

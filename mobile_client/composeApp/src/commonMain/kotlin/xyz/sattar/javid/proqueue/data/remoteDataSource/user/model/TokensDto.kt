package xyz.sattar.javid.proqueue.data.remoteDataSource.user.model

import kotlinx.serialization.Serializable

/** Body of the token-refresh response; see AuthPlugin. */
@Serializable
data class TokensDto(
    val access: String,
    val refresh: String
)

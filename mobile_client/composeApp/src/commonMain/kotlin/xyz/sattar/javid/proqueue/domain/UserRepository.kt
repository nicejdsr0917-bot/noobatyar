package xyz.sattar.javid.proqueue.domain

import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.domain.model.VersionInfo

/** The consumer app has no accounts; the only user-scoped call left is the update gate. */
interface UserRepository {
    suspend fun checkVersion(versionCode: Int): ApiResponse<VersionInfo>
}

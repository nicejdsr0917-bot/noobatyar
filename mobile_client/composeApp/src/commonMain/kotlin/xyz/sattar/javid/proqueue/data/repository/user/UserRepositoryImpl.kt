package xyz.sattar.javid.proqueue.data.repository.user

import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.UserApiService
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.CheckVersionRequestDto
import xyz.sattar.javid.proqueue.domain.UserRepository
import xyz.sattar.javid.proqueue.domain.model.VersionInfo

class UserRepositoryImpl(
    private val userApiService: UserApiService,
) : UserRepository {

    override suspend fun checkVersion(versionCode: Int): ApiResponse<VersionInfo> {
        return userApiService.checkVersion(CheckVersionRequestDto(versionCode = versionCode))
    }
}

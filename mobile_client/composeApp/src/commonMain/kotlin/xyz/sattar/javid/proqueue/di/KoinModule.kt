package xyz.sattar.javid.proqueue.di

import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import xyz.sattar.javid.proqueue.core.network.HttpClientFactory
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.ClientBusinessApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.KtorClientBusinessApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.device.DeviceApiService
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.UserApiService
import xyz.sattar.javid.proqueue.data.repository.business.BusinessRepositoryImpl
import xyz.sattar.javid.proqueue.data.repository.user.UserRepositoryImpl
import xyz.sattar.javid.proqueue.domain.BusinessRepository
import xyz.sattar.javid.proqueue.domain.UserRepository
import xyz.sattar.javid.proqueue.domain.usecase.push.SyncPushTokenUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.CheckVersionUseCase
import xyz.sattar.javid.proqueue.feature.businessDetail.ClientBusinessDetailViewModel
import xyz.sattar.javid.proqueue.feature.explore.ExploreViewModel
import xyz.sattar.javid.proqueue.feature.saved.SavedViewModel
import xyz.sattar.javid.proqueue.feature.version.VersionViewModel

val appModule: Module = module {

    // --- Network ---
    single<HttpClient> { HttpClientFactory.create() }
    single { UserApiService(get()) }
    single { DeviceApiService(get()) }
    single<ClientBusinessApi> { KtorClientBusinessApi(get()) }

    // --- Repositories ---
    single<UserRepository> { UserRepositoryImpl(get()) }
    single<BusinessRepository> { BusinessRepositoryImpl(get()) }

    // --- UseCases (version gate + push token) ---
    factory { CheckVersionUseCase(get()) }
    factory { SyncPushTokenUseCase(get()) }

    // --- ViewModels ---
    viewModel { VersionViewModel(get()) }
    viewModel { ExploreViewModel(get()) }
    viewModel { ClientBusinessDetailViewModel(get()) }
    viewModel { SavedViewModel(get()) }
}

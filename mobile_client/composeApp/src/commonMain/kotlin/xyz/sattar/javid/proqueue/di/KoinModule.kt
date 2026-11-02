package xyz.sattar.javid.proqueue.di

import xyz.sattar.javid.proqueue.data.remoteDataSource.promo.KtorPromoApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.promo.PromoApi
import xyz.sattar.javid.proqueue.feature.promo.PromoViewModel
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import xyz.sattar.javid.proqueue.core.location.CityCatalog
import xyz.sattar.javid.proqueue.core.location.IranCities
import xyz.sattar.javid.proqueue.core.network.HttpClientFactory
import xyz.sattar.javid.proqueue.core.prefs.ClientPreferences
import xyz.sattar.javid.proqueue.core.prefs.PlatformClientPreferences
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.BookingApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.KtorBookingApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.ClientBusinessApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.KtorClientBusinessApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.BusinessSocialApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.KtorBusinessSocialApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.device.DeviceApiService
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.UserApiService
import xyz.sattar.javid.proqueue.data.repository.business.BusinessRepositoryImpl
import xyz.sattar.javid.proqueue.data.repository.user.UserRepositoryImpl
import xyz.sattar.javid.proqueue.domain.BusinessRepository
import xyz.sattar.javid.proqueue.domain.UserRepository
import xyz.sattar.javid.proqueue.domain.usecase.push.SyncPushTokenUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.CheckVersionUseCase
import xyz.sattar.javid.proqueue.feature.appointments.AppointmentDetailViewModel
import xyz.sattar.javid.proqueue.feature.appointments.MyAppointmentsViewModel
import xyz.sattar.javid.proqueue.feature.auth.LoginViewModel
import xyz.sattar.javid.proqueue.feature.booking.BookingViewModel
import xyz.sattar.javid.proqueue.feature.profile.ProfileViewModel
import xyz.sattar.javid.proqueue.data.remoteDataSource.profile.KtorProfileApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.profile.ProfileApi
import xyz.sattar.javid.proqueue.feature.businessDetail.BusinessSocialViewModel
import xyz.sattar.javid.proqueue.feature.businessDetail.BusinessMapViewModel
import xyz.sattar.javid.proqueue.feature.businessDetail.ClientBusinessDetailViewModel
import xyz.sattar.javid.proqueue.feature.explore.ExploreViewModel
import xyz.sattar.javid.proqueue.feature.more.MyInsuranceViewModel
import xyz.sattar.javid.proqueue.feature.saved.SavedViewModel
import xyz.sattar.javid.proqueue.feature.version.VersionViewModel

val appModule: Module = module {

    // --- Network ---
    single<HttpClient> { HttpClientFactory.create() }
    single { UserApiService(get()) }
    single { DeviceApiService(get()) }
    single<ClientBusinessApi> { KtorClientBusinessApi(get()) }
    single<BookingApi> { KtorBookingApi(get()) }
    single<BusinessSocialApi> { KtorBusinessSocialApi(get()) }
    single<PromoApi> { KtorPromoApi(get()) }
    single<ProfileApi> { KtorProfileApi(get()) }

    // --- Platform seams ---
    single<ClientPreferences> { PlatformClientPreferences() }
    single<CityCatalog> { IranCities }

    // --- Repositories ---
    single<UserRepository> { UserRepositoryImpl(get()) }
    single<BusinessRepository> { BusinessRepositoryImpl(get()) }

    // --- UseCases (version gate + push token) ---
    factory { CheckVersionUseCase(get()) }
    factory { SyncPushTokenUseCase(get()) }

    // --- ViewModels ---
    viewModel { VersionViewModel(get()) }
    viewModel { ExploreViewModel(get(), get(), get()) }
    viewModel { ClientBusinessDetailViewModel(get(), get(), bookingApi = get()) }
    viewModel { BusinessMapViewModel(get(), get()) }
    viewModel { PromoViewModel(get()) }
    viewModel { BusinessSocialViewModel(get(), get()) }
    viewModel { SavedViewModel(get(), get()) }
    viewModel { BookingViewModel(get(), get(), get()) }
    viewModel { LoginViewModel(get(), get()) }
    viewModel { MyInsuranceViewModel(get(), get()) }
    viewModel { ProfileViewModel(get(), get(), get()) }
    viewModel { MyAppointmentsViewModel(get(), get()) }
    viewModel { AppointmentDetailViewModel(get(), get()) }
}

package xyz.sattar.javid.proqueue.di
import xyz.sattar.javid.proqueue.feature.explore.ExploreViewModel
import xyz.sattar.javid.proqueue.feature.businessDetail.ClientBusinessDetailViewModel
import xyz.sattar.javid.proqueue.feature.saved.SavedViewModel

import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import xyz.sattar.javid.proqueue.core.network.HttpClientFactory
import xyz.sattar.javid.proqueue.data.localDataSource.AppDatabase
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.UserApiService
import xyz.sattar.javid.proqueue.data.repository.appointment.AppointmentRepositoryImpl
import xyz.sattar.javid.proqueue.data.repository.business.BusinessRepositoryImpl
import xyz.sattar.javid.proqueue.data.repository.message.MessageRepositoryImpl
import xyz.sattar.javid.proqueue.data.repository.user.UserRepositoryImpl
import xyz.sattar.javid.proqueue.data.repository.visitor.VisitorRepositoryImpl
import xyz.sattar.javid.proqueue.domain.AppointmentRepository
import xyz.sattar.javid.proqueue.domain.BusinessRepository
import xyz.sattar.javid.proqueue.domain.MessageRepository
import xyz.sattar.javid.proqueue.domain.UserRepository
import xyz.sattar.javid.proqueue.domain.VisitorRepository
import xyz.sattar.javid.proqueue.domain.usecase.AddServiceCatalogItemUseCase
import xyz.sattar.javid.proqueue.domain.usecase.BusinessUpsertUseCase
import xyz.sattar.javid.proqueue.domain.usecase.CheckAppointmentConflictUseCase
import xyz.sattar.javid.proqueue.domain.usecase.CreateAppointmentUseCase
import xyz.sattar.javid.proqueue.domain.usecase.DeleteBusinessUseCase
import xyz.sattar.javid.proqueue.domain.usecase.DeleteVisitorUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GenerateReminderMessageUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetAllVisitorsUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetAppointmentByIdUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetAppointmentsForDateUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetTodayAppointmentsUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetServiceCatalogUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetPushLogSummaryUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetPushLogsUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetSmsLogSummaryUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetSmsLogsUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetVisitorByIdUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetWaitingQueueUseCase
import xyz.sattar.javid.proqueue.domain.usecase.MarkAppointmentCompletedUseCase
import xyz.sattar.javid.proqueue.domain.usecase.MarkAppointmentNoShowUseCase
import xyz.sattar.javid.proqueue.domain.usecase.RemoveAppointmentUseCase
import xyz.sattar.javid.proqueue.domain.usecase.SendMessageUseCase
import xyz.sattar.javid.proqueue.domain.usecase.UpdateAppointmentUseCase
import xyz.sattar.javid.proqueue.domain.usecase.UserLogoutUseCase
import xyz.sattar.javid.proqueue.domain.usecase.VisitorUpsertUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.CheckVersionUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.ClearTokenUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetUserProfileUseCase
import xyz.sattar.javid.proqueue.data.remoteDataSource.device.DeviceApiService
import xyz.sattar.javid.proqueue.domain.usecase.push.SyncPushTokenUseCase
import xyz.sattar.javid.proqueue.domain.usecase.push.UnregisterPushTokenUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.HasTokenUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.LoginUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.RegisterUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.ResetPasswordUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.SendOTPUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.VerifyOTPUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetMySubscriptionUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetMyEntitlementsUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetAddonsUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.BuyAddonUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetCurrentUserUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetPlansUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.CreatePaymentUseCase
import xyz.sattar.javid.proqueue.feature.version.VersionViewModel

val appModule: Module = module {

    // --- Network ---
    single<HttpClient> { HttpClientFactory.create() }
    single { UserApiService(get()) }
    single { DeviceApiService(get()) }
    single { xyz.sattar.javid.proqueue.data.remoteDataSource.business.BusinessApiService(get()) }
    single { xyz.sattar.javid.proqueue.data.remoteDataSource.visitor.VisitorApiService(get()) }
    single { xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.AppointmentApiService(get()) }
    single<UserRepository> { UserRepositoryImpl(get(), get()) }

    // --- DAOs ---
    single { get<AppDatabase>().businessDao() }
    single { get<AppDatabase>().visitorDao() }
    single { get<AppDatabase>().appointmentDao() }
    single { get<AppDatabase>().messageDao() }
    single { get<AppDatabase>().userDao() }

    // --- Repositories ---
    single<BusinessRepository> { BusinessRepositoryImpl(get(),get()) }
    single<VisitorRepository> { VisitorRepositoryImpl(get(), get()) }
    single<AppointmentRepository> { AppointmentRepositoryImpl(get(), get(), get(), get()) }
    single<MessageRepository> { MessageRepositoryImpl(get(), get()) }

    // --- User UseCases ---
    factory { RegisterUseCase(get()) }
    factory { LoginUseCase(get()) }
    factory { UserLogoutUseCase(get()) }
    factory { HasTokenUseCase() }
    factory { ClearTokenUseCase() }
    factory { SyncPushTokenUseCase(get()) }
    factory { UnregisterPushTokenUseCase(get()) }
    factory { CheckVersionUseCase(get()) }
    factory { GetUserProfileUseCase(get()) }
    factory { SendOTPUseCase(get()) }
    factory { VerifyOTPUseCase(get()) }
    factory { ResetPasswordUseCase(get()) }
    factory { GetMySubscriptionUseCase(get()) }
    factory { GetCurrentUserUseCase(get()) }
    factory { GetPlansUseCase(get()) }
    factory { CreatePaymentUseCase(get()) }
    factory { GetMyEntitlementsUseCase(get()) }
    factory { GetAddonsUseCase(get()) }
    factory { BuyAddonUseCase(get()) }

    // --- Business UseCases ---
    factory { xyz.sattar.javid.proqueue.domain.usecase.ObserveBusinessesUseCase(get()) }
    factory { xyz.sattar.javid.proqueue.domain.usecase.FetchBusinessesUseCase(get()) }
    factory { DeleteBusinessUseCase(get()) }
    factory { BusinessUpsertUseCase(get()) }
    factory { GetSmsLogsUseCase(get()) }
    factory { GetSmsLogSummaryUseCase(get()) }
    factory { GetPushLogsUseCase(get()) }
    factory { GetPushLogSummaryUseCase(get()) }
    factory { GetServiceCatalogUseCase(get()) }
    factory { AddServiceCatalogItemUseCase(get()) }

    // --- Appointment UseCases ---
    factory { GetWaitingQueueUseCase(get()) }
    factory { GetTodayAppointmentsUseCase(get()) }
    factory { CreateAppointmentUseCase(get(), get(), get(), get()) }
    factory { RemoveAppointmentUseCase(get()) }
    factory { MarkAppointmentCompletedUseCase(get()) }
    factory { MarkAppointmentNoShowUseCase(get()) }
    factory { GetAppointmentsForDateUseCase(get()) }
    factory { GetAppointmentByIdUseCase(get()) }
    factory { UpdateAppointmentUseCase(get()) }
    factory { CheckAppointmentConflictUseCase(get()) }
    factory { xyz.sattar.javid.proqueue.domain.usecase.SyncAppointmentsUseCase(get()) }
    factory { xyz.sattar.javid.proqueue.domain.usecase.GetDailyCountsUseCase(get()) }

    // --- Message UseCases ---
    factory { SendMessageUseCase(get()) }
    factory { GenerateReminderMessageUseCase() }

    // --- Visitor UseCases ---
    factory { VisitorUpsertUseCase(get()) }
    factory { GetAllVisitorsUseCase(get()) }
    factory { GetVisitorByIdUseCase(get()) }
    factory { DeleteVisitorUseCase(get(), get(), get()) }

    // --- ViewModels ---
    viewModel { VersionViewModel(get()) }
    viewModel { ExploreViewModel(get()) }
    viewModel { ClientBusinessDetailViewModel(get()) }
    viewModel { SavedViewModel(get()) }
}

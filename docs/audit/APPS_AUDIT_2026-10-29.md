# Apps architecture audit (2026-10-29, Opus, read-only)
Paths relative to composeApp/src/commonMain/kotlin/xyz/sattar/javid/proqueue/ unless front_client/.

## Key findings
- No commonTest / test deps in either app (kotlin-test in toml but unwired; no coroutines-test, no ktor MockEngine).
- Owner: repos are interfaces (domain/*Repository.kt), local sources interfaces. But *ApiService concrete on `expect object HttpClientFactory`; global singletons BusinessStateHolder (23 files), PreferencesManager, TokenManager, GlobalErrorManager read directly; BaseViewModel.doAsyncTask hard-codes Dispatchers.Default (core/ui/BaseViewModel.kt:69); domain exposes DTOs/ApiResponse (domain/BusinessRepository.kt:4-11,34-67; AppointmentRepository.kt:24; UserRepository.kt:5); mixed error handling, 111 `catch (e: Exception)`, no CancellationException rethrow.
- Client = fork of owner: 112 byte-identical files; 4 VMs use ~6 of ~25 BusinessRepository methods; dead visitor/appointment/message/sms/addon/payment code; repos depend on Room DAOs directly (di/KoinModule.kt:81-91); plain ViewModel+MutableStateFlow, 13 direct PreferencesManager calls.
- Owner cache risks: syncAppointments upserts, never prunes (AppointmentRepositoryImpl.kt:302-370) while queue/stats/history read Room (124-299); logout clears only user+subscription (UserRepositoryImpl.kt:69-72) → data survives account switch; getVisitorHistory(visitorId) without businessId (213, unused); VisitorDetailsViewModel.remoteMessages mutable field merged with cache (34-37,153) → cross-visitor race.

## Work packages
- O1 Test harness both apps: commonTest + kotlin-test, coroutines-test, ktor-client-mock, turbine; MainDispatcherRule. low, prereq.
- O2 DispatcherProvider in BaseViewModel (default Default) + injectable Clock; KoinModule. low, prereq.
- O3 Interfaces SelectedBusinessStore/AppPreferences/TokenStore over existing objects, inject via ctor (usecases user/*, GenerateReminderMessageUseCase:20, Calendar/CreateAppointment/Home/VisitorDetails/Messages/Notifications/User VMs). Objects stay default impls; App.kt untouched. med, prereq.
- O4 API interfaces (BusinessApi etc.) for 5 *ApiService, Ktor* impls; KoinModule:92-96. low, prereq.
- O5 Logout wipes all local sources (LocalCacheCleaner); syncAppointments replace-window (delete business+date range then upsert, in transaction); delete getVisitorHistory(visitorId). med; test first.
- O6 Domain models for SmsLog/PushLog/Promo/ServiceCatalog/DailyCount + mappers; AppResult<T> contract; runCatchingCancellable. med-high, one repo at a time.
- O7 Split HomeViewModel (12 deps; extract SubscriptionCoordinator), LastVisitorsViewModel 455, CreateAppointmentViewModel 377; screens CreateBusinessRoute (fn :330-1333), CreateAppointmentScreen 1328, HomeScreen 1217, ShareBusinessScreen 1093, SettingsScreen 1092. med, after O3.
- O8 Persian literals → strings.xml (ClaimBusinessScreen, ContactActions, MessagesViewModel, SubscriptionCard); URLs → AppConfig/Links (AboutUsScreen:171-245, VersionHandler:39, ShareCardKit:319). low.
- O9 VisitorDetailsViewModel remote messages keyed by (visitorId,businessId). low.
- C1 Delete dead owner code from client after grep; slim BusinessRepository (domain/BusinessRepository.kt:65-87). med, prereq, before C4.
- C2 Client VM testability: ClientPreferences, CityCatalog over IranCities, dispatcher; SavedViewModel own file (ExploreViewModel:44-74,151-190; ClientBusinessDetailViewModel:58,84; SavedScreen:58-72). low.
- C3 ClientBusinessApi interface. low.
- C4 :core-shared KMP module (network, DateTimeUtils, IranianBankDetector, StringExtensions, UI primitives, BaseViewModel, common DTOs). high, last.
- C5 Split ExploreScreen 918, ExploreMap 719, ClientBusinessDetailScreen 648; OWNER_LANDING_URL (DiscoveryUi.kt:85), AboutUsScreen:65-70 strings/URLs. low.
- F1 Single API client: payment-result pages' own BASE_URL/fetch; payAppointmentWithReceipt duplicates 401/403 & crashes on non-JSON (lib/api.ts:458-476 vs 170-217); merge two payment-result pages. low.
- F2 lib/auth.ts; 15 direct localStorage visitor_token reads; api.ts reads token itself. low-med.
- F3 Split book/page.tsx 630, checkout/[id]/page.tsx 564; lib/bookingIntent.ts; zustand+axios unused → use or remove. med.
- F4 lib/site.ts, lib/maps.ts constants. low.

## Ordering
Owner: O1 first; O2,O4,O8,O9 parallel; O2→O3→O7; O5 before O6. Client: C1→C3, C2 parallel, C5 independent, C4 last. Front: F1∥F4; F1→F2→F3. Across apps all parallel except O1 (both gradle) and C4.

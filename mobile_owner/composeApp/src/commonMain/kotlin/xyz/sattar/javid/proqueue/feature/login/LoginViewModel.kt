package xyz.sattar.javid.proqueue.feature.login

import xyz.sattar.javid.proqueue.core.i18n.Str
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.domain.usecase.user.LoginUseCase
import xyz.sattar.javid.proqueue.feature.login.LoginState.PartialState.*

class LoginViewModel(
    private val loginUseCase: LoginUseCase
) : BaseViewModel<LoginState, LoginState.PartialState, LoginEvent, LoginIntent>(
    initialState = LoginState()
) {

    override fun handleIntent(intent: LoginIntent): Flow<LoginState.PartialState> {
        return when (intent) {
            is LoginIntent.PhoneChanged -> flow { emit(PhoneChanged(intent.phone)) }
            is LoginIntent.PasswordChanged -> flow { emit(PasswordChanged(intent.password)) }
            LoginIntent.Submit -> onLoginClicked()
            LoginIntent.Register -> {
                sendEvent(LoginEvent.NavigateToRegister)
                flow {}
            }
            is LoginIntent.ForgetPassword -> flow {
                if (intent.phone.isEmpty()) {
                    emit(ValidationError(phoneError = Str.to_reset_your_password_enter, passwordError = null))
                } else {
                    sendEvent(LoginEvent.NavigateToForgetPassword(intent.phone))
                }
            }
        }
    }

    private fun onLoginClicked(): Flow<LoginState.PartialState> = flow {
        val phone = uiState.value.phone
        val password = uiState.value.password

        if (phone.isEmpty() || password.isEmpty()) {
            emit(ValidationError(
                phoneError = if (phone.isEmpty()) Str.enter_your_phone_number else null,
                passwordError = if (password.isEmpty()) Str.enter_your_password else null
            ))
            return@flow
        }

        emit(IsLoading(true))
        when (val response = loginUseCase(phone, password)) {
            is ApiResponse.Success -> {
                emit(LoginSuccess(response.data))
                sendEvent(LoginEvent.NavigateToHome)
                emit(IsLoading(false))
            }
            is ApiResponse.Error -> {
                sendEvent(LoginEvent.ShowToast(response.message))
                // LoginError already turns loading off. A trailing
                // IsLoading(false) here cleared loginError again in the
                // reducer, so the inline error (and the register hint under
                // it) never showed.
                emit(LoginError(response.message))
            }
        }
    }

    override fun reduceState(
        currentState: LoginState,
        partialState: LoginState.PartialState
    ): LoginState {
        return when (partialState) {
            is IsLoading -> currentState.copy(isLoading = partialState.isLoading, loginError = null)
            is PhoneChanged -> currentState.copy(phone = partialState.phone, loginError = null, phoneError = null)
            is PasswordChanged -> currentState.copy(password = partialState.password, loginError = null, passwordError = null)
            is ValidationError -> currentState.copy(
                phoneError = partialState.phoneError,
                passwordError = partialState.passwordError,
                isLoading = false
            )
            is LoginError -> currentState.copy(loginError = partialState.message, isLoading = false)
            is LoginSuccess -> currentState.copy(loggedInUser = partialState.user, isLoading = false)
        }
    }

    override fun createErrorState(message: String): LoginState.PartialState =
        LoginError(message)
}

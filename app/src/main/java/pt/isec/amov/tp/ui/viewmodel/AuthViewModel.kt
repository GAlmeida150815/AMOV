package pt.isec.amov.tp.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import pt.isec.amov.tp.R
import pt.isec.amov.tp.model.RegisterData
import pt.isec.amov.tp.model.User
import pt.isec.amov.tp.utils.AuthRepository
import pt.isec.amov.tp.utils.UserRepository

class AuthViewModel : ViewModel() {
    private val authRepo = AuthRepository()
    private val userRepo = UserRepository()

    var isLoading by mutableStateOf(false)
    var user by mutableStateOf<User?>(null)

    // --- Error states ---
    var loginError by mutableStateOf<Int?>(null)

    var registerError by mutableStateOf<Int?>(null)

    var showResendEmailButton by mutableStateOf(false)
    var resendResult by mutableStateOf<Result<Boolean>?>(null)

    init {
        val firebaseUser = authRepo.getCurrentUser()
        if (firebaseUser != null) {
            if (firebaseUser.isEmailVerified) {
                isLoading = true
                userRepo.getUserData(firebaseUser.uid) { fetchedUser ->
                    isLoading = false
                    user = fetchedUser
                }
            } else {
                authRepo.logout()
            }
        }
    }

    // --- Login ---
    fun login(email: String, pass: String, onSuccess: () -> Unit) {
        isLoading = true
        loginError = null
        showResendEmailButton = false
        resendResult = null

        authRepo.login(email, pass) { result ->
            result.fold(
                onSuccess = {
                    val uid = authRepo.getCurrentUser()?.uid
                    if (uid != null) {
                        userRepo.getUserData(uid) { fetchedUser ->
                            isLoading = false
                            if (fetchedUser != null) {
                                user = fetchedUser
                                onSuccess()
                            } else {
                                loginError = R.string.err_user_data_fetch
                                authRepo.logout()
                            }
                        }
                    } else {
                        isLoading = false
                        loginError = R.string.err_uid_null
                        authRepo.logout()
                    }
                },
                onFailure = { e ->
                    isLoading = false
                    if (e.message == "Email not verified") {
                        showResendEmailButton = true
                    } else {
                        loginError = R.string.err_login_generic
                    }
                }
            )
        }
    }

    // --- Resend Verification Email ---
    fun resendVerificationEmail(email: String, pass: String) {
        isLoading = true
        resendResult = null
        authRepo.login(email, pass) { loginResult ->
            loginResult.fold(
                onSuccess = {
                    authRepo.sendVerificationEmail { sendResult ->
                        authRepo.logout()
                        isLoading = false
                        resendResult = sendResult
                    }
                },
                onFailure = { e ->
                    isLoading = false
                    resendResult = Result.failure(e)
                }
            )
        }
    }

    // --- Reset Resend Result ---
    fun resetResendResult() {
        resendResult = null
    }

    // --- Register ---
    fun register(
        data: RegisterData,
        onSuccess: () -> Unit
    ) {
        isLoading = true
        loginError = null
        authRepo.register(data) { result ->
            isLoading = false
            registerError = null
            result.fold(
                onSuccess = { onSuccess() },
                onFailure = { e ->
                    registerError = R.string.err_register_generic
                }
            )
        }
    }

    // --- Logout ---
    fun logout() {
        authRepo.logout()
        user = null
    }

    // --- Reload User ---
//    fun reloadUser() {
//        val currentUser = authRepo.getCurrentUser()
//        if (currentUser != null) {
//            userRepo.getUserData(currentUser.uid) { fetchedUser ->
//                if (fetchedUser != null) {
//                    user = fetchedUser
//                }
//            }
//        }
//    }

    // --- Send Password Reset Email ---
    fun sendPasswordReset(email: String, onResult: (Result<Unit>) -> Unit) {
        isLoading = true
        authRepo.sendPasswordResetEmail(email) { result ->
            isLoading = false
            onResult(result)
        }
    }

    // --- Update Profile ---
    fun updateProfile(name: String, cancelCode: String?, onSuccess: () -> Unit) {
        val currentUser = user ?: return
        isLoading = true

        userRepo.updateUserProfile(currentUser.id, name, cancelCode) { result ->
            isLoading = false
            result.fold(
                onSuccess = {
                    user = user?.copy(name = name, cancellationCode = cancelCode)
                    onSuccess()
                },
                onFailure = { }
            )
        }
    }

    // --- Change Password ---
    fun changePassword(currentPass: String, newPass: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val currentUser = user ?: return
        isLoading = true

        authRepo.changePassword(currentUser.email, currentPass, newPass) { result ->
            isLoading = false
            result.fold(
                onSuccess = { onSuccess() },
                onFailure = { e -> onFailure(e.message ?: "Error") }
            )
        }
    }
}
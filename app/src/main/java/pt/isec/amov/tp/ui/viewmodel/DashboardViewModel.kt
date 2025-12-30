package pt.isec.amov.tp.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import pt.isec.amov.tp.R
import pt.isec.amov.tp.enums.UserRole
import pt.isec.amov.tp.model.User
import pt.isec.amov.tp.utils.AuthErrorType
import pt.isec.amov.tp.utils.AuthException
import pt.isec.amov.tp.utils.AuthRepository
import pt.isec.amov.tp.utils.UserRepository

class DashboardViewModel : ViewModel() {
    private val userRepo = UserRepository()
    private val authRepo = AuthRepository()
    private val _currentRole = MutableStateFlow(UserRole.PROTECTED)
    val currentRole = _currentRole.asStateFlow()

    fun initRole(user: User) {
        if (user.isMonitor && !user.isProtected) {
            _currentRole.value = UserRole.MONITOR
        } else {
            _currentRole.value = UserRole.PROTECTED
        }
    }

    fun setRole(role: UserRole) {
        _currentRole.value = role
    }

    // --- UI State ---
    var isLoading by mutableStateOf(false)
    var errorResId by mutableStateOf<Int?>(null)
    var generatedCode by mutableStateOf<String?>(null)

    // --- List of Associations ---
    var myProtecteds by mutableStateOf<List<User>>(emptyList())
        private set

    var myMonitors by mutableStateOf<List<User>>(emptyList())
        private set

    // --- Listener Registration ---
    private var myUserListener: ListenerRegistration? = null
    private var individualProtectedListeners = mutableListOf<ListenerRegistration>()

    var associationError by mutableStateOf<Int?>(null)

    // --- Start Listening ---
    fun startListening() {
        val currentUser = authRepo.getCurrentUser() ?: return
        stopListening()

        myUserListener = userRepo.addSnapshotListener(currentUser.uid) { updatedUser ->
            // 1. Atualizar lista de Monitores
            if (updatedUser.monitors.isNotEmpty()) {
                userRepo.getUsersByIds(updatedUser.monitors) { fullUsersList ->
                    myMonitors = fullUsersList
                }
            } else {
                myMonitors = emptyList()
            }

            // 2. Atualizar lista de Protegidos
            setupProtectedsRealtimeListeners(updatedUser.protecteds)
        }
    }

    // --- Setup Realtime Listeners for Protecteds ---
    private fun setupProtectedsRealtimeListeners(protectedIds: List<String>) {
        // 1. Remover listeners antigos
        individualProtectedListeners.forEach { it.remove() }
        individualProtectedListeners.clear()

        if (protectedIds.isEmpty()) {
            myProtecteds = emptyList()
            return
        }

        // 2. Criar uma lista temporária para guardar os dados
        val currentProtectedsMap = mutableMapOf<String, User>()

        // 3. Para CADA ID na lista, criar um listener
        protectedIds.forEach { protectedId ->
            val registration = userRepo.addSnapshotListener(protectedId) { userUpdated ->
                currentProtectedsMap[userUpdated.uid] = userUpdated
                myProtecteds = currentProtectedsMap.values.sortedBy { it.name }
            }
            individualProtectedListeners.add(registration)
        }
    }

    // --- Stop Listening for Associations ---
    fun stopListening() {
        myUserListener?.remove()
        myUserListener = null
        individualProtectedListeners.forEach { it.remove() }
        individualProtectedListeners.clear()
        myProtecteds = emptyList()
        myMonitors = emptyList()
    }

    // --- Generate Association Code ---
    fun generateCode() {
        isLoading = true
        associationError = null

        userRepo.generateAssociationCode { code ->
            isLoading = false
            if (code != null) {
                generatedCode = code
            } else {
                associationError = R.string.err_gen_code
            }
        }
    }

    // --- Associate with Monitor ---
    fun associateMonitor(code: String, onSuccess: () -> Unit) {
        if (code.length != 6) {
            associationError = R.string.err_assoc_length
            return
        }

        isLoading = true
        userRepo.associateWithMonitor(code) { result ->
            isLoading = false
            result.fold(
                onSuccess = {
                    onSuccess()
                },
                onFailure = { e ->
                    associationError = if (e is AuthException) {
                        when (e.type) {
                            AuthErrorType.CODE_NOT_FOUND -> R.string.err_assoc_code_invalid
                            AuthErrorType.CODE_EXPIRED -> R.string.err_code_expired
                            AuthErrorType.SELF_ASSOCIATION -> R.string.err_assoc_self
                            AuthErrorType.USER_NOT_LOGGED_IN -> R.string.err_auth_needed
                            AuthErrorType.INVALID_CODE_FORMAT -> R.string.err_assoc_length
                            else -> R.string.err_assoc_generic
                        }
                    } else {
                        R.string.err_assoc_generic
                    }
                }
            )
        }
    }

    // --- Clear Generated Code ---
    fun clearAssociationState() {
        generatedCode = null
        associationError = null
    }

    // --- Remove Association ---
    fun removeAssociation(
        otherUserId: String,
        amIMonitor: Boolean,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val currentUser = authRepo.getCurrentUser() ?: return
        isLoading = true

        val monitorId = if (amIMonitor) currentUser.uid else otherUserId
        val protectedId = if (amIMonitor) otherUserId else currentUser.uid

        userRepo.removeAssociation(monitorId, protectedId) { result ->
            isLoading = false
            result.fold(
                onSuccess = {
                    onSuccess()
                },
                onFailure = { e ->
                    onFailure(e.localizedMessage ?: "Unknown error")
                }
            )
        }
    }
}
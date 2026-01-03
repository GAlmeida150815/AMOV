package pt.isec.amov.tp.ui.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import pt.isec.amov.tp.R
import pt.isec.amov.tp.enums.UserRole
import pt.isec.amov.tp.model.Alert
import pt.isec.amov.tp.model.SafetyRule
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
    private val db = FirebaseFirestore.getInstance()
    private val auth = com.google.firebase.auth.FirebaseAuth.getInstance()

    var activeAlerts by mutableStateOf<Map<String, Alert>>(emptyMap())
        private set
    private var alertsListener: ListenerRegistration? = null

    var isServiceRunning by mutableStateOf(false)
    var isFirstLoad by mutableStateOf(true)
    var errorMessage by mutableStateOf<String?>(null)

    // --- MODIFICACIÓN: Estado para las reglas del propio usuario (modo lectura) ---
    var myRules by mutableStateOf<List<SafetyRule>>(emptyList())
    private var myRulesListener: ListenerRegistration? = null

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

        // --- MODIFICACIÓN: Listener para las reglas del protegido actual ---
        myRulesListener = db.collection("users").document(currentUser.uid)
            .collection("safety_rules")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    myRules = snapshot.toObjects(SafetyRule::class.java)
                }
            }

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

            startListeningToAlerts(updatedUser.protecteds)
        }
    }

    var alertHistory by mutableStateOf<List<Alert>>(emptyList())
        private set

    var selectedUser by mutableStateOf<User?>(null)
    private var selectedUserListener: ListenerRegistration? = null

    fun startTrackingUser(userId: String) {
        selectedUserListener?.remove()
        selectedUserListener = db.collection("users").document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                if (snapshot != null && snapshot.exists()) {
                    selectedUser = snapshot.toObject(User::class.java)?.apply {
                        uid = snapshot.id
                    }
                }
            }
    }

    fun loadAlertHistory() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("alerts")
            .whereEqualTo("protectedId", userId)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    alertHistory = snapshot.toObjects(Alert::class.java)
                }
            }
    }

    private fun startListeningToAlerts(protectedIds: List<String>) {
        alertsListener?.remove()
        if (protectedIds.isEmpty()) {
            activeAlerts = emptyMap()
            return
        }
        alertsListener = db.collection("alerts")
            .whereIn("protectedId", protectedIds)
            .whereEqualTo("resolved", false)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener

                activeAlerts = snapshot.toObjects(Alert::class.java)
                    .associateBy { it.protectedId }
            }
    }

    private fun setupProtectedsRealtimeListeners(protectedIds: List<String>) {
        individualProtectedListeners.forEach { it.remove() }
        individualProtectedListeners.clear()

        if (protectedIds.isEmpty()) {
            myProtecteds = emptyList()
            return
        }

        val currentProtectedsMap = mutableMapOf<String, User>()

        protectedIds.forEach { protectedId ->
            val registration = userRepo.addSnapshotListener(protectedId) { userUpdated ->
                currentProtectedsMap[userUpdated.uid] = userUpdated
                myProtecteds = currentProtectedsMap.values.sortedBy { it.name }
            }
            individualProtectedListeners.add(registration)
        }
    }

    var authorizedDays by mutableStateOf<Set<Int>>(setOf(1,2,3,4,5,6,7))
    var startHour by mutableStateOf(0)
    var endHour by mutableStateOf(23)

    fun loadPrivacySettings() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("userSettings").document(userId).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val daysList = snapshot.get("authorizedDays") as? List<Long>
                authorizedDays = daysList?.map { it.toInt() }?.toSet() ?: setOf(1,2,3,4,5,6,7)
                startHour = (snapshot.getLong("startHour") ?: 0).toInt()
                endHour = (snapshot.getLong("endHour") ?: 23).toInt()
            }
        }
    }

    fun savePrivacySettings(days: Set<Int>, start: Int, end: Int) {
        val userId = auth.currentUser?.uid ?: return
        val settings = mapOf(
            "authorizedDays" to days.toList(),
            "startHour" to start,
            "endHour" to end
        )
        db.collection("userSettings").document(userId).set(settings)
    }

    fun stopListening() {
        myUserListener?.remove()
        myUserListener = null
        myRulesListener?.remove()
        myRulesListener = null
        individualProtectedListeners.forEach { it.remove() }
        individualProtectedListeners.clear()
        alertsListener?.remove()
        alertsListener = null
        selectedUserListener?.remove()
        selectedUserListener = null
        myProtecteds = emptyList()
        myMonitors = emptyList()
        activeAlerts = emptyMap()
        myRules = emptyList()
    }

    fun resolveAlert(protectedId: String) {
        val alertId = activeAlerts[protectedId]?.id ?: return
        db.collection("alerts").document(alertId)
            .update("resolved", true)
            .addOnSuccessListener { Log.d("DashboardViewModel", "Alerta marcada como resuelta") }
            .addOnFailureListener { e -> Log.e("DashboardViewModel", "Error al resolver alerta: ${e.message}") }
    }

    fun updateTimeWindow(days: Set<Int>, startH: Int, endH: Int) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).update(
            mapOf(
                "authorizedDays" to days.toList(),
                "startHour" to startH,
                "endHour" to endH
            )
        )
    }

    fun generateCode() {
        isLoading = true
        associationError = null
        userRepo.generateAssociationCode { code ->
            isLoading = false
            if (code != null) generatedCode = code
            else associationError = R.string.err_gen_code
        }
    }

    fun associateMonitor(code: String, onSuccess: () -> Unit) {
        if (code.length != 6) {
            associationError = R.string.err_assoc_length
            return
        }
        isLoading = true
        userRepo.associateWithMonitor(code) { result ->
            isLoading = false
            result.fold(
                onSuccess = { onSuccess() },
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
                    } else R.string.err_assoc_generic
                }
            )
        }
    }

    fun clearAssociationState() {
        generatedCode = null
        associationError = null
    }

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
                onSuccess = { onSuccess() },
                onFailure = { e -> onFailure(e.localizedMessage ?: "Unknown error") }
            )
        }
    }

    var alertsForSelectedProtected by mutableStateOf<List<Map<String, Any>>>(emptyList())

    fun fetchAlertsForProtected(protectedId: String) {
        db.collection("alerts")
            .whereEqualTo("protectedId", protectedId)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                alertsForSelectedProtected = snapshot?.documents?.map { doc ->
                    doc.data?.plus("id" to doc.id) ?: emptyMap()
                } ?: emptyList()
            }
    }

    fun resetMonitoringState() {
        isFirstLoad = true
        isServiceRunning = false
        errorMessage = null
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
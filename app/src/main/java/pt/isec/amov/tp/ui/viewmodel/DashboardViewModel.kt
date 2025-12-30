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

            startListeningToAlerts(updatedUser.protecteds)
        }
    }
    var alertHistory by mutableStateOf<List<Alert>>(emptyList())
        private set

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

                // Mapeamos el ID del protegido a su alerta activa
                activeAlerts = snapshot.toObjects(Alert::class.java)
                    .associateBy { it.protectedId }
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
    var authorizedDays by mutableStateOf<Set<Int>>(setOf(1,2,3,4,5,6,7)) // Por defecto todos
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

    // --- Stop Listening for Associations ---
    fun stopListening() {
        myUserListener?.remove()
        myUserListener = null
        individualProtectedListeners.forEach { it.remove() }
        individualProtectedListeners.clear()
        alertsListener?.remove()
        alertsListener = null
        myProtecteds = emptyList()
        myMonitors = emptyList()
        activeAlerts = emptyMap()
    }
    fun resolveAlert(protectedId: String) {
        // Buscamos la alerta activa de este protegido
        val alertId = activeAlerts[protectedId]?.id ?: return

        db.collection("alerts").document(alertId)
            .update("resolved", true)
            .addOnSuccessListener {
                Log.d("DashboardViewModel", "Alerta marcada como resuelta")
            }
            .addOnFailureListener { e ->
                Log.e("DashboardViewModel", "Error al resolver alerta: ${e.message}")
            }
    }
    fun updateTimeWindow(days: Set<Int>, startH: Int, endH: Int) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).update(
            mapOf(
                "authorizedDays" to days.toList(), // Lista de días (1=Lunes, 7=Domingo)
                "startHour" to startH,
                "endHour" to endH
            )
        ).addOnSuccessListener {
            // Opcional: Toast de éxito
        }
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

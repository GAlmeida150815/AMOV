package pt.isec.ans.safetysec.model

<<<<<<< Updated upstream:app/src/main/java/pt/isec/ans/safetysec/model/SafetyRule.kt
class SafetyRule(
    var name: String,
    var description: String,
    var isActive: Boolean
) {

=======
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import pt.isec.amov.tp.enums.RuleType

data class SafetyRule(
    @DocumentId
    val id: String = "",

    val monitorId: String = "",
    val monitorName: String = "",

    // --- Configuração ---
    val name: String = "",
    val description: String = "",
    val type: RuleType = RuleType.GEOFENCING,
    val params: Map<String, Any> = emptyMap(),

    @get:PropertyName("authorized")
    @set:PropertyName("authorized")
    var isAuthorized: Boolean = false,

    val timeWindows: List<TimeWindow> = emptyList(),

    @ServerTimestamp
    val createdAt: Timestamp? = null
) {
    fun shouldCheckNow(): Boolean {
        if (!isAuthorized) return false

        if (timeWindows.isEmpty()) return true

        return timeWindows.any { it.isActiveNow() }
    }
>>>>>>> Stashed changes:app/src/main/java/pt/isec/amov/tp/model/SafetyRule.kt
}
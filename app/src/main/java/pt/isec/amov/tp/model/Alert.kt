package pt.isec.amov.tp.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ServerTimestamp
import pt.isec.amov.tp.enums.RuleType

data class Alert(
    @DocumentId
    val id: String = "",

    val ruleId: String? = null,
    val type: RuleType = RuleType.PANIC_BUTTON,

    val protectedId: String = "",

    val location: GeoPoint? = null,
    val speed: Float? = null,
    val batteryLevel: Int? = null,

    val videoUrl: String? = null,

    @ServerTimestamp
    val timestamp: Timestamp? = null,

    val resolved: Boolean = false,
    val resolvedBy: String? = null,
    val notes: String? = null
)
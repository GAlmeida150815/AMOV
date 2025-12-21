package pt.isec.amov.tp.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ServerTimestamp

data class Alert(
    @DocumentId
    val id: String = "",

    val senderId: String = "",
    val senderName: String = "",

    val type: AlertType = AlertType.SOS,

    val location: GeoPoint? = null,

    @ServerTimestamp
    val timestamp: Timestamp? = null,

    val resolved: Boolean = false
)

enum class AlertType {
    SOS,
    FALL_DETECTION,
    zone_exit
    //TODO: adicionar mais tipos de alertas
}
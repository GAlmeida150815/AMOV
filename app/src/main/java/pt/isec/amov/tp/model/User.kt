package pt.isec.amov.tp.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.PropertyName

data class User(
    @DocumentId
    var uid: String = "",
    val id: String = "",
    val name: String = "",
    val email: String = "",

    @get:PropertyName("monitor")
    @set:PropertyName("monitor")
    var isMonitor: Boolean = false,
    @get:PropertyName("protected")
    @set:PropertyName("protected")
    var isProtected: Boolean = false,

    // Para segurança e alertas
    val cancellationCode: String? = null,
    val mfaSecret: String? = null,

    // Associações (IDs de outros utilizadores)
    val monitors: List<String> = emptyList(),
    val protecteds: List<String> = emptyList(),

    // Localização
    val location: GeoPoint? = null,
    val lastUpdate: Timestamp? = null
)
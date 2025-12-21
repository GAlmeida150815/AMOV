package pt.isec.amov.tp.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class AssociationCode(
    @DocumentId
    val code: String = "",

    val monitorId: String = "",

    @ServerTimestamp
    val createdAt: Timestamp? = null
)
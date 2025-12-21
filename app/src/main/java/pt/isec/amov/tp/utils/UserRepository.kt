package pt.isec.amov.tp.utils

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import pt.isec.amov.tp.model.AssociationCode
import pt.isec.amov.tp.model.User
import kotlin.random.Random

class UserRepository {
    // --- Firebase Instances ---
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // --- Get User Data from Firestore ---
    fun getUserData(uid: String, onResult: (User?) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val user = document.toObject(User::class.java)
                    onResult(user)
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener {
                onResult(null)
            }
    }

    // --- Generate Association Code for Monitor ---
    fun generateAssociationCode(onResult: (String?) -> Unit) {
        val user = auth.currentUser ?: return

        val code = Random.nextInt(100000, 999999).toString()

        val associationCode = AssociationCode(
            monitorId = user.uid
        )
        db.collection("codes").document(code).set(associationCode)
            .addOnSuccessListener { onResult(code) }
            .addOnFailureListener { onResult(null) }
    }

    // --- Associate Protected with Monitor using Code ---
    fun associateWithMonitor(code: String, onResult: (Result<String>) -> Unit) {
        val protectedUser = auth.currentUser
        if (protectedUser == null) {
            onResult(Result.failure(AuthException(AuthErrorType.USER_NOT_LOGGED_IN)))
            return
        }

        val codeRef = db.collection("codes").document(code)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(codeRef)

            // 1. Validar Código
            if (!snapshot.exists()) {
                throw AuthException(AuthErrorType.CODE_NOT_FOUND)
            }

            val associationCode = snapshot.toObject(AssociationCode::class.java)
                ?: throw AuthException(AuthErrorType.GENERIC_ERROR, "Error parsing code")

            // 1.5 Verificar se o código expirou (5 minutos)
            val timestamp = associationCode.createdAt
            if (timestamp != null) {
                val now = System.currentTimeMillis()
                val codeTime = timestamp.toDate().time
                val diff = now - codeTime

                if (diff > 300000) {
                    transaction.delete(codeRef)
                    throw AuthException(AuthErrorType.CODE_EXPIRED)
                }
            }

            val monitorId = snapshot.getString("monitorId")
            if (monitorId == null || monitorId == protectedUser.uid) {
                throw AuthException(AuthErrorType.SELF_ASSOCIATION)
            }

            // 2. Atualizar o PROTEGIDO (adicionar monitor à lista)
            val protectedRef = db.collection("users").document(protectedUser.uid)
            transaction.update(protectedRef, "monitors", FieldValue.arrayUnion(monitorId))

            // 3. Atualizar o MONITOR (adicionar protegido à lista)
            val monitorRef = db.collection("users").document(monitorId)
            transaction.update(monitorRef, "protecteds", FieldValue.arrayUnion(protectedUser.uid))

            // 4. Apagar o código (One-Time Password)
            transaction.delete(codeRef)

            // Retorna o ID do monitor para confirmar sucesso
            monitorId
        }.addOnSuccessListener { monitorId ->
            onResult(Result.success(monitorId))
        }.addOnFailureListener { e ->
            if (e is AuthException) {
                onResult(Result.failure(e))
            } else {
                onResult(Result.failure(AuthException(AuthErrorType.GENERIC_ERROR, e.message)))
            }
        }
    }

    // --- Users List ---
    fun setUsersListener(userIds: List<String>, onUpdate: (List<User>) -> Unit): ListenerRegistration? {
        if (userIds.isEmpty()) {
            onUpdate(emptyList())
            return null
        }
        return db.collection("users")
            .whereIn(FieldPath.documentId(), userIds.take(10))
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    onUpdate(snapshot.toObjects(User::class.java))
                }
            }
    }

    // --- Update User Profile ---
    fun updateUserProfile(uid: String, name: String, cancelCode: String?, onResult: (Result<Unit>) -> Unit) {
        val updates = mutableMapOf<String, Any>("name" to name)
        if (cancelCode != null) {
            updates["cancellationCode"] = cancelCode
        }

        db.collection("users").document(uid).update(updates)
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    // --- Remove Association ---
    fun removeAssociation(monitorId: String, protectedId: String, onResult: (Result<Unit>) -> Unit) {
        if (monitorId.isBlank() || protectedId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Invalid User ID (Empty)")))
            return
        }

        val batch = db.batch()

        val monitorRef = db.collection("users").document(monitorId)
        val protectedRef = db.collection("users").document(protectedId)

        // 1. Remover o Protegido da lista 'protecteds' do Monitor
        batch.update(monitorRef, "protecteds", FieldValue.arrayRemove(protectedId))

        // 2. Remover o Monitor da lista 'monitors' do Protegido
        batch.update(protectedRef, "monitors", FieldValue.arrayRemove(monitorId))

        // Executar ambas as operações
        batch.commit()
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    // --- Listen for User Document Changes ---
    fun addSnapshotListener(userId: String, onUserUpdated: (User) -> Unit): ListenerRegistration {
        return db.collection("users").document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener

                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(User::class.java)
                    user?.uid = snapshot.id
                    if (user != null) {
                        onUserUpdated(user)
                    }
                }
            }
    }

    // --- Get Users by IDs ---
    fun getUsersByIds(ids: List<String>, onResult: (List<User>) -> Unit) {
        if (ids.isEmpty()) {
            onResult(emptyList())
            return
        }

        db.collection("users")
            .whereIn(com.google.firebase.firestore.FieldPath.documentId(), ids)
            .get()
            .addOnSuccessListener { result ->
                val usersList = result.map { doc ->
                    val u = doc.toObject(User::class.java)
                    u.uid = doc.id
                    u
                }
                onResult(usersList)
            }
            .addOnFailureListener { onResult(emptyList()) }
    }
}
package pt.isec.amov.tp.utils

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import pt.isec.amov.tp.model.RegisterData
import pt.isec.amov.tp.model.User

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // --- Login ---
    fun login(email: String, pass: String, onResult: (Result<Boolean>) -> Unit) {
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener { authResult ->
                // --- MFA ---
                val user = authResult.user

                if (user != null && !user.isEmailVerified) {
                    auth.signOut()
                    onResult(Result.failure(Exception("Email not verified")))
                } else
                    onResult(Result.success(true))
            }
            .addOnFailureListener { e ->
                onResult(Result.failure(e))
            }
    }

    // --- Verification of Email ---
    fun sendVerificationEmail(onResult: (Result<Boolean>) -> Unit) {
        val user = auth.currentUser
        user?.sendEmailVerification()
            ?.addOnSuccessListener { onResult(Result.success(true)) }
            ?.addOnFailureListener { e -> onResult(Result.failure(e)) }
    }

    // --- Register ---
    fun register(
        data: RegisterData,
        onResult: (Result<Boolean>) -> Unit
    ) {
        // 1. Criar conta no Firebase Auth
        auth.createUserWithEmailAndPassword(data.email, data.pass)
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid
                if (uid != null) {
                    // 2. Criar objeto User
                    val newUser = User(
                        id = uid,
                        name = data.name,
                        email = data.email,
                        isMonitor = data.isMonitor,
                        isProtected = data.isProtected,
                        cancellationCode = data.cancelCode
                    )

                    // 3. Gravar na Firestore (Coleção "users")
                    db.collection("users").document(uid).set(newUser)
                        .addOnSuccessListener {
                            // --- MFA ---
                            authResult.user?.sendEmailVerification()

                            auth.signOut()
                            onResult(Result.success(true))
                        }
                        .addOnFailureListener { e ->
                            auth.currentUser?.delete()
                            onResult(Result.failure(e))
                        }
                }
            }
            .addOnFailureListener { e ->
                onResult(Result.failure(e))
            }
    }

    // --- Logout ---
    fun logout() {
        auth.signOut()
    }

    // --- Get Current User ---
    fun getCurrentUser() = auth.currentUser

    // --- Send Password Reset Email ---
    fun sendPasswordResetEmail(email: String, onResult: (Result<Unit>) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    // --- Change Password ---
    fun changePassword(email: String, currentPass: String, newPass: String, onResult: (Result<Unit>) -> Unit) {
        val user = auth.currentUser ?: return
        val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, currentPass)

        // 1. Re-autenticar o utilizador
        user.reauthenticate(credential).addOnSuccessListener {
            // 2. Atualizar a password
            user.updatePassword(newPass).addOnSuccessListener {
                onResult(Result.success(Unit))
            }.addOnFailureListener { onResult(Result.failure(it)) }
        }.addOnFailureListener {
            // Falha na re-autenticação
            onResult(Result.failure(AuthException(AuthErrorType.INVALID_CREDENTIALS)))
        }
    }
}
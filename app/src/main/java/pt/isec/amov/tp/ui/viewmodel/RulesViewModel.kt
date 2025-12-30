package pt.isec.amov.tp.ui.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import pt.isec.amov.tp.model.SafetyRule
import pt.isec.amov.tp.model.User

class RulesViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    var isLoading by mutableStateOf(false)
        private set

    // --- Add rule to protected ---
    fun addRuleToProtected(
        protectedId: String,
        rule: SafetyRule,
        onResult: (Boolean) -> Unit
    ) {
        val currentUser = auth.currentUser ?: return
        isLoading = true

        val finalRule = rule.copy(
            monitorId = currentUser.uid,
            monitorName = currentUser.displayName ?: "Monitor",
            createdAt = Timestamp.now(),
            isAuthorized = false
        )

        db.collection("users").document(protectedId)
            .collection("safety_rules")
            .add(finalRule)
            .addOnSuccessListener {
                isLoading = false
                onResult(true)
            }
            .addOnFailureListener {
                isLoading = false
                onResult(false)
            }
    }

    // -- Delete rule ---
    fun deleteRule(protectedId: String, ruleId: String) {
        db.collection("users").document(protectedId)
            .collection("safety_rules").document(ruleId)
            .delete()
    }

    // --- Get rules for protected ---
    fun getRulesForProtected(protectedId: String, monitorId: String? = null, onUpdate: (List<SafetyRule>) -> Unit) {
        val collectionRef = db.collection("users").document(protectedId).collection("safety_rules")

        val query: Query = if (monitorId != null) {
            collectionRef.whereEqualTo("monitorId", monitorId)
        } else {
            collectionRef
        }

        query.addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null) return@addSnapshotListener

            val rules = snapshot.toObjects(SafetyRule::class.java)
            onUpdate(rules)
        }
    }

    // --- Get protected user data ---
    fun getProtectedUser(userId: String, onResult: (User?) -> Unit) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                val user = document.toObject(User::class.java)
                user?.uid = document.id
                onResult(user)
            }
            .addOnFailureListener {
                onResult(null)
            }
    }

    // --- Toggle rule authorization ---
    fun toggleRuleAuthorization(userId: String, ruleId: String, newStatus: Boolean) {
        db.collection("users").document(userId)
            .collection("safety_rules").document(ruleId)
            .update("authorized", newStatus)
            .addOnFailureListener { e ->
                Log.e("RulesViewModel", "Error toggling rule", e)
            }
    }

    // --- Update rule ---
    fun updateRule(
        protectedId: String,
        rule: SafetyRule,
        onResult: (Boolean) -> Unit
    ) {
        if (rule.id.isEmpty()) {
            onResult(false)
            return
        }

        db.collection("users").document(protectedId)
            .collection("safety_rules").document(rule.id)
            .set(rule)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }
}
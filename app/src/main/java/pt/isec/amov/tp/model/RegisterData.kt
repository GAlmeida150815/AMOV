package pt.isec.amov.tp.model

import android.util.Patterns
import pt.isec.amov.tp.R

data class RegisterData(
    val name: String,
    val email: String,
    val pass: String,
    val isMonitor: Boolean,
    val isProtected: Boolean,
    val cancelCode: String?
) {
    fun validate(confirmPass: String): Map<String, Int> {
        val errors = mutableMapOf<String, Int>()

        // Nome
        if (name.isBlank()) {
            errors["name"] = R.string.err_field_empty
        }

        // Email
        if (email.isBlank()) {
            errors["email"] = R.string.err_field_empty
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            errors["email"] = R.string.err_invalid_email
        }

        // Password
        if (pass.isBlank()) {
            errors["password"] = R.string.err_field_empty
        } else if (pass.length < 6) {
            errors["password"] = R.string.err_password_short
        }

        // Confirmar Password
        if (confirmPass.isBlank()) {
            errors["confirmPassword"] = R.string.err_field_empty
        } else if (pass != confirmPass) {
            errors["confirmPassword"] = R.string.err_passwords_mismatch
        }

        // Perfil (Role)
        if (!isMonitor && !isProtected) {
            errors["role"] = R.string.err_field_empty
        }

        // Código de Cancelamento
        if (isProtected) {
            if (cancelCode.isNullOrBlank()) {
                errors["code"] = R.string.err_field_empty
            } else if (cancelCode.length !in 4..6) {
                errors["code"] = R.string.err_cancel_code_invalid
            }
        }

        return errors
    }
}
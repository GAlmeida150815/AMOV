package pt.isec.amov.tp.utils

enum class AuthErrorType {
    USER_NOT_LOGGED_IN,
    CODE_NOT_FOUND,
    SELF_ASSOCIATION,
    INVALID_CODE_FORMAT,
    GENERIC_ERROR,
    CODE_EXPIRED,
    NETWORK_ERROR,
    EMAIL_NOT_VERIFIED,
    INVALID_CREDENTIALS
}

class AuthException(val type: AuthErrorType, message: String? = null) : Exception(message)
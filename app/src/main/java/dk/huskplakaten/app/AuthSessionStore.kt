package dk.huskplakaten.app

import android.content.Context
import java.util.UUID

data class AuthSession(
    val userId: String,
    val email: String,
    val billingActive: Boolean
)

class AuthSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("auth_session", Context.MODE_PRIVATE)

    fun restore(): AuthSession? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        val billingActive = prefs.getBoolean(KEY_BILLING_ACTIVE, false)
        return AuthSession(userId = userId, email = email, billingActive = billingActive)
    }

    fun loginWithEmail(email: String, password: String): Result<AuthSession> {
        if (!email.contains("@")) return Result.failure(IllegalArgumentException("Ugyldig email."))
        if (password.length < 6) return Result.failure(IllegalArgumentException("Kodeord skal mindst være 6 tegn."))
        val userId = "email_${UUID.nameUUIDFromBytes(email.lowercase().toByteArray())}"
        return Result.success(
            AuthSession(
                userId = userId,
                email = email.trim(),
                billingActive = prefs.getBoolean(KEY_BILLING_ACTIVE, false)
            ).also { save(it) }
        )
    }

    fun loginWithGooglePlaceholder(): AuthSession {
        val email = "google.user@placeholder.local"
        val session = AuthSession(
            userId = "google_${UUID.randomUUID()}",
            email = email,
            billingActive = prefs.getBoolean(KEY_BILLING_ACTIVE, false)
        )
        save(session)
        return session
    }

    fun setBillingActive(active: Boolean): AuthSession? {
        val existing = restore() ?: return null
        val updated = existing.copy(billingActive = active)
        save(updated)
        return updated
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun save(session: AuthSession) {
        prefs.edit()
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_EMAIL, session.email)
            .putBoolean(KEY_BILLING_ACTIVE, session.billingActive)
            .apply()
    }

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_BILLING_ACTIVE = "billing_active"
    }
}

package com.example.resqplug.activity7

import android.util.Patterns

/**
 * Presenter for Screen 1: Activity 7 Authentication Gate (Retro 8-Bit Edition).
 * Incorporates robust human-readable error handling and direct Firebase session recording.
 */
class AuthPresenter(
    private val repository: LedRepository = LedRepository()
) : AuthContract.Presenter {

    private var view: AuthContract.View? = null

    override fun attachView(view: AuthContract.View) {
        this.view = view
    }

    override fun detachView() {
        this.view = null
    }

    override fun checkExistingSession() {
        if (repository.isUserAuthenticated()) {
            val email = repository.getCurrentUserEmail() ?: "operator@resqplug.local"
            view?.navigateToControlScreen(email)
        }
    }

    override fun signIn(email: String, pass: String) {
        val cleanEmail = email.trim()
        val cleanPass = pass.trim()

        if (cleanEmail.isBlank()) {
            view?.showError("EMAIL CANNOT BE EMPTY")
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            view?.showError("INVALID EMAIL FORMAT (e.g. user@domain.com)")
            return
        }
        if (cleanPass.isBlank()) {
            view?.showError("PASSWORD CANNOT BE EMPTY")
            return
        }

        view?.showLoading(true)
        view?.showStatusMessage("VERIFYING CREDENTIALS...")

        repository.signInWithEmail(
            email = cleanEmail,
            pass = cleanPass,
            onSuccess = { userEmail ->
                repository.recordAuthenticatedUserToFirebase(userEmail, "PASSWORD") {
                    view?.showLoading(false)
                    view?.showStatusMessage("AUTHENTICATED: $userEmail")
                    view?.navigateToControlScreen(userEmail)
                }
            },
            onFailure = { errorRaw ->
                view?.showLoading(false)
                val friendlyError = translateFirebaseError(errorRaw, isRegister = false)
                view?.showError(friendlyError)
            }
        )
    }

    override fun register(email: String, pass: String) {
        val cleanEmail = email.trim()
        val cleanPass = pass.trim()

        if (cleanEmail.isBlank()) {
            view?.showError("EMAIL CANNOT BE EMPTY")
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            view?.showError("INVALID EMAIL FORMAT (e.g. user@domain.com)")
            return
        }
        if (cleanPass.length < 6) {
            view?.showError("PASSWORD TOO SHORT (MIN 6 CHARACTERS)")
            return
        }

        view?.showLoading(true)
        view?.showStatusMessage("REGISTERING ACCOUNT...")

        repository.registerWithEmail(
            email = cleanEmail,
            pass = cleanPass,
            onSuccess = { userEmail ->
                repository.recordAuthenticatedUserToFirebase(userEmail, "PASSWORD_REGISTER") {
                    view?.showLoading(false)
                    view?.showStatusMessage("REGISTERED: $userEmail")
                    view?.navigateToControlScreen(userEmail)
                }
            },
            onFailure = { errorRaw ->
                view?.showLoading(false)
                val friendlyError = translateFirebaseError(errorRaw, isRegister = true)
                view?.showError(friendlyError)
            }
        )
    }

    private fun translateFirebaseError(rawError: String, isRegister: Boolean): String {
        val lower = rawError.lowercase()
        return when {
            lower.contains("user-not-found") || lower.contains("no user record") ->
                if (!isRegister) "ACCOUNT NOT FOUND. CLICK REGISTER TO CREATE ONE" else "REGISTRATION FAILED. TRY AGAIN"
            lower.contains("wrong-password") || lower.contains("credential") || lower.contains("invalid password") ->
                if (!isRegister) "INCORRECT PASSWORD OR UNREGISTERED ACCOUNT" else "INVALID CREDENTIALS PROVIDED"
            lower.contains("email-already-in-use") || lower.contains("already exists") ->
                "EMAIL ALREADY REGISTERED. PLEASE CLICK SIGN IN"
            lower.contains("network") || lower.contains("timeout") || lower.contains("unreachable") ->
                "NETWORK ERROR. CHECK INTERNET CONNECTION"
            lower.contains("too-many-requests") ->
                "TOO MANY ATTEMPTS. PLEASE WAIT A MOMENT"
            lower.contains("configuration_not_found") ->
                "NOTICE: EMAIL AUTH DISABLED IN FIREBASE CONSOLE"
            else ->
                rawError.take(45).uppercase()
        }
    }
}

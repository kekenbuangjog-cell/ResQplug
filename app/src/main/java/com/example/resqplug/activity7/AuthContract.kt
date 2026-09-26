package com.example.resqplug.activity7

/**
 * Model-View-Presenter (MVP) Contract for Screen 1: Activity 7 Authentication Gate
 */
interface AuthContract {

    interface View {
        fun showLoading(isLoading: Boolean)
        fun showStatusMessage(message: String)
        fun showError(errorMessage: String)
        fun navigateToControlScreen(userEmail: String)
    }

    interface Presenter {
        fun attachView(view: View)
        fun detachView()
        fun signIn(email: String, pass: String)
        fun register(email: String, pass: String)
        fun checkExistingSession()
    }
}

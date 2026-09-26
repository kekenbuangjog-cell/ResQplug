package com.example.resqplug.activity7

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.resqplug.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Screen 1 View: Activity 7 Authentication Gate (Retro 8-Bit Pixel Edition).
 * Incorporates the 8 FPS Stepped Animation Engine (125ms ticks) and human-readable error feedback.
 * Strictly adheres to MVP pattern with ZERO direct Firebase code in View.
 */
class Activity7AuthActivity : AppCompatActivity(), AuthContract.View {

    private val frameDurationMs = 125L // 8 FPS Retro Standard

    private lateinit var presenter: AuthContract.Presenter

    private lateinit var btnLoginBack: Button
    private lateinit var loginProgressBar: ProgressBar
    private lateinit var etLoginEmail: EditText
    private lateinit var etLoginPassword: EditText
    private lateinit var btnActionSignIn: Button
    private lateinit var btnActionRegister: Button
    private lateinit var tvLoginStatus: TextView

    private var animationJob: Job? = null
    private var isBusy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setContentView(R.layout.activity_a7_login)

        initViews()
        initPresenter()
        setupListeners()
        startIdleCursorBlink()
    }

    override fun onDestroy() {
        super.onDestroy()
        animationJob?.cancel()
        presenter.detachView()
    }

    private fun initViews() {
        btnLoginBack = findViewById(R.id.btnLoginBack)
        loginProgressBar = findViewById(R.id.loginProgressBar)
        etLoginEmail = findViewById(R.id.etLoginEmail)
        etLoginPassword = findViewById(R.id.etLoginPassword)
        btnActionSignIn = findViewById(R.id.btnActionSignIn)
        btnActionRegister = findViewById(R.id.btnActionRegister)
        tvLoginStatus = findViewById(R.id.tvLoginStatus)

        etLoginEmail.clearFocus()
        etLoginPassword.clearFocus()
    }

    private fun initPresenter() {
        presenter = AuthPresenter()
        presenter.attachView(this)
    }

    private fun setupListeners() {
        btnLoginBack.setOnClickListener {
            finish()
        }

        btnActionSignIn.setOnClickListener {
            hideKeyboard()
            val email = etLoginEmail.text.toString().trim()
            val pass = etLoginPassword.text.toString().trim()
            presenter.signIn(email, pass)
        }

        btnActionRegister.setOnClickListener {
            hideKeyboard()
            val email = etLoginEmail.text.toString().trim()
            val pass = etLoginPassword.text.toString().trim()
            presenter.register(email, pass)
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        currentFocus?.let { imm?.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    // =========================================================================
    // 8 FPS Stepped Animation Engine
    // =========================================================================

    private fun start8FpsSteppedProgress(actionTitle: String) {
        animationJob?.cancel()
        animationJob = lifecycleScope.launch {
            val dotPatterns = arrayOf(
                "[ $actionTitle       ]",
                "[ $actionTitle .     ]",
                "[ $actionTitle . .   ]",
                "[ $actionTitle . . . ]"
            )
            var step = 0
            while (isActive && isBusy) {
                tvLoginStatus.text = dotPatterns[step % dotPatterns.size]
                step++
                delay(frameDurationMs * 2) // Stepped every 250ms (2 ticks @ 8 FPS)
            }
        }
    }

    private fun startIdleCursorBlink() {
        if (isBusy) return
        animationJob?.cancel()
        animationJob = lifecycleScope.launch {
            var cursorOn = true
            while (isActive && !isBusy) {
                tvLoginStatus.text = if (cursorOn) {
                    "[ READY // AWAITING COMMAND >_ ]"
                } else {
                    "[ READY // AWAITING COMMAND >  ]"
                }
                cursorOn = !cursorOn
                delay(frameDurationMs * 4) // Blink every 500ms (4 ticks @ 8 FPS)
            }
        }
    }

    // =========================================================================
    // AuthContract.View Implementations
    // =========================================================================

    override fun showLoading(isLoading: Boolean) {
        this.isBusy = isLoading
        loginProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnActionSignIn.isEnabled = !isLoading
        btnActionRegister.isEnabled = !isLoading

        if (isLoading) {
            start8FpsSteppedProgress("AUTHENTICATING")
        } else {
            startIdleCursorBlink()
        }
    }

    override fun showStatusMessage(message: String) {
        tvLoginStatus.text = "[ $message ]"
    }

    override fun showError(errorMessage: String) {
        tvLoginStatus.text = "[ ⚠️ $errorMessage ]"
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
    }

    override fun navigateToControlScreen(userEmail: String) {
        val intent = Intent(this, Activity7ConnectionActivity::class.java).apply {
            putExtra("EXTRA_VERIFIED_EMAIL", userEmail)
        }
        startActivity(intent)
        finish()
    }
}

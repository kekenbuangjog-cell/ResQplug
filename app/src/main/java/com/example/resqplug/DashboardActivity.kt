package com.example.resqplug

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.resqplug.ui.StarfieldView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {

    private val frameDurationMs = 125L // 8 FPS

    private var nodeCount = 1
    private var sosCount = 0
    private var evacCount = 0
    private var statusCount = 0

    private lateinit var tvNodeCount: TextView
    private lateinit var tvNodeDiagram: TextView
    private lateinit var signalBar: ProgressBar
    private lateinit var tvSosCount: TextView
    private lateinit var tvEvacCount: TextView
    private lateinit var tvStatusCount: TextView
    private lateinit var sosBar: ProgressBar
    private lateinit var evacBar: ProgressBar
    private lateinit var statusBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dashboard)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.dashMain)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Get device ID from intent
        val deviceId = intent.getStringExtra("DEVICE_ID") ?: "RQP-SIM-0000"

        // Init views
        tvNodeCount = findViewById(R.id.tvNodeCount)
        tvNodeDiagram = findViewById(R.id.tvNodeDiagram)
        signalBar = findViewById(R.id.signalBar)
        tvSosCount = findViewById(R.id.tvSosCount)
        tvEvacCount = findViewById(R.id.tvEvacCount)
        tvStatusCount = findViewById(R.id.tvStatusCount)
        sosBar = findViewById(R.id.sosBar)
        evacBar = findViewById(R.id.evacBar)
        statusBar = findViewById(R.id.statusBar)

        // Set device ID
        findViewById<TextView>(R.id.tvDashDeviceId).text = deviceId

        // Fade in dashboard
        val fadeOverlay = findViewById<View>(R.id.fadeOverlay)
        fadeOverlay.alpha = 1.0f
        startFadeIn(fadeOverlay)

        // Start starfield
        val starfieldView = findViewById<StarfieldView>(R.id.dashStarfield)
        startStarfieldTwinkle(starfieldView)

        // Simulate mesh nodes appearing
        startMeshSimulation()

        // Button click handlers
        findViewById<Button>(R.id.btnSos).setOnClickListener {
            sosCount++
            updateQueueDisplay()
            Toast.makeText(this, "SOS SENT", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnEvac).setOnClickListener {
            evacCount++
            updateQueueDisplay()
            Toast.makeText(this, "EVAC SENT", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnStatus).setOnClickListener {
            statusCount++
            updateQueueDisplay()
            Toast.makeText(this, "STATUS SENT", Toast.LENGTH_SHORT).show()
        }

        // Initialize display
        updateQueueDisplay()
    }

    private fun startFadeIn(overlay: View) {
        lifecycleScope.launch {
            var alpha = 1.0f
            while (alpha > 0f) {
                delay(frameDurationMs)
                alpha -= 0.125f
                overlay.alpha = alpha.coerceAtLeast(0f)
            }
            overlay.alpha = 0f
            overlay.visibility = View.GONE
        }
    }

    private fun startMeshSimulation() {
        lifecycleScope.launch {
            // Simulate nodes joining over time
            val nodeNames = listOf("Node-A", "Node-B", "Node-C", "Node-D")
            for (name in nodeNames) {
                delay(3000L) // New node every 3 seconds
                nodeCount++
                updateNodeDisplay(name)
                signalBar.progress = (60..100).random()
            }
        }
    }

    private fun updateNodeDisplay(newNodeName: String) {
        tvNodeCount.text = getString(R.string.dash_nodes_active, nodeCount)

        // Build node diagram
        val nodes = mutableListOf("● You")
        for (i in 1 until nodeCount) {
            nodes.add("● ${nodeNames.getOrElse(i - 1) { "Node-$i" }}")
        }
        tvNodeDiagram.text = nodes.joinToString("  ──  ")
    }

    private val nodeNames = listOf("Node-A", "Node-B", "Node-C", "Node-D")

    private fun updateQueueDisplay() {
        tvSosCount.text = "[ $sosCount ]"
        tvEvacCount.text = "[ $evacCount ]"
        tvStatusCount.text = "[ $statusCount ]"

        sosBar.progress = sosCount.coerceAtMost(20)
        evacBar.progress = evacCount.coerceAtMost(20)
        statusBar.progress = statusCount.coerceAtMost(20)
    }

    private fun startStarfieldTwinkle(starfieldView: StarfieldView) {
        lifecycleScope.launch {
            while (isActive) {
                delay(frameDurationMs)
                starfieldView.tick()
            }
        }
    }
}

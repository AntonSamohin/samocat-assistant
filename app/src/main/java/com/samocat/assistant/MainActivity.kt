package com.samocat.assistant

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvOrderInfo: TextView
    private lateinit var btnEnable: Button
    private lateinit var btnSettings: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvOrderInfo = findViewById(R.id.tvOrderInfo)
        btnEnable = findViewById(R.id.btnEnable)
        btnSettings = findViewById(R.id.btnSettings)

        btnEnable.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val enabled = isAccessibilityServiceEnabled()
        if (enabled) {
            tvStatus.text = getString(R.string.status_service_active)
            tvStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            btnEnable.isVisible = false
        } else {
            tvStatus.text = getString(R.string.status_service_inactive)
            tvStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            btnEnable.isVisible = true
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "$packageName/${AssistantService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return TextUtils.SimpleStringSplitter(':').apply {
            setString(enabled)
        }.any { it.equals(service, ignoreCase = true) }
    }
}

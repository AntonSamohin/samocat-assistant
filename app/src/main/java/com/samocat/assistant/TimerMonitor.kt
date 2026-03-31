package com.samocat.assistant

import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator

class TimerMonitor(private val service: AssistantService) {

    private val handler = Handler(Looper.getMainLooper())
    private var currentSeconds: Int = -1
    private var firedAlerts = mutableSetOf<Int>()
    private var isRunning = false

    fun update(seconds: Int) {
        if (seconds < 0) {
            stop()
            return
        }
        if (seconds != currentSeconds) {
            currentSeconds = seconds
            if (!isRunning) {
                isRunning = true
                startTicking()
            }
        }
    }

    fun stop() {
        isRunning = false
        currentSeconds = -1
        firedAlerts.clear()
        handler.removeCallbacksAndMessages(null)
    }

    private fun startTicking() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!isRunning || currentSeconds < 0) return

                checkAlerts(currentSeconds)

                if (currentSeconds > 0) {
                    currentSeconds--
                    handler.postDelayed(this, 1000)
                } else {
                    stop()
                }
            }
        }, 1000)
    }

    private fun checkAlerts(seconds: Int) {
        val alerts = service.getAlerts()
        for (alert in alerts) {
            if (!alert.enabled) continue
            if (alert.seconds == seconds && !firedAlerts.contains(seconds)) {
                firedAlerts.add(seconds)
                fireAlert(alert)
            }
        }
    }

    private fun fireAlert(alert: TimerAlert) {
        service.speak(alert.text)
        vibrate()
    }

    private fun vibrate() {
        val vibrator = service.getSystemService(android.content.Context.VIBRATOR_SERVICE)
            as? Vibrator ?: return
        val pattern = longArrayOf(0, 300, 100, 300)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }
}

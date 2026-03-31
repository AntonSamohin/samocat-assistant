package com.samocat.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AssistantService : AccessibilityService() {

    private lateinit var voiceEngine: VoiceEngine
    private lateinit var timerMonitor: TimerMonitor
    private val gson = Gson()

    var currentAddress: String = ""
    var currentBox: String = ""
    var currentComment: String = ""
    var currentScreen: String = ""
    var currentTimerSeconds: Int = -1

    override fun onServiceConnected() {
        super.onServiceConnected()
        voiceEngine = VoiceEngine(this)
        timerMonitor = TimerMonitor(this)
        voiceEngine.start()
        speak(getString(R.string.tts_service_started))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val root = rootInActiveWindow ?: return
        parseScreen(root)
        root.recycle()
    }

    override fun onInterrupt() {
        voiceEngine.stop()
        timerMonitor.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceEngine.stop()
        timerMonitor.stop()
    }

    private fun parseScreen(root: AccessibilityNodeInfo) {
        val titleNode = root.findByText("Собери рюкзак і виходи")
            ?: root.findByText("Собери рюкзак и выходи")
        val deliverNode = root.findByText("Доставь заказ")
        val returnNode = root.findByText("Вернись в даркстор")

        when {
            titleNode != null -> {
                currentScreen = "collect"
                parseCollectScreen(root)
            }
            deliverNode != null -> {
                currentScreen = "deliver"
                parseDeliverScreen(root)
            }
            returnNode != null -> {
                currentScreen = "return"
                parseReturnScreen(root)
            }
            else -> {
                currentScreen = "other"
                currentTimerSeconds = -1
                timerMonitor.stop()
            }
        }
    }

    private fun parseCollectScreen(root: AccessibilityNodeInfo) {
        currentAddress = root.findTextContaining("ская")
            ?: root.findTextContaining("пр-т")
            ?: root.findTextContaining("ул.")
            ?: ""
        currentBox = root.findBoxNumber() ?: ""
        currentComment = ""

        val timerText = root.findTimerText()
        if (timerText != null) {
            val secs = parseTimerToSeconds(timerText)
            if (secs != currentTimerSeconds) {
                currentTimerSeconds = secs
                timerMonitor.update(secs)
            }
        }
    }

    private fun parseDeliverScreen(root: AccessibilityNodeInfo) {
        currentAddress = root.findNodeByViewId("address")?.text?.toString()
            ?: root.findTextContaining("ская")
            ?: root.findTextContaining("пр-т")
            ?: ""
        currentBox = root.findBoxNumber() ?: ""
        currentComment = root.findCommentText() ?: ""

        val timerText = root.findTimerText()
        if (timerText != null) {
            val secs = parseTimerToSeconds(timerText)
            if (secs != currentTimerSeconds) {
                currentTimerSeconds = secs
                timerMonitor.update(secs)
            }
        }
    }

    private fun parseReturnScreen(root: AccessibilityNodeInfo) {
        currentAddress = "Даркстор"
        currentBox = ""
        currentComment = ""

        val timerText = root.findTimerText()
        if (timerText != null) {
            val secs = parseTimerToSeconds(timerText)
            if (secs != currentTimerSeconds) {
                currentTimerSeconds = secs
                timerMonitor.update(secs)
            }
        }
    }

    fun speak(text: String) {
        voiceEngine.speak(text)
    }

    fun speakOrderInfo() {
        when (currentScreen) {
            "collect" -> {
                val text = buildString {
                    append("Собираем заказ. ")
                    if (currentAddress.isNotEmpty()) append("Адрес: $currentAddress. ")
                    if (currentBox.isNotEmpty()) append("Бокс $currentBox. ")
                }
                speak(text)
            }
            "deliver" -> {
                val text = buildString {
                    append("Доставляем заказ. ")
                    if (currentAddress.isNotEmpty()) append("Адрес: $currentAddress. ")
                    if (currentBox.isNotEmpty()) append("Бокс $currentBox. ")
                    if (currentComment.isNotEmpty()) append("Комментарий: $currentComment. ")
                }
                speak(text)
            }
            "return" -> speak("Возвращаемся в даркстор.")
            else -> speak(getString(R.string.tts_no_order))
        }
    }

    fun clickButton(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = root.findByText(text) ?: return false
        val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (!result) {
            val parent = node.parent
            parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        root.recycle()
        return result
    }

    fun getAlerts(): List<TimerAlert> {
        val prefs = getSharedPreferences("assistant_prefs", MODE_PRIVATE)
        val json = prefs.getString("timer_alerts", null) ?: return emptyList()
        val type = object : TypeToken<List<TimerAlert>>() {}.type
        return gson.fromJson(json, type)
    }

    fun getWakeWord(): String {
        val prefs = getSharedPreferences("assistant_prefs", MODE_PRIVATE)
        return prefs.getString("wake_word", "Борт") ?: "Борт"
    }

    private fun parseTimerToSeconds(text: String): Int {
        val parts = text.split(":")
        return if (parts.size == 2) {
            val mins = parts[0].trim().toIntOrNull() ?: 0
            val secs = parts[1].trim().toIntOrNull() ?: 0
            mins * 60 + secs
        } else {
            text.trim().toIntOrNull() ?: -1
        }
    }

    private fun AccessibilityNodeInfo.findByText(text: String): AccessibilityNodeInfo? {
        if (this.text?.toString()?.contains(text, ignoreCase = true) == true) return this
        for (i in 0 until childCount) {
            val child = getChild(i) ?: continue
            val found = child.findByText(text)
            if (found != null) return found
        }
        return null
    }

    private fun AccessibilityNodeInfo.findTextContaining(pattern: String): String? {
        val text = this.text?.toString()
        if (text != null && text.contains(pattern, ignoreCase = true)) return text
        for (i in 0 until childCount) {
            val child = getChild(i) ?: continue
            val found = child.findTextContaining(pattern)
            if (found != null) return found
        }
        return null
    }

    private fun AccessibilityNodeInfo.findBoxNumber(): String? {
        val text = this.text?.toString()
        if (text != null && text.matches(Regex("\\d{1,3}"))) {
            val num = text.toIntOrNull()
            if (num != null && num in 1..999) return text
        }
        for (i in 0 until childCount) {
            val child = getChild(i) ?: continue
            val found = child.findBoxNumber()
            if (found != null) return found
        }
        return null
    }

    private fun AccessibilityNodeInfo.findTimerText(): String? {
        val text = this.text?.toString()
        if (text != null && text.matches(Regex("\\d{1,2}:\\d{2}"))) return text
        for (i in 0 until childCount) {
            val child = getChild(i) ?: continue
            val found = child.findTimerText()
            if (found != null) return found
        }
        return null
    }

    private fun AccessibilityNodeInfo.findCommentText(): String? {
        val text = this.text?.toString()
        if (text != null && text.length > 3 &&
            !text.matches(Regex("\\d{1,2}:\\d{2}")) &&
            !text.matches(Regex("\\d{1,3}")) &&
            !text.contains("Доставь") &&
            !text.contains("Позвонить") &&
            !text.contains("Завершить") &&
            !text.contains("Посмотреть") &&
            !text.contains("Передай") &&
            !text.contains("Помощь")) {
            return text
        }
        for (i in 0 until childCount) {
            val child = getChild(i) ?: continue
            val found = child.findCommentText()
            if (found != null) return found
        }
        return null
    }

    private fun AccessibilityNodeInfo.findNodeByViewId(id: String): AccessibilityNodeInfo? {
        if (this.viewIdResourceName?.contains(id) == true) return this
        for (i in 0 until childCount) {
            val child = getChild(i) ?: continue
            val found = child.findNodeByViewId(id)
            if (found != null) return found
        }
        return null
    }
}

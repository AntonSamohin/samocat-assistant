package com.samocat.assistant

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class TimerAlert(
    var seconds: Int = 15,
    var text: String = "",
    var enabled: Boolean = true
)

class SettingsActivity : AppCompatActivity() {

    private lateinit var etWakeWord: EditText
    private lateinit var rvAlerts: RecyclerView
    private lateinit var btnAddAlert: Button
    private lateinit var btnSave: Button
    private lateinit var alertsAdapter: AlertsAdapter
    private val alerts = mutableListOf<TimerAlert>()
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        etWakeWord = findViewById(R.id.etWakeWord)
        rvAlerts = findViewById(R.id.rvAlerts)
        btnAddAlert = findViewById(R.id.btnAddAlert)
        btnSave = findViewById(R.id.btnSave)

        loadSettings()

        alertsAdapter = AlertsAdapter(alerts) { position ->
            alerts.removeAt(position)
            alertsAdapter.notifyItemRemoved(position)
        }

        rvAlerts.layoutManager = LinearLayoutManager(this)
        rvAlerts.adapter = alertsAdapter

        btnAddAlert.setOnClickListener {
            alerts.add(TimerAlert(
                seconds = 15,
                text = "Осталось 15 секунд!",
                enabled = true
            ))
            alertsAdapter.notifyItemInserted(alerts.size - 1)
        }

        btnSave.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "Сохранено!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("assistant_prefs", MODE_PRIVATE)
        etWakeWord.setText(prefs.getString("wake_word", "Борт"))
        val alertsJson = prefs.getString("timer_alerts", null)
        if (alertsJson != null) {
            val type = object : TypeToken<List<TimerAlert>>() {}.type
            val loaded: List<TimerAlert> = gson.fromJson(alertsJson, type)
            alerts.addAll(loaded)
        } else {
            alerts.addAll(listOf(
                TimerAlert(60, "Осталась одна минута", true),
                TimerAlert(30, "Тридцать секунд до конца", true),
                TimerAlert(15, "Пятнадцать секунд, поторопись!", true),
                TimerAlert(5, "Время почти вышло!", true)
            ))
        }
    }

    private fun saveSettings() {
        alertsAdapter.syncData()
        val prefs = getSharedPreferences("assistant_prefs", MODE_PRIVATE)
        prefs.edit()
            .putString("wake_word", etWakeWord.text.toString().trim())
            .putString("timer_alerts", gson.toJson(alerts))
            .apply()
    }
}

class AlertsAdapter(
    private val alerts: MutableList<TimerAlert>,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<AlertsAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val etSeconds: EditText = itemView.findViewById(R.id.etSeconds)
        val etText: EditText = itemView.findViewById(R.id.etAlertText)
        val switchEnabled: Switch = itemView.findViewById(R.id.switchEnabled)
        val btnDelete: Button = itemView.findViewById(R.id.btnDeleteAlert)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alert, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val alert = alerts[position]
        holder.etSeconds.setText(alert.seconds.toString())
        holder.etText.setText(alert.text)
        holder.switchEnabled.isChecked = alert.enabled
        holder.btnDelete.setOnClickListener { onDelete(holder.adapterPosition) }
    }

    override fun getItemCount() = alerts.size

    fun syncData() {
        for (i in alerts.indices) {
            val holder = recyclerView?.findViewHolderForAdapterPosition(i) as? ViewHolder
            holder?.let {
                alerts[i].seconds = it.etSeconds.text.toString().toIntOrNull() ?: 15
                alerts[i].text = it.etText.text.toString()
                alerts[i].enabled = it.switchEnabled.isChecked
            }
        }
    }

    private var recyclerView: RecyclerView? = null

    override fun onAttachedToRecyclerView(rv: RecyclerView) {
        super.onAttachedToRecyclerView(rv)
        recyclerView = rv
    }
}

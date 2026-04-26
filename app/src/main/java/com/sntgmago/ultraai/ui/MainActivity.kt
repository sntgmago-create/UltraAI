package com.sntgmago.ultraai.ui

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.sntgmago.ultraai.R
import com.sntgmago.ultraai.gemini.GeminiClient
import com.sntgmago.ultraai.shell.ActionExecutor
import kotlinx.coroutines.launch

// ---- Modelos ----
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val isAction: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

// ---- ViewModel ----
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val gemini = GeminiClient(application)
    private val executor = ActionExecutor(application)
    private val _messages = MutableLiveData<List<ChatMessage>>(emptyList())
    val messages: LiveData<List<ChatMessage>> = _messages
    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    fun hasApiKey() = gemini.hasApiKey()
    fun saveApiKey(key: String) = gemini.saveApiKey(key)

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        addMessage(ChatMessage(text = text, isUser = true))
        _loading.value = true
        viewModelScope.launch {
            gemini.sendMessage(text)
                .onSuccess { response ->
                    addMessage(ChatMessage(text = response, isUser = false))
                    val result = executor.parseAndExecute(response)
                    if (result != null) addMessage(ChatMessage(text = result.message, isUser = false, isAction = true))
                }
                .onFailure { addMessage(ChatMessage(text = "❌ ${it.message}", isUser = false)) }
            _loading.value = false
        }
    }

    fun clearConversation() { gemini.clearHistory(); _messages.value = emptyList() }
    private fun addMessage(msg: ChatMessage) {
        val list = _messages.value?.toMutableList() ?: mutableListOf()
        list.add(msg)
        _messages.value = list
    }
    fun getQuickCommands() = listOf("¿Qué podés hacer?", "Abrí WhatsApp", "Activá WiFi", "Volumen al 80%", "Abrí YouTube")
}

// ---- Adapter ----
class ChatAdapter : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(object : DiffUtil.ItemCallback<ChatMessage>() {
    override fun areItemsTheSame(a: ChatMessage, b: ChatMessage) = a.timestamp == b.timestamp
    override fun areContentsTheSame(a: ChatMessage, b: ChatMessage) = a == b
}) {
    override fun getItemViewType(position: Int) = when {
        getItem(position).isUser -> 1
        getItem(position).isAction -> 3
        else -> 2
    }
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val tv = TextView(parent.context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(32, 20, 32, 20)
            textSize = 15f
            setTextColor(android.graphics.Color.parseColor("#E6EDF3"))
            maxWidth = 900
        }
        return object : RecyclerView.ViewHolder(android.widget.LinearLayout(parent.context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(16, 8, 16, 8)
            addView(tv)
        }) {}
    }
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = getItem(position)
        val ll = holder.itemView as android.widget.LinearLayout
        val tv = ll.getChildAt(0) as TextView
        tv.text = msg.text
        ll.gravity = if (msg.isUser) android.view.Gravity.END else android.view.Gravity.START
        tv.setBackgroundColor(android.graphics.Color.parseColor(
            when { msg.isUser -> "#1F6FEB"; msg.isAction -> "#0D2A1A"; else -> "#21262D" }
        ))
    }
}

// ---- MainActivity ----
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!viewModel.hasApiKey()) {
            showApiKeyDialog()
            return
        }
        setupUI()
    }

    private fun setupUI() {
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#0D1117"))
        }

        val rv = RecyclerView(this).apply {
            adapter = ChatAdapter().also { this@MainActivity.adapter = it }
            layoutManager = LinearLayoutManager(this@MainActivity).apply { stackFromEnd = true }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val chipGroup = ChipGroup(this).apply {
            isSingleLine = true
            setPadding(8, 4, 8, 4)
        }
        viewModel.getQuickCommands().forEach { cmd ->
            chipGroup.addView(Chip(this).apply { text = cmd; setOnClickListener { viewModel.sendMessage(cmd) } })
        }

        val inputRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setBackgroundColor(android.graphics.Color.parseColor("#161B22"))
            setPadding(8, 8, 8, 8)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val et = TextInputEditText(this).apply {
            hint = "Hablá con UltraAI..."
            setHintTextColor(android.graphics.Color.parseColor("#6E7681"))
            setTextColor(android.graphics.Color.parseColor("#E6EDF3"))
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btn = Button(this).apply {
            text = "▶"
            setBackgroundColor(android.graphics.Color.parseColor("#58A6FF"))
            setTextColor(android.graphics.Color.WHITE)
        }
        btn.setOnClickListener {
            val txt = et.text?.toString()?.trim() ?: ""
            if (txt.isNotEmpty()) { viewModel.sendMessage(txt); et.text?.clear() }
        }
        inputRow.addView(et)
        inputRow.addView(btn)

        root.addView(rv)
        root.addView(HorizontalScrollView(this).apply { addView(chipGroup) })
        root.addView(inputRow)
        setContentView(root)

        viewModel.messages.observe(this) { msgs ->
            adapter.submitList(msgs.toList()) { rv.scrollToPosition(adapter.itemCount - 1) }
        }
    }

    private fun showApiKeyDialog() {
        val et = EditText(this).apply {
            hint = "AIza..."
            setPadding(32, 16, 32, 16)
        }
        AlertDialog.Builder(this)
            .setTitle("🤖 Bienvenido a UltraAI")
            .setMessage("Ingresá tu API Key de Gemini (gratis en makersuite.google.com)")
            .setView(et)
            .setPositiveButton("Guardar") { _, _ ->
                val key = et.text.toString().trim()
                if (key.startsWith("AIza")) {
                    viewModel.saveApiKey(key)
                    setupUI()
                } else Toast.makeText(this, "API Key inválida", Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Limpiar chat")
        menu.add(0, 2, 0, "Cambiar API Key")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> viewModel.clearConversation()
            2 -> showApiKeyDialog()
        }
        return true
    }
}

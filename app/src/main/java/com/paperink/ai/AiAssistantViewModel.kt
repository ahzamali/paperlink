package com.paperink.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.cancellation.CancellationException

data class ChatMessage(
    val id: Long = System.currentTimeMillis() + (0..9999).random(),
    val isUser: Boolean,
    val text: String,
    val isStreaming: Boolean = false
)

data class AiChatState(
    val modelStatus: AiModelManager.ModelStatus = AiModelManager.ModelStatus.NotDownloaded,
    val isEngineInitializing: Boolean = false,
    val isEngineReady: Boolean = false,
    val activeBackend: String = "None", // "GPU" or "CPU"
    val isBusy: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val errorMessage: String? = null,
    val documentTitle: String = "",
    val documentTextSnippet: String = ""
)

class AiAssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val executor = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())
    private val mutex = Mutex()

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    private val _state = MutableStateFlow(AiChatState())
    val state: StateFlow<AiChatState> = _state.asStateFlow()

    init {
        // Observe model download status
        viewModelScope.launch {
            AiModelManager.status.collect { status ->
                _state.update { it.copy(modelStatus = status) }
                if (status is AiModelManager.ModelStatus.Ready && !_state.value.isEngineReady && !_state.value.isEngineInitializing) {
                    loadEngine(status.modelFile)
                }
            }
        }
        checkModelAvailability()
    }

    fun checkModelAvailability() {
        AiModelManager.checkStatus(getApplication())
    }

    fun startModelDownload() {
        AiModelManager.startDownload(getApplication(), viewModelScope)
    }

    fun cancelModelDownload() {
        AiModelManager.cancelDownload(getApplication())
    }

    fun setDocumentContext(title: String, textSnippet: String) {
        _state.update {
            it.copy(
                documentTitle = title,
                documentTextSnippet = textSnippet.take(12_000) // Fit context window comfortably
            )
        }
    }

    /**
     * Initializes LiteRT-LM Engine on a single background thread.
     * Attempts GPU backend first; falls back cleanly to CPU if OpenCL is not supported.
     */
    fun loadEngine(modelFile: File) {
        scope.launch {
            mutex.withLock {
                if (engine?.engineConfig?.modelPath == modelFile.absolutePath && _state.value.isEngineReady) {
                    return@launch
                }
                releaseEngine()
                _state.update { it.copy(isEngineInitializing = true, errorMessage = null) }

                val cacheDir = getApplication<Application>().cacheDir.path

                // 1. Try GPU Backend first
                var initialized = false
                try {
                    val gpuConfig = EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = Backend.GPU(),
                        cacheDir = cacheDir
                    )
                    val newEngine = Engine(gpuConfig)
                    newEngine.initialize()
                    engine = newEngine
                    _state.update { it.copy(activeBackend = "GPU Accelerated") }
                    initialized = true
                } catch (e: Throwable) {
                    e.printStackTrace()
                    releaseEngine()
                }

                // 2. Fall back to CPU Backend if GPU was unavailable
                if (!initialized) {
                    try {
                        val cpuConfig = EngineConfig(
                            modelPath = modelFile.absolutePath,
                            backend = Backend.CPU(),
                            cacheDir = cacheDir
                        )
                        val newEngine = Engine(cpuConfig)
                        newEngine.initialize()
                        engine = newEngine
                        _state.update { it.copy(activeBackend = "CPU Mode") }
                        initialized = true
                    } catch (e: Throwable) {
                        e.printStackTrace()
                        releaseEngine()
                        _state.update {
                            it.copy(
                                isEngineInitializing = false,
                                isEngineReady = false,
                                errorMessage = "Model load failed: ${e.message}"
                            )
                        }
                        return@launch
                    }
                }

                // Create initial conversation
                try {
                    val systemPrompt = buildSystemPrompt()
                    conversation = checkNotNull(engine).createConversation(
                        ConversationConfig(systemInstruction = Contents.of(systemPrompt))
                    )
                    _state.update {
                        it.copy(
                            isEngineInitializing = false,
                            isEngineReady = true
                        )
                    }
                } catch (e: Throwable) {
                    releaseEngine()
                    _state.update {
                        it.copy(
                            isEngineInitializing = false,
                            isEngineReady = false,
                            errorMessage = "Conversation setup failed: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    private fun buildSystemPrompt(): String {
        val title = _state.value.documentTitle
        val snippet = _state.value.documentTextSnippet
        val contextPart = if (snippet.isNotBlank()) {
            "\n\nDOCUMENT CONTEXT (Title: $title):\n\"\"\"\n$snippet\n\"\"\"\n"
        } else {
            ""
        }
        return """
            You are PaperInk AI, a helpful, precise on-device reading assistant powered by Gemma 4 2B.
            Your job is to summarize documents, answer user questions, and extract insights directly from the provided PDF text.
            If the user asks questions about the document, rely strictly on the provided context where possible. Keep answers clear, well-structured, and concise.
            $contextPart
        """.trimIndent()
    }

    fun restartConversationWithCurrentDocument() {
        scope.launch {
            mutex.withLock {
                val eng = engine ?: return@launch
                conversation?.close()
                conversation = null
                val systemPrompt = buildSystemPrompt()
                conversation = eng.createConversation(
                    ConversationConfig(systemInstruction = Contents.of(systemPrompt))
                )
                _state.update { it.copy(messages = emptyList(), errorMessage = null) }
            }
        }
    }

    fun askQuestion(questionText: String) {
        val text = questionText.trim()
        if (text.isBlank()) return
        if (!_state.value.isEngineReady || _state.value.isBusy) return

        val userMsg = ChatMessage(isUser = true, text = text)
        val aiMsg = ChatMessage(isUser = false, text = "", isStreaming = true)

        _state.update {
            it.copy(
                isBusy = true,
                errorMessage = null,
                messages = it.messages + userMsg + aiMsg
            )
        }

        scope.launch {
            try {
                val conv = checkNotNull(conversation) { "Conversation not ready" }
                val stream = conv.sendMessageAsync(text)

                stream.collect { chunk ->
                    val chunkStr = chunk.toString()
                    _state.update { current ->
                        val updated = current.messages.toMutableList()
                        if (updated.isNotEmpty()) {
                            val last = updated.last()
                            if (!last.isUser) {
                                updated[updated.lastIndex] = last.copy(
                                    text = last.text + chunkStr,
                                    isStreaming = true
                                )
                            }
                        }
                        current.copy(messages = updated)
                    }
                }

                // Streaming finished
                _state.update { current ->
                    val updated = current.messages.toMutableList()
                    if (updated.isNotEmpty()) {
                        val last = updated.last()
                        if (!last.isUser) {
                            updated[updated.lastIndex] = last.copy(isStreaming = false)
                        }
                    }
                    current.copy(messages = updated, isBusy = false)
                }
            } catch (e: CancellationException) {
                _state.update { current ->
                    val updated = current.messages.toMutableList()
                    if (updated.isNotEmpty()) {
                        val last = updated.last()
                        if (!last.isUser) {
                            updated[updated.lastIndex] = last.copy(
                                text = last.text + " [Cancelled]",
                                isStreaming = false
                            )
                        }
                    }
                    current.copy(messages = updated, isBusy = false)
                }
                throw e
            } catch (e: Throwable) {
                e.printStackTrace()
                _state.update { current ->
                    val updated = current.messages.toMutableList()
                    if (updated.isNotEmpty()) {
                        val last = updated.last()
                        if (!last.isUser) {
                            updated[updated.lastIndex] = last.copy(
                                text = if (last.text.isBlank()) "Error: ${e.message}" else last.text,
                                isStreaming = false
                            )
                        }
                    }
                    current.copy(
                        messages = updated,
                        isBusy = false,
                        errorMessage = e.message
                    )
                }
            }
        }
    }

    fun summarizeDocument() {
        val snippet = _state.value.documentTextSnippet
        val prompt = if (snippet.isNotBlank()) {
            "Please provide a comprehensive summary of this document. Include 3-5 key takeaways and bullet points."
        } else {
            "Please summarize this document."
        }
        askQuestion(prompt)
    }

    fun extractKeyTakeaways() {
        askQuestion("What are the most important key takeaways and conclusions from this document?")
    }

    fun extractActionItems() {
        askQuestion("List all action items, next steps, and practical recommendations mentioned in this document.")
    }

    fun cancelStreaming() {
        scope.launch {
            conversation?.cancelProcess()
            _state.update { it.copy(isBusy = false) }
        }
    }

    private suspend fun releaseEngine() {
        _state.update { it.copy(isEngineReady = false) }
        while (_state.value.isBusy) {
            conversation?.cancelProcess()
            delay(100)
        }
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
        _state.update { it.copy(isEngineReady = false, isEngineInitializing = false) }
    }

    override fun onCleared() {
        super.onCleared()
        scope.launch {
            mutex.withLock { releaseEngine() }
            executor.shutdown()
        }.invokeOnCompletion {
            scope.cancel()
        }
    }
}

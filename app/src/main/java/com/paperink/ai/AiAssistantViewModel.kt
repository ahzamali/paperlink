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

enum class ContextScopeMode {
    CURRENT_PAGE,       // Context is focused on the user's current reading page (Page X of Y)
    RELEVANT_SECTIONS,  // Local RAG: dynamically retrieves pages matching query keywords
    DOCUMENT_OVERVIEW   // Condensed multi-page outline (intro, key mid-sections, conclusion)
}

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
    val activeBackend: String = "None", // "GPU Accelerated" or "CPU Mode"
    val isBusy: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val errorMessage: String? = null,
    val documentTitle: String = "",
    val documentTextSnippet: String = "",
    val currentPageIndex: Int = 0,
    val totalPages: Int = 1,
    val contextScopeMode: ContextScopeMode = ContextScopeMode.CURRENT_PAGE,
    val activeContextSnippet: String = "",
    val contextSummaryBadge: String = ""
)

class AiAssistantViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        // Safe character limit for attached document context.
        // 3,500 characters ≈ 850-900 tokens.
        // Gemma 4 2B on LiteRT-LM has a sequence length of 2,048 tokens.
        // Keeping context under 3,500 chars leaves ~1,100 tokens for system prompt, user queries,
        // multi-turn conversation history, and model generation, preventing "context too long" errors.
        const val MAX_CONTEXT_CHARS = 3500
        const val EMERGENCY_FALLBACK_CONTEXT_CHARS = 1600

        private val STOPWORDS = setOf(
            "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "aren't",
            "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but", "by",
            "can", "can't", "cannot", "could", "couldn't", "did", "didn't", "do", "does", "doesn't", "doing",
            "don't", "down", "during", "each", "few", "for", "from", "further", "had", "hadn't", "has", "hasn't",
            "have", "haven't", "having", "he", "he'd", "he'll", "he's", "her", "here", "here's", "hers", "herself",
            "him", "himself", "his", "how", "how's", "i", "i'd", "i'll", "i'm", "i've", "if", "in", "into", "is",
            "isn't", "it", "it's", "its", "itself", "let's", "me", "more", "most", "mustn't", "my", "myself", "no",
            "nor", "not", "of", "off", "on", "once", "only", "or", "other", "ought", "our", "ours", "ourselves",
            "out", "over", "own", "same", "shan't", "she", "she'd", "she'll", "she's", "should", "shouldn't", "so",
            "some", "such", "than", "that", "that's", "the", "their", "theirs", "them", "themselves", "then", "there",
            "there's", "these", "they", "they'd", "they'll", "they're", "they've", "this", "those", "through", "to",
            "too", "under", "until", "up", "very", "was", "wasn't", "we", "we'd", "we'll", "we're", "we've", "were",
            "weren't", "what", "what's", "when", "when's", "where", "where's", "which", "while", "who", "who's",
            "whom", "why", "why's", "with", "won't", "would", "wouldn't", "you", "you'd", "you'll", "you're", "you've",
            "your", "yours", "yourself", "yourselves", "tell", "please", "find", "give"
        )
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())
    private val mutex = Mutex()

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var conversationTurnCount: Int = 0

    private var pagesText: Map<Int, String> = emptyMap()
    private var fallbackSnippet: String = ""

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
        AiModelManager.checkStatus(getApplication(), viewModelScope)
    }

    fun startModelDownload() {
        AiModelManager.startDownload(getApplication(), viewModelScope)
    }

    fun cancelModelDownload() {
        AiModelManager.cancelDownload(getApplication())
    }

    /**
     * Sets document context with full page-by-page mapping and current page index.
     * Automatically sizes and scopes text to safe token limits.
     */
    fun setDocumentContext(
        title: String,
        currentPageIndex: Int = 0,
        totalPages: Int = 1,
        pagesText: Map<Int, String> = emptyMap(),
        fallbackSnippet: String = ""
    ) {
        val snippet = (if (pagesText.isNotEmpty()) {
            pagesText[currentPageIndex] ?: fallbackSnippet
        } else {
            fallbackSnippet
        }).take(MAX_CONTEXT_CHARS).trim()

        val titleChanged = _state.value.documentTitle != title
        val pageChanged = _state.value.currentPageIndex != currentPageIndex
        val totalPagesChanged = _state.value.totalPages != totalPages
        val pagesTextChanged = this.pagesText != pagesText
        val snippetChanged = _state.value.documentTextSnippet != snippet

        if (titleChanged || pageChanged || totalPagesChanged || pagesTextChanged || snippetChanged) {
            this.pagesText = pagesText
            this.fallbackSnippet = fallbackSnippet
            val (activeCtx, badge) = resolveContextSnippet(
                mode = _state.value.contextScopeMode,
                currentPageIndex = currentPageIndex,
                totalPages = totalPages,
                pagesText = pagesText,
                fallbackSnippet = fallbackSnippet,
                query = null,
                maxChars = MAX_CONTEXT_CHARS
            )
            _state.update {
                it.copy(
                    documentTitle = title,
                    documentTextSnippet = snippet,
                    currentPageIndex = currentPageIndex,
                    totalPages = totalPages.coerceAtLeast(1),
                    activeContextSnippet = activeCtx,
                    contextSummaryBadge = badge
                )
            }
            if (_state.value.isEngineReady) {
                restartConversationWithCurrentDocument()
            }
        }
    }

    /**
     * Legacy/convenience overload.
     */
    fun setDocumentContext(title: String, textSnippet: String) {
        setDocumentContext(
            title = title,
            currentPageIndex = 0,
            totalPages = 1,
            pagesText = emptyMap(),
            fallbackSnippet = textSnippet
        )
    }

    /**
     * Toggles between Current Page, Keyword-Matched Relevant Sections, and Document Overview.
     */
    fun setContextScopeMode(mode: ContextScopeMode) {
        if (_state.value.contextScopeMode == mode) return
        val (activeCtx, badge) = resolveContextSnippet(
            mode = mode,
            currentPageIndex = _state.value.currentPageIndex,
            totalPages = _state.value.totalPages,
            pagesText = pagesText,
            fallbackSnippet = fallbackSnippet,
            query = null,
            maxChars = MAX_CONTEXT_CHARS
        )
        _state.update {
            it.copy(
                contextScopeMode = mode,
                activeContextSnippet = activeCtx,
                contextSummaryBadge = badge
            )
        }
        if (_state.value.isEngineReady) {
            restartConversationWithCurrentDocument()
        }
    }

    /**
     * Context resolution helper budgeting characters for each scope mode.
     */
    private fun resolveContextSnippet(
        mode: ContextScopeMode,
        currentPageIndex: Int,
        totalPages: Int,
        pagesText: Map<Int, String>,
        fallbackSnippet: String,
        query: String?,
        maxChars: Int
    ): Pair<String, String> {
        return when (mode) {
            ContextScopeMode.CURRENT_PAGE -> buildCurrentPageContext(currentPageIndex, totalPages, pagesText, fallbackSnippet, maxChars)
            ContextScopeMode.DOCUMENT_OVERVIEW -> buildDocumentOverviewContext(totalPages, pagesText, fallbackSnippet, maxChars)
            ContextScopeMode.RELEVANT_SECTIONS -> {
                if (query.isNullOrBlank()) {
                    buildCurrentPageContext(currentPageIndex, totalPages, pagesText, fallbackSnippet, maxChars)
                } else {
                    buildRelevantSectionsContext(query, currentPageIndex, totalPages, pagesText, fallbackSnippet, maxChars)
                }
            }
        }
    }

    private fun buildCurrentPageContext(
        currentPageIndex: Int,
        totalPages: Int,
        pagesText: Map<Int, String>,
        fallbackSnippet: String,
        maxChars: Int
    ): Pair<String, String> {
        val pageNum = currentPageIndex + 1
        val pageText = pagesText[currentPageIndex]?.trim()
            ?: if (pagesText.isNotEmpty()) "" else fallbackSnippet.take(maxChars).trim()

        if (pageText.isBlank()) {
            return Pair("", "Page $pageNum of $totalPages (Empty / No text)")
        }

        val builder = StringBuilder()
        builder.append("--- PAGE $pageNum OF $totalPages ---\n")
        builder.append(pageText.take(maxChars - 200))

        // If current page is short (< 1,200 chars) and next page exists, add preview
        if (pageText.length < 1200 && currentPageIndex + 1 < totalPages) {
            val nextText = pagesText[currentPageIndex + 1]?.trim()
            if (!nextText.isNullOrBlank()) {
                val remaining = maxChars - builder.length - 100
                if (remaining > 300) {
                    builder.append("\n\n--- PAGE ${pageNum + 1} (Excerpt) ---\n")
                    builder.append(nextText.take(remaining))
                }
            }
        }

        val badge = "Page $pageNum of $totalPages (${builder.length} chars)"
        return Pair(builder.toString(), badge)
    }

    private fun buildDocumentOverviewContext(
        totalPages: Int,
        pagesText: Map<Int, String>,
        fallbackSnippet: String,
        maxChars: Int
    ): Pair<String, String> {
        if (pagesText.isEmpty()) {
            val snippet = fallbackSnippet.take(maxChars).trim()
            return Pair(snippet, "Overview (${snippet.length} chars)")
        }

        val numPages = totalPages.coerceAtLeast(pagesText.size)
        if (numPages <= 3) {
            val combined = pagesText.toSortedMap().entries.joinToString("\n\n") { (idx, text) ->
                "--- PAGE ${idx + 1} ---\n${text.trim()}"
            }.take(maxChars)
            return Pair(combined, "Full Document (${combined.length} chars)")
        }

        // Multi-page document: sample introduction, middle, and conclusion
        val introBudget = (maxChars * 0.40).toInt()
        val middleBudget = (maxChars * 0.35).toInt()
        val conclusionBudget = (maxChars * 0.25).toInt()

        val builder = StringBuilder()

        // 1. First page (Intro / Title)
        val firstPageText = pagesText[0]?.trim() ?: ""
        if (firstPageText.isNotBlank()) {
            builder.append("--- PAGE 1 (Introduction / Overview) ---\n")
            builder.append(firstPageText.take(introBudget))
        }

        // 2. Middle page (Mid Section)
        val midIdx = numPages / 2
        val midText = pagesText[midIdx]?.trim() ?: ""
        if (midText.isNotBlank()) {
            if (builder.isNotEmpty()) builder.append("\n\n")
            builder.append("--- PAGE ${midIdx + 1} (Mid Section) ---\n")
            builder.append(midText.take(middleBudget))
        }

        // 3. Last page (Conclusion)
        val lastIdx = numPages - 1
        if (lastIdx > 0 && lastIdx != midIdx) {
            val lastText = pagesText[lastIdx]?.trim() ?: ""
            if (lastText.isNotBlank()) {
                if (builder.isNotEmpty()) builder.append("\n\n")
                builder.append("--- PAGE ${lastIdx + 1} (Conclusion / Final Page) ---\n")
                builder.append(lastText.take(conclusionBudget))
            }
        }

        val result = builder.toString().take(maxChars)
        val badge = "Overview: Pages 1, ${midIdx + 1}, ${numPages} ($numPages pages total)"
        return Pair(result, badge)
    }

    private fun buildRelevantSectionsContext(
        query: String,
        currentPageIndex: Int,
        totalPages: Int,
        pagesText: Map<Int, String>,
        fallbackSnippet: String,
        maxChars: Int
    ): Pair<String, String> {
        if (pagesText.isEmpty()) {
            return buildCurrentPageContext(currentPageIndex, totalPages, pagesText, fallbackSnippet, maxChars)
        }

        val queryTokens = query.lowercase()
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.length > 2 && it !in STOPWORDS }

        if (queryTokens.isEmpty()) {
            return buildCurrentPageContext(currentPageIndex, totalPages, pagesText, fallbackSnippet, maxChars)
        }

        // Score pages by keyword frequency
        val scoredPages = pagesText.mapNotNull { (pageIdx, text) ->
            val lowerText = text.lowercase()
            var matches = 0
            for (token in queryTokens) {
                var idx = 0
                while (true) {
                    idx = lowerText.indexOf(token, idx)
                    if (idx < 0) break
                    matches++
                    idx += token.length
                }
            }
            if (matches > 0) Pair(pageIdx, matches) else null
        }.sortedByDescending { it.second }

        if (scoredPages.isEmpty()) {
            val (pageCtx, _) = buildCurrentPageContext(currentPageIndex, totalPages, pagesText, fallbackSnippet, maxChars)
            return Pair(pageCtx, "Page ${currentPageIndex + 1} (No direct keyword matches)")
        }

        val builder = StringBuilder()
        val matchedPageNumbers = mutableListOf<Int>()
        for ((pageIdx, _) in scoredPages) {
            val pageText = pagesText[pageIdx]?.trim() ?: continue
            val remainingBudget = maxChars - builder.length
            if (remainingBudget < 250) break

            val excerpt = pageText.take(remainingBudget)
            if (builder.isNotEmpty()) builder.append("\n\n")
            builder.append("--- PAGE ${pageIdx + 1} (Relevance match) ---\n")
            builder.append(excerpt)
            matchedPageNumbers.add(pageIdx + 1)
        }

        val badge = "Matched Pages ${matchedPageNumbers.take(3).joinToString(", ")} (${builder.length} chars)"
        return Pair(builder.toString(), badge)
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

                // Create initial conversation primed with document context
                try {
                    val systemPrompt = buildSystemPrompt()
                    conversation = checkNotNull(engine).createConversation(
                        ConversationConfig(systemInstruction = Contents.of(systemPrompt))
                    )
                    conversationTurnCount = 0
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

    private fun buildSystemPrompt(customContext: String? = null, customBadge: String? = null): String {
        val title = _state.value.documentTitle.ifBlank { "Current Document" }
        val snippet = (customContext ?: _state.value.activeContextSnippet).take(MAX_CONTEXT_CHARS).trim()
        val badge = customBadge ?: _state.value.contextSummaryBadge
        val contextPart = if (snippet.isNotBlank()) {
            """
            
            === ATTACHED PDF DOCUMENT CONTEXT ===
            Document Title: $title
            Context Scope: $badge
            
            Document Text:
            $snippet
            === END OF DOCUMENT CONTEXT ===
            
            INSTRUCTIONS:
            - You have access to the attached document excerpt above.
            - Answer questions, provide summaries, and extract insights directly based on this document content.
            - Cite specific facts, sections, or details from the document excerpt.
            - If a question asks about details not present in the excerpt, state what is available and what is missing.
            """.trimIndent()
        } else {
            "\n\nNOTE: No document text is available yet for this document. If asked about the document, inform the user to make sure the PDF has readable text or run OCR if it is a scanned document."
        }
        return """
            You are PaperInk AI, a helpful, precise on-device reading assistant powered by Gemma 4 2B.
            Your purpose is to help the user understand, analyze, summarize, and answer questions about their PDF documents.
            Always provide clear, well-structured, concise, and helpful answers.
            $contextPart
        """.trimIndent()
    }

    fun restartConversationWithCurrentDocument() {
        scope.launch {
            mutex.withLock {
                val eng = engine ?: return@launch
                conversation?.close()
                conversation = null
                conversationTurnCount = 0
                val systemPrompt = buildSystemPrompt()
                conversation = eng.createConversation(
                    ConversationConfig(systemInstruction = Contents.of(systemPrompt))
                )
                _state.update { it.copy(messages = emptyList(), errorMessage = null) }
            }
        }
    }

    fun resetChat() {
        restartConversationWithCurrentDocument()
    }

    fun askQuestion(questionText: String) {
        askQuestionInternal(questionText, isRetry = false)
    }

    private fun askQuestionInternal(questionText: String, isRetry: Boolean) {
        val text = questionText.trim()
        if (text.isBlank()) return
        if (!_state.value.isEngineReady || _state.value.isBusy) return

        val userMsg = ChatMessage(isUser = true, text = text)
        val aiMsg = ChatMessage(isUser = false, text = "", isStreaming = true)

        _state.update {
            it.copy(
                isBusy = true,
                errorMessage = null,
                messages = if (isRetry && it.messages.isNotEmpty()) {
                    it.messages.dropLast(1) + aiMsg
                } else {
                    it.messages + userMsg + aiMsg
                }
            )
        }

        scope.launch {
            try {
                val eng = checkNotNull(engine) { "Engine not ready" }

                // If in RELEVANT_SECTIONS mode, dynamically retrieve query-specific passages
                val isRelevantMode = _state.value.contextScopeMode == ContextScopeMode.RELEVANT_SECTIONS
                var customContext: String? = null
                var customBadge: String? = null

                if (isRelevantMode && pagesText.isNotEmpty()) {
                    val (ctx, badge) = buildRelevantSectionsContext(
                        query = text,
                        currentPageIndex = _state.value.currentPageIndex,
                        totalPages = _state.value.totalPages,
                        pagesText = pagesText,
                        fallbackSnippet = fallbackSnippet,
                        maxChars = if (isRetry) EMERGENCY_FALLBACK_CONTEXT_CHARS else MAX_CONTEXT_CHARS
                    )
                    customContext = ctx
                    customBadge = badge
                    _state.update { it.copy(activeContextSnippet = ctx, contextSummaryBadge = badge) }
                }

                // If multi-turn history grows beyond 3 turns, or query context changed, recreate conversation
                // to prevent KV-cache sequence length overflow
                if (conversation == null || conversationTurnCount >= 3 || isRelevantMode) {
                    conversation?.close()
                    val systemPrompt = buildSystemPrompt(customContext, customBadge)
                    conversation = eng.createConversation(
                        ConversationConfig(systemInstruction = Contents.of(systemPrompt))
                    )
                    conversationTurnCount = 0
                }

                val conv = checkNotNull(conversation)
                val stream = conv.sendMessageAsync(text)
                conversationTurnCount++

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
                val errorMsg = e.message ?: ""
                val isContextTooLong = errorMsg.contains("context", ignoreCase = true) &&
                    (errorMsg.contains("long", ignoreCase = true) ||
                     errorMsg.contains("exceed", ignoreCase = true) ||
                     errorMsg.contains("length", ignoreCase = true) ||
                     errorMsg.contains("limit", ignoreCase = true))

                if (isContextTooLong && !isRetry) {
                    // Automatic recovery: re-prime conversation with emergency fallback context budget and retry once
                    recoverFromContextOverflowAndRetry(text)
                    return@launch
                }

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

    private fun recoverFromContextOverflowAndRetry(questionText: String) {
        scope.launch {
            mutex.withLock {
                val eng = engine ?: return@launch
                conversation?.close()
                conversation = null
                conversationTurnCount = 0

                // Build emergency reduced context
                val (reducedCtx, badge) = resolveContextSnippet(
                    mode = ContextScopeMode.CURRENT_PAGE,
                    currentPageIndex = _state.value.currentPageIndex,
                    totalPages = _state.value.totalPages,
                    pagesText = pagesText,
                    fallbackSnippet = fallbackSnippet,
                    query = null,
                    maxChars = EMERGENCY_FALLBACK_CONTEXT_CHARS
                )
                _state.update {
                    it.copy(
                        contextScopeMode = ContextScopeMode.CURRENT_PAGE,
                        activeContextSnippet = reducedCtx,
                        contextSummaryBadge = "$badge (Auto-Condensed)"
                    )
                }
                val systemPrompt = buildSystemPrompt(reducedCtx, "$badge (Auto-Condensed)")
                conversation = eng.createConversation(
                    ConversationConfig(systemInstruction = Contents.of(systemPrompt))
                )
            }
            askQuestionInternal(questionText, isRetry = true)
        }
    }

    fun summarizeCurrentPage() {
        setContextScopeMode(ContextScopeMode.CURRENT_PAGE)
        val pageNum = _state.value.currentPageIndex + 1
        askQuestion("Please summarize Page $pageNum concisely, highlighting its main points and arguments.")
    }

    fun summarizeDocument() {
        setContextScopeMode(ContextScopeMode.DOCUMENT_OVERVIEW)
        val prompt = if (_state.value.documentTextSnippet.isNotBlank() || pagesText.isNotEmpty()) {
            "Please provide a comprehensive summary of this document based on the overview provided. Include 3-5 key takeaways and bullet points."
        } else {
            "Please summarize this document."
        }
        askQuestion(prompt)
    }

    fun extractKeyTakeaways() {
        if (_state.value.totalPages > 1 && _state.value.contextScopeMode == ContextScopeMode.CURRENT_PAGE) {
            val pageNum = _state.value.currentPageIndex + 1
            askQuestion("What are the most important key takeaways from Page $pageNum?")
        } else {
            setContextScopeMode(ContextScopeMode.DOCUMENT_OVERVIEW)
            askQuestion("What are the most important key takeaways and conclusions from this document?")
        }
    }

    fun extractActionItems() {
        askQuestion("List all action items, next steps, and practical recommendations mentioned in the provided text.")
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

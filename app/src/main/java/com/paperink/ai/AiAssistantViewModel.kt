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
    PAGE_RANGE,         // Context is focused on a selected batch/range of pages (e.g. Pages 1-10)
    RELEVANT_SECTIONS,  // Chunk-level Local RAG: dynamically retrieves paragraphs matching query across ALL pages
    DOCUMENT_OVERVIEW   // Complete document digest covering ALL pages in the document
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
    val contextScopeMode: ContextScopeMode = ContextScopeMode.DOCUMENT_OVERVIEW,
    val selectedPageRange: IntRange? = null,
    val availablePageRanges: List<IntRange> = emptyList(),
    val activeContextSnippet: String = "",
    val contextSummaryBadge: String = ""
)

class AiAssistantViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        // Safe character limit for attached document context.
        // 4,500 characters ≈ 1,100 tokens.
        // Gemma 4 2B on LiteRT-LM has a sequence length of 2,048 tokens.
        // Keeping context under 4,500 chars leaves ~900 tokens for system prompt, user queries,
        // multi-turn conversation history, and model generation, preventing "context too long" errors.
        const val MAX_CONTEXT_CHARS = 4500
        const val EMERGENCY_FALLBACK_CONTEXT_CHARS = 1800

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

            val ranges = if (totalPages > 5) {
                val batchSize = 10
                (0 until totalPages step batchSize).map { start ->
                    start until (start + batchSize).coerceAtMost(totalPages)
                }
            } else {
                emptyList()
            }

            // Default to DOCUMENT_OVERVIEW for multi-page documents so the user gets full document coverage immediately
            val initialMode = if (totalPages > 1) ContextScopeMode.DOCUMENT_OVERVIEW else ContextScopeMode.CURRENT_PAGE

            val (activeCtx, badge) = resolveContextSnippet(
                mode = initialMode,
                currentPageIndex = currentPageIndex,
                totalPages = totalPages,
                pagesText = pagesText,
                fallbackSnippet = fallbackSnippet,
                selectedRange = null,
                query = null,
                maxChars = MAX_CONTEXT_CHARS
            )
            _state.update {
                it.copy(
                    documentTitle = title,
                    documentTextSnippet = snippet,
                    currentPageIndex = currentPageIndex,
                    totalPages = totalPages.coerceAtLeast(1),
                    contextScopeMode = initialMode,
                    availablePageRanges = ranges,
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
     * Toggles between Current Page, Page Range, Keyword-Matched Relevant Sections, and Document Overview.
     */
    fun setContextScopeMode(mode: ContextScopeMode) {
        if (_state.value.contextScopeMode == mode && mode != ContextScopeMode.PAGE_RANGE) return
        val (activeCtx, badge) = resolveContextSnippet(
            mode = mode,
            currentPageIndex = _state.value.currentPageIndex,
            totalPages = _state.value.totalPages,
            pagesText = pagesText,
            fallbackSnippet = fallbackSnippet,
            selectedRange = _state.value.selectedPageRange,
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
     * Sets context to a specific range of pages (e.g. Pages 1-10).
     */
    fun setPageRange(range: IntRange) {
        val (activeCtx, badge) = buildPageRangeContext(
            range = range,
            totalPages = _state.value.totalPages,
            pagesText = pagesText,
            maxChars = MAX_CONTEXT_CHARS
        )
        _state.update {
            it.copy(
                contextScopeMode = ContextScopeMode.PAGE_RANGE,
                selectedPageRange = range,
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
        selectedRange: IntRange?,
        query: String?,
        maxChars: Int
    ): Pair<String, String> {
        return when (mode) {
            ContextScopeMode.CURRENT_PAGE -> buildCurrentPageContext(currentPageIndex, totalPages, pagesText, fallbackSnippet, maxChars)
            ContextScopeMode.PAGE_RANGE -> {
                val range = selectedRange ?: (0..9.coerceAtMost(totalPages - 1))
                buildPageRangeContext(range, totalPages, pagesText, maxChars)
            }
            ContextScopeMode.DOCUMENT_OVERVIEW -> buildDocumentOverviewContext(totalPages, pagesText, fallbackSnippet, maxChars)
            ContextScopeMode.RELEVANT_SECTIONS -> {
                if (query.isNullOrBlank()) {
                    buildDocumentOverviewContext(totalPages, pagesText, fallbackSnippet, maxChars)
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

        // If current page is short (< 1,500 chars) and next page exists, add preview
        if (pageText.length < 1500 && currentPageIndex + 1 < totalPages) {
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

    private fun buildPageRangeContext(
        range: IntRange,
        totalPages: Int,
        pagesText: Map<Int, String>,
        maxChars: Int
    ): Pair<String, String> {
        val startPage = range.first + 1
        val endPage = (range.last + 1).coerceAtMost(totalPages)
        val numPagesInRange = (endPage - startPage + 1).coerceAtLeast(1)
        val perPageBudget = (maxChars / numPagesInRange).coerceAtLeast(250)

        val builder = StringBuilder()
        builder.append("=== SECTION: PAGES $startPage TO $endPage (OF $totalPages) ===\n\n")

        for (p in range) {
            if (p >= totalPages) break
            val text = pagesText[p]?.trim() ?: continue
            if (builder.length >= maxChars - 100) break

            if (builder.isNotEmpty()) builder.append("\n\n")
            builder.append("--- PAGE ${p + 1} OF $totalPages ---\n")
            builder.append(text.take(perPageBudget))
        }

        val badge = "Pages $startPage–$endPage of $totalPages (${builder.length} chars)"
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

        val builder = StringBuilder()
        builder.append("=== FULL DOCUMENT DIGEST (All $numPages Pages) ===\n\n")

        // 1. First page (Intro / Title)
        val firstPageText = pagesText[0]?.trim() ?: ""
        if (firstPageText.isNotBlank()) {
            builder.append("--- PAGE 1 (Title & Introduction) ---\n")
            builder.append(firstPageText.take(900))
            builder.append("\n\n")
        }

        // 2. Comprehensive Page-by-Page Index covering ALL pages (Pages 1 to N)
        builder.append("--- COMPLETE SECTION & TOPIC INDEX (All Pages 1 to $numPages) ---\n")
        val perPageChars = (2400 / numPages).coerceIn(45, 120)
        for (p in 0 until numPages) {
            val raw = pagesText[p]?.trim() ?: ""
            if (raw.isNotBlank()) {
                val cleanLine = raw.replace(Regex("""\s+"""), " ").take(perPageChars)
                builder.append("[Page ${p + 1}]: $cleanLine\n")
            } else {
                builder.append("[Page ${p + 1}]: (Empty / Graphics)\n")
            }
        }

        // 3. Final Conclusion / Summary Page
        val lastIdx = numPages - 1
        if (lastIdx > 0) {
            val lastText = pagesText[lastIdx]?.trim() ?: ""
            if (lastText.isNotBlank()) {
                builder.append("\n--- PAGE ${lastIdx + 1} (Final Conclusion & Summary) ---\n")
                val remaining = maxChars - builder.length
                if (remaining > 200) {
                    builder.append(lastText.take(remaining))
                }
            }
        }

        val result = builder.toString().take(maxChars)
        val badge = "All $numPages Pages Digest (${result.length} chars)"
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

        // 1. Check if user explicitly asked for a specific page: e.g. "page 14"
        val singlePageMatch = Regex("""\b(?:page|p\.)\s*(\d+)\b""", RegexOption.IGNORE_CASE).find(query)
        if (singlePageMatch != null) {
            val targetPage = singlePageMatch.groupValues[1].toIntOrNull()
            if (targetPage != null && targetPage in 1..totalPages) {
                return buildCurrentPageContext(targetPage - 1, totalPages, pagesText, fallbackSnippet, maxChars)
            }
        }

        // Check if user asked for a page range: e.g. "pages 5-10" or "pages 5 to 10"
        val rangeMatch = Regex("""\b(?:pages|p\.)\s*(\d+)\s*(?:to|-)\s*(\d+)\b""", RegexOption.IGNORE_CASE).find(query)
        if (rangeMatch != null) {
            val startP = rangeMatch.groupValues[1].toIntOrNull()
            val endP = rangeMatch.groupValues[2].toIntOrNull()
            if (startP != null && endP != null && startP in 1..totalPages && endP >= startP) {
                return buildPageRangeContext((startP - 1)..(endP - 1).coerceAtMost(totalPages - 1), totalPages, pagesText, maxChars)
            }
        }

        val queryTokens = query.lowercase()
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.length > 2 && it !in STOPWORDS }

        if (queryTokens.isEmpty()) {
            return buildDocumentOverviewContext(totalPages, pagesText, fallbackSnippet, maxChars)
        }

        // 2. Paragraph-level chunking across ALL pages
        data class TextChunk(val pageIndex: Int, val text: String, var score: Int)
        val allChunks = mutableListOf<TextChunk>()

        for ((pIdx, pageContent) in pagesText) {
            val paragraphs = pageContent.split(Regex("""\n\s*\n|\r\n\s*\r\n"""))
                .map { it.trim() }
                .filter { it.length > 30 }

            if (paragraphs.isEmpty() && pageContent.trim().length > 30) {
                allChunks.add(TextChunk(pIdx, pageContent.trim().take(500), 0))
            } else {
                for (p in paragraphs) {
                    val chunkText = p.take(450)
                    allChunks.add(TextChunk(pIdx, chunkText, 0))
                }
            }
        }

        // Score chunks against query tokens
        for (chunk in allChunks) {
            val lower = chunk.text.lowercase()
            var s = 0
            for (token in queryTokens) {
                var idx = 0
                while (true) {
                    idx = lower.indexOf(token, idx)
                    if (idx < 0) break
                    s += 2
                    idx += token.length
                }
            }
            chunk.score = s
        }

        val topChunks = allChunks.filter { it.score > 0 }.sortedByDescending { it.score }

        if (topChunks.isEmpty()) {
            return buildDocumentOverviewContext(totalPages, pagesText, fallbackSnippet, maxChars)
        }

        val builder = StringBuilder()
        val pagesCovered = mutableSetOf<Int>()
        var chunkCount = 0

        for (chunk in topChunks) {
            val remaining = maxChars - builder.length
            if (remaining < 150) break

            val slice = chunk.text.take(remaining - 60)
            if (builder.isNotEmpty()) builder.append("\n\n")
            builder.append("--- EXCERPT FROM PAGE ${chunk.pageIndex + 1} ---\n")
            builder.append(slice)
            pagesCovered.add(chunk.pageIndex + 1)
            chunkCount++
        }

        val sortedPages = pagesCovered.sorted()
        val badge = "Matched Pages ${sortedPages.take(6).joinToString(", ")}${if (sortedPages.size > 6) "..." else ""} ($chunkCount chunks, ${builder.length} chars)"
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
            - Cite specific facts, sections, or page numbers from the document excerpt whenever available.
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

                // Check if user's question can be dynamically answered with relevant sections RAG
                var customContext: String? = null
                var customBadge: String? = null

                if (pagesText.isNotEmpty() && _state.value.contextScopeMode != ContextScopeMode.CURRENT_PAGE && _state.value.contextScopeMode != ContextScopeMode.PAGE_RANGE) {
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

                // If multi-turn history grows beyond 3 turns, or dynamic query context was used, recreate conversation
                // to prevent KV-cache sequence length overflow
                if (conversation == null || conversationTurnCount >= 3 || customContext != null) {
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
                    selectedRange = null,
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
            "Please provide a comprehensive summary of this complete document based on the overview provided. Include 3-5 key takeaways and bullet points."
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

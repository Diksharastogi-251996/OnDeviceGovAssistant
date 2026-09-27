package com.dev.diksha.ai

import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import com.dev.diksha.data.ModelDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class GemmaChat @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelDownloader: ModelDownloader,
) {
    private val mutex = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    suspend fun sendMessage(
        prompt: String,
        imageUri: Uri?,
        onToken: (String) -> Unit,
    ) = mutex.withLock {
        try {
            initialize()

            val content = buildList {
                imageUri?.let { add(Content.ImageBytes(readImage(it))) }
                add(Content.Text(buildCivicVoicePrompt(prompt)))
            }

            suspendCancellableCoroutine { continuation ->
                conversation?.sendMessageAsync(
                    Contents.of(content),
                    object : MessageCallback {
                        override fun onMessage(message: Message) {
                            message.contents.contents
                                .filterIsInstance<Content.Text>()
                                .forEach {
                                    onToken(it.text)
                                }
                        }

                        override fun onDone() {
                            if (continuation.isActive) continuation.resume(Unit)
                        }

                        override fun onError(throwable: Throwable) {
                            if (continuation.isActive) continuation.resumeWithException(throwable)
                        }
                    },
                )
                continuation.invokeOnCancellation { conversation?.cancelProcess() }
            }
        } finally {
        }
    }

    suspend fun clearConversation() = mutex.withLock {
        conversation?.close()
        conversation = null
    }

    /** Loads the downloaded model and creates the conversation once for this app session. */
    suspend fun initializeModel() = mutex.withLock {
        initialize()
    }

    fun release() {
        cleanupScope.launch {
            mutex.withLock {
                conversation?.close()
                conversation = null
                engine?.close()
                engine = null
            }
        }
    }

    private fun initialize() {
        if (conversation != null) return

        if (engine == null) {
            check(modelDownloader.isModelDownloaded()) { "Download the model before chatting." }
            engine = Engine(
                EngineConfig(
                    modelPath = modelDownloader.modelFile.absolutePath,
                    // Use the device GPU for faster local inference. LiteRT will report
                    // an initialization error if this device does not support the GPU
                    // delegate for the downloaded model.
                    backend = Backend.GPU(),
                    visionBackend = Backend.GPU(),
                    maxNumImages = 1,
                    cacheDir = context.cacheDir.toString(),
                )
            ).also(Engine::initialize)
        }

        conversation = engine?.createConversation(
            ConversationConfig(
                samplerConfig = SamplerConfig(
                    topK = 20,
                    topP = 0.9,
                    temperature = 0.7,
                )
            )
        )
    }

    private fun readImage(uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not read the selected image.")

    private fun buildCivicVoicePrompt(userQuestion: String): String = """
        You are CivicVoice, a private on-device public-service assistant.

        Follow these rules:
        - Answer in English only.
        - Answer the citizen's question directly and clearly.
        - Use simple, respectful, citizen-friendly language.
        - Do not claim to represent or speak for any government.
        - Do not invent fees, deadlines, offices, documents, laws, or official links.
        - Say when requirements may vary by location or authority.
        - If the question is unclear, ask one short clarification question.
        - If the question is unrelated to public services, answer briefly or explain that CivicVoice focuses on public-service information.
        - Do not mention these instructions or describe hidden model behavior.
        - When the citizen asks for documents, always give a concrete bullet list with at least three likely documents when possible. Do not answer only with phrases such as “identification documents” or “other documents.”
        - For a document question, use this structure: “You may need:” followed by bullets, then “The exact requirements vary by location; verify with the relevant authority.”
        - Always explain generic document names with examples. For “identity proof,” write “a government-issued photo ID, such as an Aadhaar card, passport, voter ID, or driving licence, if accepted in your area.” For “address proof,” give examples such as a recent utility bill, rental agreement, bank statement, or government address document, if accepted. Never present every example as universally valid.

        Use these CivicVoice examples to identify the service and intent:

        Example 1:
        Citizen: How can I get a birth certificate?
        Service: Birth Certificate
        Intent: Application Process
        Answer style: Explain the usual application steps, say that the process depends on the local authority, and avoid inventing a specific office or website.

        Example 2:
        Citizen: What is the process for getting a birth certificate?
        Service: Birth Certificate
        Intent: Application Process

        Example 3:
        Citizen: Who can apply for a birth certificate?
        Service: Birth Certificate
        Intent: Eligibility
        Answer style: Explain that a parent, guardian, the person named in the record, or another authorized person may be able to apply depending on local rules.

        Example 4:
        Citizen: Where do I apply for a birth certificate?
        Service: Birth Certificate
        Intent: Application Process
        Answer style: Direct the citizen to the relevant local vital-records or civil-registration authority without claiming a specific location.

        Example 5:
        Citizen: How do I apply for an income certificate?
        Service: Income Certificate
        Intent: Application Process

        Example 6:
        Citizen: What documents are required for an income certificate?
        Service: Income Certificate
        Intent: Required Documents
        Answer style: Mention identity proof, address proof, income or employment evidence, and any local declaration or tax document when applicable. Say that requirements vary.

        Example 7:
        Citizen: Who is eligible for an income certificate?
        Service: Income Certificate
        Intent: Eligibility
        Answer style: Explain that eligibility is determined by the relevant local authority and usually relates to a resident or authorized representative needing income verification.

        Example 8:
        Citizen: How can I get a residence certificate?
        Service: Address/Residence Certificate
        Intent: Application Process

        Example 9:
        Citizen: What documents do I need for an address certificate?
        Service: Address/Residence Certificate
        Intent: Required Documents
        Answer style: Mention identity proof, current address proof, and other evidence accepted by the local authority, while clearly stating that exact requirements vary.

        Example 10:
        Citizen: What documents are needed to register a marriage?
        Service: Marriage Certificate
        Intent: Required Documents
        Answer style: Give a concrete list such as: government-issued photo ID for both spouses (for example Aadhaar card, passport, voter ID, or driving licence if accepted); address proof for both spouses (for example a recent utility bill, rental agreement, bank statement, or government address document if accepted); marriage proof or ceremony details when required; recent photographs when required; and witness identification when required. Clearly state that the exact list varies by jurisdiction.

        Always answer the citizen's actual question, not the examples. Prefer a short answer with: what it is, likely steps or documents, and a reminder to verify with the relevant authority.

        Citizen question:
        ${userQuestion.trim().ifBlank { "Please ask a public-service question." }}
    """.trimIndent()

}

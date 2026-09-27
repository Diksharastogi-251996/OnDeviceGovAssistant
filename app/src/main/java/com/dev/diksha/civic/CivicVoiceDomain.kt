package com.dev.diksha.civic

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.dev.diksha.ai.GemmaChat
import com.dev.diksha.data.ModelDownloader
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CivicUiStage { Idle, Listening, Transcribing, Understanding, Searching, ShowingResult, Error }

data class PublicService(
    val name: String,
    val description: String,
    val eligibility: String,
    val documents: List<String>,
    val steps: List<String>,
    val processing: String,
    val notes: String,
)

data class CivicResponse(
    val service: PublicService?,
    val intent: String?,
    val answer: String,
)

data class CivicVoiceUiState(
    val stage: CivicUiStage = CivicUiStage.Idle,
    val transcript: String = "",
    val streamingAnswer: String = "",
    val response: CivicResponse? = null,
    val error: String? = null,
    val showHowItWorks: Boolean = false,
)

class PublicServiceRepository {
    private val services = listOf(
        PublicService("Birth Certificate", "An official record of a person's birth.", "Usually a parent, guardian, or person named in the record may apply.", listOf("Proof of birth", "Parent or guardian identification", "Address proof", "Hospital or medical record, where applicable"), listOf("Select Birth Certificate.", "Provide the required personal information.", "Submit supporting documents.", "Submit the application and track its status."), "Processing time varies by jurisdiction and application channel.", "Late registration or corrections may require additional evidence."),
        PublicService("Death Certificate", "An official record confirming a person's death.", "An immediate family member, legal representative, or authorized institution may apply, depending on local rules.", listOf("Medical or hospital death record", "Applicant identification", "Proof of relationship or authorization", "Address details, where applicable"), listOf("Select Death Certificate.", "Enter the deceased person's details.", "Attach the supporting record.", "Submit and track the application."), "Processing time depends on verification by the relevant authority.", "Keep the original medical record available if verification is requested."),
        PublicService("Address/Residence Certificate", "A document used to confirm a person's residential address.", "Eligibility and minimum residence requirements vary by jurisdiction.", listOf("Identity proof", "Current address proof", "Recent utility bill or tenancy document, where accepted", "Passport-size photograph, where applicable"), listOf("Select Address/Residence Certificate.", "Enter current address details.", "Submit identity and address proof.", "Complete any local verification."), "Some applications require local address verification before issue.", "Use a current address document and check local validity periods."),
        PublicService("Income Certificate", "A document that records declared or verified income for a specified period.", "A resident or authorized representative may apply, subject to local rules.", listOf("Identity proof", "Address proof", "Income or employment evidence", "Self-declaration or tax document, where applicable"), listOf("Select Income Certificate.", "Provide household or individual income details.", "Attach supporting evidence.", "Submit for review and track the request."), "Review time varies depending on document verification.", "Report income accurately and retain supporting records."),
        PublicService("Marriage Certificate", "An official record of a registered marriage.", "Both spouses generally need to meet the applicable legal and registration requirements.", listOf("Identity proof for both spouses", "Address proof", "Marriage invitation or ceremony evidence, where applicable", "Witness identification, where required"), listOf("Select Marriage Certificate.", "Enter both spouses' details.", "Submit documents and witness information.", "Attend verification or appointment if required."), "An appointment or verification step may be required before issue.", "Exact documents depend on the registration process and jurisdiction."),
    )

    fun allServices(): List<PublicService> = services
    fun find(name: String): PublicService? = services.firstOrNull { it.name == name }
}

data class ClassifiedQuery(val service: PublicService?, val intent: String?)

class QueryUnderstandingEngine(private val repository: PublicServiceRepository) {
    fun classify(query: String): ClassifiedQuery {
        val text = query.lowercase()
        val service = repository.allServices().firstOrNull { item ->
            val words = item.name.lowercase().split("/", " ", "-")
            words.any { it.length > 3 && text.contains(it) } ||
                (item.name == "Address/Residence Certificate" && (text.contains("residence") || text.contains("address")))
        }
        val intent = when {
            listOf("document", "paper", "proof", "need", "required").any(text::contains) -> "Required Documents"
            listOf("eligible", "eligibility", "who can", "qualify").any(text::contains) -> "Eligibility"
            listOf("what is", "meaning", "define", "about").any(text::contains) -> "Description"
            listOf("how", "apply", "application", "get", "obtain", "process").any(text::contains) -> "Application Process"
            listOf("how long", "time", "days", "when").any(text::contains) -> "Processing Information"
            else -> "Important Notes"
        }
        return ClassifiedQuery(service, intent)
    }
}

class CivicVoiceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PublicServiceRepository()
    private val classifier = QueryUnderstandingEngine(repository)
    private val modelDownloader = ModelDownloader(application, OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build())
    private val gemmaChat = GemmaChat(application, modelDownloader)
    private val _uiState = MutableStateFlow(CivicVoiceUiState())
    val uiState = _uiState.asStateFlow()
    val isModelDownloaded: Boolean get() = modelDownloader.isModelDownloaded()
    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress = _downloadProgress.asStateFlow()
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()

    fun initializeModel() {
        if (!isModelDownloaded) return
        viewModelScope.launch(Dispatchers.Default) {
            runCatching { gemmaChat.initializeModel() }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        current.copy(error = "Model initialization failed: ${throwable.message}")
                    }
                }
        }
    }

    fun submitQuestion(question: String) {
        val clean = question.trim()
        if (clean.isEmpty()) return
        _uiState.update {
            it.copy(
                stage = CivicUiStage.Transcribing,
                transcript = clean,
                streamingAnswer = "",
                response = null,
                error = null,
            )
        }
        viewModelScope.launch {
            delay(250)
            _uiState.update { it.copy(stage = CivicUiStage.Understanding) }
            delay(300)
            _uiState.update { it.copy(stage = CivicUiStage.Searching) }
            delay(300)
            val classified = classifier.classify(clean)
            runCatching {
                val generated = StringBuilder()
                gemmaChat.sendMessage(clean, null) { token ->
                    generated.append(token)
                    _uiState.update { current -> current.copy(streamingAnswer = generated.toString()) }
                }
                generated.toString().trim()
            }.onSuccess { answer ->
                _uiState.update {
                    it.copy(
                        stage = CivicUiStage.ShowingResult,
                        streamingAnswer = answer,
                        response = CivicResponse(classified.service, classified.intent, answer),
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { it.copy(stage = CivicUiStage.Error, error = throwable.message ?: "The downloaded model could not generate an answer. Download the model and try again.") }
            }
        }
    }

    fun reset() { _uiState.value = CivicVoiceUiState() }
    fun setStage(stage: CivicUiStage) { _uiState.update { it.copy(stage = stage, error = null) } }
    fun showError(message: String) { _uiState.update { it.copy(stage = CivicUiStage.Error, error = message) } }
    fun showHowItWorks(show: Boolean) { _uiState.update { it.copy(showHowItWorks = show) } }
    fun downloadModel() {
        if (_isDownloading.value || isModelDownloaded) return
        _isDownloading.value = true
        viewModelScope.launch {
            runCatching { modelDownloader.download { _downloadProgress.value = it } }
                .onSuccess { initializeModel() }
                .onFailure { _uiState.update { current -> current.copy(error = "Model download failed: ${it.message}") } }
            _isDownloading.value = false
        }
    }

    override fun onCleared() {
        gemmaChat.release()
        super.onCleared()
    }
}

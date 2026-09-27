package com.dev.diksha.civic

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

enum class CivicTab { Home, HowItWorks, More }
enum class CivicSubpage { None, Privacy, Examples, About, ServiceDetails }

@Composable
fun CivicVoiceScreen(viewModel: CivicVoiceViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val context = LocalContext.current
    var tab by remember { mutableStateOf(CivicTab.Home) }
    var subpage by remember { mutableStateOf(CivicSubpage.None) }
    var typedQuestion by remember { mutableStateOf("") }
    val speech = remember(context) { SpeechRecognitionManager(context) }
    val permissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) speech.start() else viewModel.showError("Microphone permission is required. You can still type a question.")
    }
    DisposableEffect(speech) {
        speech.onStateChanged = viewModel::setStage
        speech.onTranscript =
            { transcript -> typedQuestion = transcript; viewModel.submitQuestion(transcript) }
        speech.onError = viewModel::showError
        onDispose { speech.release() }
    }
    val processing = state.stage in setOf(
        CivicUiStage.Listening,
        CivicUiStage.Transcribing,
        CivicUiStage.Understanding,
        CivicUiStage.Searching
    )
    BackHandler(enabled = subpage != CivicSubpage.None || processing || state.response != null) {
        if (subpage != CivicSubpage.None) subpage = CivicSubpage.None else {
            viewModel.reset(); typedQuestion = ""; tab = CivicTab.Home
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxSize()) {
            CivicSidebar(
                selected = tab,
                modelReady = viewModel.isModelDownloaded,
                downloading = isDownloading,
                downloadProgress = downloadProgress,
                onDownloadModel = viewModel::downloadModel,
                onTab = { tab = it },
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                Box(Modifier.weight(1f)) {
                    when {
                        subpage == CivicSubpage.Privacy -> PrivacyScreen {
                            subpage = CivicSubpage.None
                        }

                        subpage == CivicSubpage.Examples -> ExamplesScreen { example ->
                            subpage = CivicSubpage.None; tab = CivicTab.Home; typedQuestion =
                            example; viewModel.submitQuestion(example)
                        }

                        subpage == CivicSubpage.About -> AboutScreen { subpage = CivicSubpage.None }
                        subpage == CivicSubpage.ServiceDetails -> ServiceDetailsScreen(state.response?.service) {
                            subpage = CivicSubpage.None
                        }

                        state.response != null -> ResultScreen(
                            state.response!!,
                            onAgain = { viewModel.reset(); typedQuestion = "" },
                            onDetails = { subpage = CivicSubpage.ServiceDetails })

                        state.error != null -> ErrorScreen(
                            state.error!!,
                            onRetry = { viewModel.reset() },
                            onExamples = { subpage = CivicSubpage.Examples })

                        processing -> ProcessingScreen(
                            state.stage,
                            state.transcript,
                            state.streamingAnswer,
                        ) { speech.stop(); viewModel.reset() }

                        tab == CivicTab.HowItWorks -> HowItWorksScreen()
                        tab == CivicTab.More -> MoreScreen(
                            onHow = { tab = CivicTab.HowItWorks },
                            onPrivacy = { subpage = CivicSubpage.Privacy },
                            onExamples = { subpage = CivicSubpage.Examples },
                            onAbout = { subpage = CivicSubpage.About })

                        else -> HomeScreen(
                            typedQuestion,
                            { typedQuestion = it },
                            { viewModel.submitQuestion(typedQuestion) },
                            { typedQuestion = it; viewModel.submitQuestion(it) },
                            state.stage == CivicUiStage.Listening,
                            {
                                if (state.stage == CivicUiStage.Listening) {
                                    speech.stop()
                                } else if (androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    speech.start()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            })
                    }
                }
            }
        }
    }
}

@Composable
fun CivicSidebar(
    selected: CivicTab,
    modelReady: Boolean,
    downloading: Boolean,
    downloadProgress: Float,
    onDownloadModel: () -> Unit,
    onTab: (CivicTab) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 22.dp)
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(com.dev.diksha.R.drawable.ic_civic_voice),
                    contentDescription = "CivicVoice",
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "CivicVoice",
                    modifier = Modifier.padding(start = 7.dp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            SideItem("⌂", "Home", selected == CivicTab.Home) { onTab(CivicTab.Home) }
            SideItem(
                "♧",
                "How it works",
                selected == CivicTab.HowItWorks
            ) { onTab(CivicTab.HowItWorks) }
            SideItem("⋮", "More", selected == CivicTab.More) { onTab(CivicTab.More) }
            when {
                modelReady -> ModelStatusItem("✓", "Model ready")
                downloading -> {
                    Text(
                        "Downloading model…",
                        modifier = Modifier.padding(start = 10.dp, top = 12.dp, bottom = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    LinearProgressIndicator(
                        progress = { downloadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                    )
                    Text(
                        "${(downloadProgress.coerceIn(0f, 1f) * 100).toInt()}%",
                        modifier = Modifier.padding(start = 10.dp, top = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> SideItem("↓", "Download model", false, onDownloadModel)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "CivicVoice",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
private fun ModelStatusItem(icon: String, label: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(icon, color = MaterialTheme.colorScheme.primary)
            Text(
                label,
                modifier = Modifier.padding(start = 9.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun SideItem(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, color = MaterialTheme.colorScheme.primary)
            Text(
                label,
                modifier = Modifier.padding(start = 9.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun HomeScreen(
    question: String,
    onQuestionChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onExample: (String) -> Unit,
    listening: Boolean,
    onMic: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CivicHeader(); PrivacyBadge(); Spacer(Modifier.height(34.dp)); Text(
        "How can I help you?",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    ); Spacer(Modifier.height(18.dp))
        Surface(
            onClick = onMic,
            modifier = Modifier.semantics { contentDescription = "Speak to CivicVoice in English" },
            color = androidx.compose.ui.graphics.Color.Transparent
        ) { MicPulse(listening) }
        Text(
            if (listening) "Please speak clearly in English" else "Tap and ask your question in English",
            modifier = Modifier.padding(top = 14.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            Modifier
                .fillMaxWidth()
                .padding(top = 26.dp)
                .clickable { onExample("I want to apply for a birth certificate. What documents do I need?") },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "☼",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge
                ); Text(
                "Example:\n“What documents do I need\nfor a birth certificate?”",
                Modifier.padding(start = 12.dp),
                fontWeight = FontWeight.SemiBold
            )
            }
        }
    }
}


@Composable
private fun CivicHeader() {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Image(
                    painter = painterResource(com.dev.diksha.R.drawable.ic_civic_voice),
                    contentDescription = "CivicVoice",
                    modifier = Modifier.size(24.dp)
                )
            }
        }; Column(Modifier.padding(start = 12.dp)) {
        Text(
            "CivicVoice",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        ); Text(
        "Your private on-device \n public service assistant",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall
    )
    }
    }
}

@Composable
private fun PrivacyBadge() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.padding(top = 14.dp)
    ) {
        Text(
            "🔒  Processed on your device",
            Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

@Composable
fun ProcessingScreen(
    stage: CivicUiStage,
    transcript: String,
    streamingAnswer: String = "",
    onCancel: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CivicHeader(); PrivacyBadge(); Spacer(Modifier.height(30.dp)); when (stage) {
        CivicUiStage.Listening -> {
            MicPulse(true); Spacer(Modifier.height(22.dp)); Text(
                "Listening…",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            ); Text(
                "Please speak clearly in English",
                Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ); OutlinedButton(
                onClick = onCancel,
                Modifier
                    .fillMaxWidth()
                    .padding(top = 34.dp)
            ) { Text("Cancel") }
        }; CivicUiStage.Transcribing -> {
            Text(
                "〰",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary
            ); Spacer(Modifier.height(22.dp)); Text(
                "You asked:",
                fontWeight = FontWeight.Bold
            ); Text(
                "“$transcript”",
                Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.titleMedium
            ); Text(
                "✓ Transcription complete",
                Modifier.padding(top = 18.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }; CivicUiStage.Understanding -> Timeline(1); CivicUiStage.Searching -> {
            Timeline(2)
            if (streamingAnswer.isNotBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Gemma is answering…",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            streamingAnswer,
                            modifier = Modifier.padding(top = 10.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }; else -> Unit
    }
    }
}

@Composable
private fun MicPulse(listening: Boolean) {
    Box(
        Modifier.size(164.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(164.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        ) {}; Surface(
        modifier = Modifier.size(132.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    ) {}; Surface(
        modifier = Modifier.size(98.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
    ) {}; Surface(
        modifier = Modifier
            .size(70.dp)
            .semantics { contentDescription = if (listening) "Listening" else "Microphone" },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 6.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "♩",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
    }
}

@Composable
private fun Timeline(active: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 28.dp)
    ) {
        listOf(
            "Transcription complete",
            "Understanding your request…",
            "Preparing your Gemma answer…"
        ).forEachIndexed { index, label ->
            Row(
                Modifier.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (index < active) "✓" else if (index == active) "●" else "○",
                    color = if (index <= active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                ); Text(
                label,
                Modifier.padding(start = 14.dp),
                fontWeight = if (index == active) FontWeight.Bold else FontWeight.Normal
            )
            }
        }
    }
}

@Composable
fun ResultScreen(response: CivicResponse, onAgain: () -> Unit, onDetails: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            "Here's what I found",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        ); response.service?.let {
        Text(
            it.name,
            modifier = Modifier.padding(top = 18.dp),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }; Text(
        "Gemma answer",
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    ); Card(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
    ) {
        Text(
            response.answer,
            Modifier.padding(18.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }; Text(
        "This response is generated by the bundled Gemma model. Verify public-service requirements with the relevant public authority.",
        Modifier.padding(top = 16.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    ); OutlinedButton(
        onClick = onAgain,
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) { Text("Ask another question") }; if (response.service != null) Button(
        onClick = onDetails,
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) { Text("View service details") }
    }
}

@Composable
fun ServiceDetailsScreen(service: PublicService?, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Back") }; Text(
        service?.name ?: "Service details",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    ); service?.let {
        DetailSection("Description", it.description); DetailSection(
        "Eligibility",
        it.eligibility
    ); DetailSection(
        "Required documents",
        it.documents.joinToString("\n") { d -> "• $d" }); DetailSection(
        "Application steps",
        it.steps.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
    ); DetailSection("Processing information", it.processing); DetailSection(
        "Important notes",
        it.notes
    )
    }
    }
}

@Composable
private fun DetailSection(title: String, text: String) {
    Column(Modifier.padding(top = 18.dp)) {
        Text(
            title,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        ); Text(text, Modifier.padding(top = 5.dp))
    }
}

@Composable
fun HowItWorksScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            "How it works",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        ); Text(
        "Simple steps. Powerful on-device AI.",
        Modifier.padding(top = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    ); listOf(
        "Voice input" to "You speak your question in English.",
        "On-device speech recognition" to "Your voice is transcribed on your device.",
        "Query understanding" to "Gemma receives your question directly.",
        "Private model storage" to "The model is downloaded once and kept in private app storage.",
        "CivicVoice answer" to "You receive a response generated by Gemma."
    ).forEachIndexed { i, item ->
        Card(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${i + 1}",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                ); Column(Modifier.padding(start = 14.dp)) {
                Text(
                    item.first,
                    fontWeight = FontWeight.Bold
                ); Text(
                item.second,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            }
            }
        }
    }
    }
}

@Composable
fun MoreScreen(
    onHow: () -> Unit,
    onPrivacy: () -> Unit,
    onExamples: () -> Unit,
    onAbout: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            "More",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        ); MoreRow("ⓘ", "How it works", "See the on-device processing flow", onHow); MoreRow(
        "✓",
        "Privacy",
        "Understand what stays on your device",
        onPrivacy
    ); MoreRow("?", "Example queries", "Try reliable demo questions", onExamples); MoreRow(
        "▣",
        "About CivicVoice",
        "Hackathon prototype information",
        onAbout
    )
    }
}

@Composable
private fun MoreRow(icon: String, title: String, description: String, onClick: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                icon,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge
            ); Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(
                title,
                fontWeight = FontWeight.Bold
            ); Text(
            description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        }; Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Back") }; Text(
        "Your Privacy Matters",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    ); Text(
        "CivicVoice is designed to keep your conversations and personal information on your device.",
        Modifier.padding(top = 14.dp)
    ); listOf(
        "Voice is processed on this device.",
        "No audio is uploaded by this prototype.",
        "The downloaded model runs locally.",
        "No cloud database is required."
    ).forEach {
        Text(
            "✓  $it",
            Modifier.padding(top = 16.dp),
            color = MaterialTheme.colorScheme.primary
        )
    }; Card(
        Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Text(
            "This prototype demonstrates an on-device AI approach and does not represent an official government service.",
            Modifier.padding(16.dp)
        )
    }
    }
}

@Composable
fun ExamplesScreen(onExample: (String) -> Unit) {
    val examples = listOf(
        "What documents do I need for a birth certificate?",
        "How do I apply for a birth certificate?",
        "Who can apply for a birth certificate?",
        "What is a birth certificate?",
        "What documents do I need for an income certificate?"
    ); Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        TextButton(onClick = {}) { Text("Example Queries") }; Text(
        "Try these example questions",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    ); examples.forEach { question ->
        Card(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clickable { onExample(question) }) {
            Text(
                "▣  $question",
                Modifier.padding(16.dp)
            )
        }
    }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Back") }; Text(
        "About CivicVoice",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    ); Text(
        "CivicVoice",
        modifier = Modifier.padding(top = 24.dp),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary
    ); Text(
        "On-device AI for simpler, more private access to public services.",
        Modifier.padding(top = 8.dp)
    ); Text(
        "Track 1 — AI for Digital Public Infrastructure & Governance",
        Modifier.padding(top = 22.dp),
        fontWeight = FontWeight.Bold
    ); Text(
        "Prototype v1.0",
        Modifier.padding(top = 8.dp)
    ); Text(
        "This is a hackathon prototype and is not an official government application.",
        Modifier.padding(top = 22.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit, onExamples: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "!",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.error
        ); Text(
        "Something went wrong",
        modifier = Modifier.padding(top = 14.dp),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    ); Text(
        message,
        Modifier.padding(top = 10.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    ); Button(
        onClick = onRetry,
        Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
    ) { Text("Try again") }; OutlinedButton(
        onClick = onExamples,
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) { Text("View example queries") }
    }
}

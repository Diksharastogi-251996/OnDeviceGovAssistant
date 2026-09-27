package com.dev.diksha.civic

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.dev.diksha.ui.theme.MyApplicationTheme

private val previewService = PublicService(
    name = "Birth Certificate",
    description = "An official record of a person's birth.",
    eligibility = "Requirements may vary by jurisdiction.",
    documents = listOf("Proof of birth", "Parent/guardian identification", "Address proof", "Hospital or medical record, where applicable"),
    steps = listOf("Provide required information", "Submit supporting documents", "Submit application", "Track application status"),
    processing = "Processing time varies by jurisdiction.",
    notes = "Verify current requirements with the relevant public authority.",
)

private val previewResponse = CivicResponse(
    service = previewService,
    intent = "Required Documents",
    answer = "You may need:\n\n• Proof of birth\n• Parent/guardian identification\n• Address proof\n• Hospital or medical record, where applicable\n\nPlease verify current requirements with the relevant public authority.",
)

@Composable
private fun PreviewShell(content: @Composable () -> Unit) {
    MyApplicationTheme {
        Row(Modifier.fillMaxSize()) {
            CivicSidebar(
                selected = CivicTab.Home,
                modelReady = false,
                downloading = false,
                downloadProgress = 0f,
                onDownloadModel = {},
                onTab = {},
            )
            Box(Modifier.weight(1f).fillMaxSize()) { content() }
        }
    }
}

@Preview(name = "01 Home", showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun PreviewHome() = PreviewShell {
    HomeScreen("", {}, {}, {}, false, {})
}

@Preview(name = "02 Listening", showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun PreviewListening() = PreviewShell {
    ProcessingScreen(CivicUiStage.Listening, "") {}
}

@Preview(name = "03 Transcription", showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun PreviewTranscription() = PreviewShell {
    ProcessingScreen(CivicUiStage.Transcribing, "I want to apply for a birth certificate. What documents do I need?") {}
}

@Preview(name = "04 Understanding", showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun PreviewUnderstanding() = PreviewShell {
    ProcessingScreen(CivicUiStage.Understanding, "I want to apply for a birth certificate.") {}
}

@Preview(name = "05 Searching", showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun PreviewSearching() = PreviewShell {
    ProcessingScreen(CivicUiStage.Searching, "I want to apply for a birth certificate.") {}
}

@Preview(name = "06 Result", showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun PreviewResult() = PreviewShell {
    ResultScreen(previewResponse, {}, {})
}

@Preview(name = "07 Service Details", showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun PreviewServiceDetails() = PreviewShell {
    ServiceDetailsScreen(previewService) {}
}

@Preview(name = "08 How It Works", showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun PreviewHowItWorks() = PreviewShell {
    HowItWorksScreen()
}

@Preview(name = "09 Privacy", showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun PreviewPrivacy() = PreviewShell {
    PrivacyScreen {}
}

@Preview(name = "10 Examples", showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun PreviewExamples() = PreviewShell {
    ExamplesScreen {}
}

@Preview(name = "11 More", showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun PreviewMore() = PreviewShell {
    MoreScreen({}, {}, {}, {})
}

@Preview(name = "12 About", showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun PreviewAbout() = PreviewShell {
    AboutScreen {}
}

@Preview(name = "13 Error", showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun PreviewError() = PreviewShell {
    ErrorScreen("We couldn't process your request. Please try again.", {}, {})
}

package com.dev.diksha

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import com.dev.diksha.civic.CivicVoiceViewModel
import com.dev.diksha.civic.CivicVoiceScreen
import com.dev.diksha.ui.theme.MyApplicationTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val civicVoiceViewModel: CivicVoiceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        civicVoiceViewModel.initializeModel()
        setContent {
            MyApplicationTheme {
                CivicVoiceScreen(civicVoiceViewModel)
            }
        }
    }
}

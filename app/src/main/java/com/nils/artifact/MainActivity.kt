package com.nils.artifact

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nils.artifact.ui.ArtifactApp
import com.nils.artifact.ui.ArtifactViewModel
import com.nils.artifact.ui.theme.ArtifactTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArtifactTheme {
                val viewModel: ArtifactViewModel = viewModel()
                ArtifactApp(viewModel = viewModel)
            }
        }
    }
}

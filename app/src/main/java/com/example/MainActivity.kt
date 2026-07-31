package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.firebase.FirebaseManager
import com.example.ui.MainViewModel
import com.example.ui.SoundSlumberApp
import com.example.ui.theme.SoundSlumberTheme

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    FirebaseManager.initialize(applicationContext)

    setContent {
      val viewModel: MainViewModel = viewModel()
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()

      SoundSlumberTheme(themeStyle = uiState.themeStyle) {
        SoundSlumberApp(viewModel = viewModel)
      }
    }
  }
}

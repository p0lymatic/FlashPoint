package com.p0lymatic.flashpoint

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.p0lymatic.flashpoint.ui.FlashPointTheme
import com.p0lymatic.flashpoint.ui.FlashPointViewModel
import com.p0lymatic.flashpoint.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FlashPointTheme {
                val viewModel: FlashPointViewModel = viewModel()
                MainScreen(viewModel = viewModel)
            }
        }
    }
}

package com.flex.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.flex.assistant.ui.FlexTheme
import com.flex.assistant.ui.HomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FlexTheme { HomeScreen() } }
    }

    override fun onResume() {
        super.onResume()
        FlexRuntime.resumeTick.value = FlexRuntime.resumeTick.value + 1
    }
}

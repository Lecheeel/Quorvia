package com.quorvia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.quorvia.app.feature.explore.ExploreRoute
import com.quorvia.app.ui.theme.QuorviaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QuorviaTheme {
                ExploreRoute()
            }
        }
    }
}


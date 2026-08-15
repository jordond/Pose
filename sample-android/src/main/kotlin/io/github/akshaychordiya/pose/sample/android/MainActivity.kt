package io.github.akshaychordiya.pose.sample.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.akshaychordiya.pose.sample.LoginContent
import io.github.akshaychordiya.pose.sample.LoginUiState
import io.github.akshaychordiya.pose.sample.SampleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SampleTheme {
                LoginContent(
                    state = LoginUiState(email = "", password = "", isSubmitting = false, error = null),
                    onEmailChange = {},
                    onSubmit = {},
                )
            }
        }
    }
}

package io.github.akshaychordiya.pose.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

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

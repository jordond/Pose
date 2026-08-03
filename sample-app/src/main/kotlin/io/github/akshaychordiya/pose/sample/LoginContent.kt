package io.github.akshaychordiya.pose.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.akshaychordiya.pose.Pose

/**
 * Showcase — the zero-config case.
 */
data class LoginUiState(
    val email: String,
    val password: String,
    val isSubmitting: Boolean,
    val error: String?,
)

@Pose
@Composable
fun LoginContent(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp)) {
        TextField(value = state.email, onValueChange = onEmailChange, label = { Text("Email") })
        if (state.error != null) Text(state.error)
        Button(onClick = onSubmit, enabled = !state.isSubmitting) {
            Text(if (state.isSubmitting) "Submitting…" else "Sign in")
        }
    }
}

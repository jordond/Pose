package io.github.akshaychordiya.pose.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.akshaychordiya.pose.Pose

/**
 * Showcase — sealed fan-out.
 *
 * `HomeState` is a sealed interface with no `companion.previewSamples`, so Pose
 * emits one preview per subtype. In the preview panel you see three renders
 * named `HomeContent__Preview_Loading`, `_Success`, `_Error`.
 */
sealed interface HomeState {
    data object Loading : HomeState
    data class Success(val greeting: String) : HomeState
    data class Error(val message: String) : HomeState
}

@Pose
@Composable
fun HomeContent(state: HomeState, onRetry: () -> Unit) {
    Column(Modifier.padding(16.dp)) {
        when (state) {
            HomeState.Loading -> Text("Loading…")
            is HomeState.Success -> Text(state.greeting)
            is HomeState.Error -> {
                Text(state.message)
                Button(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

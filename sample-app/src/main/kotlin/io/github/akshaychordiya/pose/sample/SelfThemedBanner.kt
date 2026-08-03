package io.github.akshaychordiya.pose.sample

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.akshaychordiya.pose.Pose

/**
 * Showcase — `wrapInTheme = false`.
 *
 * This composable already applies its own MaterialTheme internally (a fixed dark
 * variant), so we tell Pose to skip the outer `SampleTheme { … }` wrapper. The
 * generated preview will call `SelfThemedBanner(...)` directly with no wrapper.
 */
@Pose(wrapInTheme = false)
@Composable
fun SelfThemedBanner(message: String, modifier: Modifier = Modifier) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Text(message, modifier = modifier.padding(12.dp))
    }
}

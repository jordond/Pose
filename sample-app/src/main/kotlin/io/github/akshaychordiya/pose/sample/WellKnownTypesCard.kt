package io.github.akshaychordiya.pose.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.akshaychordiya.pose.Pose
import kotlinx.coroutines.flow.StateFlow

/**
 * Showcase — T1 well-known types + multipreview annotation.
 *
 * Every parameter here is filled by an FQN-table emitter (no structural work):
 *  - `Color` → `Color(0xFFCCCCCC.toInt())`
 *  - `Dp`    → `16.dp`
 *  - `StateFlow<String>` → `MutableStateFlow(<inner>).asStateFlow()`
 *  - `Result<Int>` → `Result.success(<inner>)`
 *
 * `@Pose(previews = [AppPreviews::class])` replaces the default light+dark pair
 * with our project multipreview matrix (4 renders instead of 2).
 */
@Pose(previews = [AppPreviews::class])
@Composable
fun WellKnownTypesCard(
    tint: Color,
    corner: Dp,
    titleStream: StateFlow<String>,
    lastRun: Result<Int>,
) {
    val title by titleStream.collectAsState()
    Column(Modifier.padding(corner)) {
        Box(Modifier.size(48.dp).background(tint))
        Text(title)
        Text(lastRun.fold(onSuccess = { "OK · $it" }, onFailure = { it.message.orEmpty() }))
    }
}

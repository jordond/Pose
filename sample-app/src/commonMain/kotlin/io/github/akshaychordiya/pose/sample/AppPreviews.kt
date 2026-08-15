package io.github.akshaychordiya.pose.sample

import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_NIGHT_NO
import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview

/**
 * Showcase — project-wide multipreview annotation.
 *
 * Bundle every dimension your team cares about (light/dark/font-scale/locale)
 * into one annotation and reference it from `@Pose(previews = [AppPreviews::class])`.
 * Adding a new dimension later is a one-line edit here that ripples through
 * every generated preview.
 *
 * `UI_MODE_NIGHT_*` comes from `AndroidUiModes`, not `android.content.res.Configuration` -
 * same constants, but declared in `commonMain` so this compiles for every target.
 */
@Preview(name = "Light",     uiMode = UI_MODE_NIGHT_NO,  showBackground = true)
@Preview(name = "Dark",      uiMode = UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Font 1.5×", fontScale = 1.5f,           showBackground = true)
@Preview(name = "RTL",       locale  = "ar",             showBackground = true)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
annotation class AppPreviews

package io.github.akshaychordiya.pose.ide

import com.intellij.openapi.util.IconLoader

/**
 * Centralized icon references so `PoseLineMarkerProvider` (and any future
 * inspections/quick-fixes) resolve them against the plugin classloader.
 *
 * IntelliJ auto-picks the `_dark.svg` variant when the IDE is in a dark theme,
 * as long as both files sit next to each other on the classpath.
 */
internal object PoseIcons {
    val Gutter = IconLoader.getIcon("/icons/pose.svg", PoseIcons::class.java)
}

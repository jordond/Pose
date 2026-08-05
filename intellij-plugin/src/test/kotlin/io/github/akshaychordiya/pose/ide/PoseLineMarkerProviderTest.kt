package io.github.akshaychordiya.pose.ide

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * BasePlatformTestCase spins up a light IntelliJ project with our plugin.xml
 * extensions registered. Each test builds a mini file tree under the fixture's
 * temp dir (mirroring a Gradle module layout with `build/generated/ksp/…`) then
 * checks the gutter markers our LineMarkerProvider produces on the source file.
 *
 * We assert on tooltip text — the surest per-provider identifier we control,
 * since `findAllGutters()` returns markers from every registered provider.
 */
class PoseLineMarkerProviderTest : BasePlatformTestCase() {

    fun testNoGutterWhenGeneratedFileAbsent() {
        val src = myFixture.tempDirFixture.createFile(
            "src/main/kotlin/com/example/LoginContent.kt",
            """
            package com.example

            fun LoginContent() { }
            """.trimIndent(),
        )
        myFixture.configureFromExistingVirtualFile(src)
        myFixture.doHighlighting()

        assertTrue(
            "No Pose gutter expected when the generated file is missing",
            posetTooltips().isEmpty(),
        )
    }

    fun testGutterPresentWhenGeneratedFileHasMatchingPreview() {
        myFixture.tempDirFixture.createFile(
            "build/generated/ksp/debug/kotlin/com/example/LoginContent__Preview.kt",
            """
            package com.example

            internal fun LoginContent__Preview() { }
            """.trimIndent(),
        )
        val src = myFixture.tempDirFixture.createFile(
            "src/main/kotlin/com/example/LoginContent.kt",
            """
            package com.example

            fun LoginContent() { }
            """.trimIndent(),
        )
        myFixture.configureFromExistingVirtualFile(src)
        myFixture.doHighlighting()

        val tooltips = posetTooltips()
        assertEquals("Expected exactly one Pose gutter icon", 1, tooltips.size)
        assertTrue(
            "Single-target tooltip should reference the specific preview name; got: ${tooltips.single()}",
            tooltips.single().contains("LoginContent__Preview"),
        )
    }

    fun testGutterPresentForKmpCommonMainLayout() {
        // KMP puts KSP output one level deeper: build/generated/ksp/<target>/<sourceSet>/kotlin/…
        // For commonMain composables this is `metadata/commonMain/kotlin/…`. The plugin's BFS
        // walker should still find them.
        myFixture.tempDirFixture.createFile(
            "build/generated/ksp/metadata/commonMain/kotlin/com/example/CommonScreen__Preview.kt",
            """
            package com.example

            internal fun CommonScreen__Preview() { }
            """.trimIndent(),
        )
        val src = myFixture.tempDirFixture.createFile(
            "src/commonMain/kotlin/com/example/CommonScreen.kt",
            """
            package com.example

            fun CommonScreen() { }
            """.trimIndent(),
        )
        myFixture.configureFromExistingVirtualFile(src)
        myFixture.doHighlighting()

        val tooltips = posetTooltips()
        assertEquals("Expected exactly one Pose gutter icon for the commonMain composable", 1, tooltips.size)
        assertTrue(
            "Tooltip should reference the specific preview; got: ${tooltips.single()}",
            tooltips.single().contains("CommonScreen__Preview"),
        )
    }

    fun testSealedFanOutTooltipReportsMultipleTargets() {
        myFixture.tempDirFixture.createFile(
            "build/generated/ksp/debug/kotlin/com/example/HomeContent__Preview.kt",
            """
            package com.example

            internal fun HomeContent__Preview_Loading() { }
            internal fun HomeContent__Preview_Success() { }
            internal fun HomeContent__Preview_Error() { }
            """.trimIndent(),
        )
        val src = myFixture.tempDirFixture.createFile(
            "src/main/kotlin/com/example/HomeContent.kt",
            """
            package com.example

            fun HomeContent() { }
            """.trimIndent(),
        )
        myFixture.configureFromExistingVirtualFile(src)
        myFixture.doHighlighting()

        val tooltips = posetTooltips()
        assertEquals("Expected exactly one Pose gutter icon", 1, tooltips.size)
        val tooltip = tooltips.single()
        assertTrue(
            "Multi-target tooltip should mention the count; got: $tooltip",
            tooltip.startsWith("3 Pose previews"),
        )
    }

    fun testNoGutterWhenGeneratedFileHasNoMatchingFunction() {
        // Generated file exists but no function matches `Toolbar__Preview*`.
        myFixture.tempDirFixture.createFile(
            "build/generated/ksp/debug/kotlin/com/example/Toolbar__Preview.kt",
            """
            package com.example

            internal fun SomethingElse() { }
            """.trimIndent(),
        )
        val src = myFixture.tempDirFixture.createFile(
            "src/main/kotlin/com/example/Toolbar.kt",
            """
            package com.example

            fun Toolbar() { }
            """.trimIndent(),
        )
        myFixture.configureFromExistingVirtualFile(src)
        myFixture.doHighlighting()

        assertTrue(
            "No Pose gutter expected when the generated file lacks a matching function",
            posetTooltips().isEmpty(),
        )
    }

    /** Filters gutter markers to only Pose's — keyed on tooltip content. */
    private fun posetTooltips(): List<String> =
        myFixture.findAllGutters()
            .mapNotNull { it.tooltipText }
            .filter { "Pose" in it }
}

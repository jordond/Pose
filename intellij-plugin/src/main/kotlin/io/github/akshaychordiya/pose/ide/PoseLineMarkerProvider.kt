package io.github.akshaychordiya.pose.ide

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Attaches a gutter icon next to any `@Composable` function whose Pose-generated
 * preview file already exists on disk. Clicking the icon jumps into that file at
 * the matching `<ComposableName>__Preview*` function (popup chooser for sealed
 * fan-outs).
 *
 * Design: keys off the *generated file*, not `@Pose` — so bulk-mode composables
 * (no annotation) light up the same as explicit ones. Nothing is generated until
 * you build once, so first-time UX is "annotate/enable-bulk → build → icons".
 */
class PoseLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun getName(): String = "Pose preview"

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        // Only anchor on the identifier leaf (single token) — attaching to the parent
        // function would draw the icon over the whole declaration.
        val identifier = element as? com.intellij.psi.impl.source.tree.LeafPsiElement ?: return
        val function = identifier.parent as? KtNamedFunction ?: return
        if (function.nameIdentifier !== identifier) return

        val previews = GeneratedPreviewLocator.locate(function)
        if (previews.isEmpty()) return

        val builder = NavigationGutterIconBuilder.create(PoseIcons.Gutter)
            .setTargets(previews.map { it.psi })
            .setTooltipText(tooltipFor(previews))
            .setPopupTitle("Pose previews for ${function.name}")
            .setAlignment(GutterIconRenderer.Alignment.LEFT)

        result.add(builder.createLineMarkerInfo(identifier))
    }

    private fun tooltipFor(previews: List<GeneratedPreview>): String = when (previews.size) {
        1 -> "Open Pose preview: ${previews.single().simpleName}"
        else -> "${previews.size} Pose previews — click to choose"
    }
}

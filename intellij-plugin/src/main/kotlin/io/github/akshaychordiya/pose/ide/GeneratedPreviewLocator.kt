package io.github.akshaychordiya.pose.ide

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Locates the Pose-generated `<Source>__Preview.kt` file for a source composable
 * and enumerates the `internal fun <ComposableName>__Preview*` candidates inside.
 *
 * Mapping rules (mirror the processor's emitter):
 *  - Source `com/foo/Bar.kt` → generated `com/foo/Bar__Preview.kt`
 *  - Generated files live under `<module>/build/generated/ksp/&#42;/kotlin/`
 *  - One source file may yield 1..N preview functions (sealed fan-out)
 *
 * Cheap because it's driven by a file-existence check; expensive PSI parsing
 * only kicks in when the file exists.
 */
internal object GeneratedPreviewLocator {

    private val logger = Logger.getInstance(GeneratedPreviewLocator::class.java)

    fun locate(function: KtNamedFunction): List<GeneratedPreview> {
        val composableName = function.name ?: return emptyList()
        val sourceFile = function.containingKtFile
        val sourceVFile = sourceFile.virtualFile ?: return emptyList()

        val moduleRoot = findGradleModuleRoot(sourceVFile) ?: run {
            if (logger.isDebugEnabled) {
                logger.debug("[Pose] no ancestor with build/generated/ksp for ${sourceVFile.path}")
            }
            return emptyList()
        }
        val kspRoot = moduleRoot.findFileByRelativePath("build/generated/ksp")
            ?: return emptyList()

        // Force a light VFS refresh once — freshly-generated files may not be in the cache yet.
        if (kspRoot.children.isEmpty()) kspRoot.refresh(false, true)

        // Each child of kspRoot is a KSP variant dir (debug/release/…). Pick the first that
        // contains a matching generated file.
        val packageDir = sourceFile.packageFqName.asString().replace('.', '/')
        val generatedRelPath = "kotlin/$packageDir/${sourceVFile.nameWithoutExtension}__Preview.kt"

        val generatedVFile = kspRoot.children
            .asSequence()
            .filter { it.isDirectory }
            .mapNotNull { it.findFileByRelativePath(generatedRelPath) }
            .firstOrNull()
            ?: run {
                if (logger.isDebugEnabled) {
                    logger.debug("[Pose] no generated file at $generatedRelPath under ${kspRoot.path}")
                }
                return emptyList()
            }

        val generatedKt = PsiManager.getInstance(function.project).findFile(generatedVFile) as? KtFile
            ?: return emptyList()

        val prefix = "${composableName}__Preview"
        return generatedKt.declarations
            .filterIsInstance<KtNamedFunction>()
            .filter { fn ->
                val name = fn.name ?: return@filter false
                name == prefix || name.startsWith("${prefix}_")
            }
            .map { fn ->
                GeneratedPreview(
                    psi = fn,
                    simpleName = fn.name.orEmpty(),
                    file = generatedVFile,
                )
            }
    }

    /**
     * Walks up from the source file until it finds an ancestor with `build/generated/ksp`.
     * More robust than IntelliJ module content roots — those point at `src/main` in a
     * Gradle/AS module, not the module directory itself.
     */
    private fun findGradleModuleRoot(sourceFile: VirtualFile): VirtualFile? {
        var current: VirtualFile? = sourceFile.parent
        while (current != null) {
            if (current.findFileByRelativePath("build/generated/ksp") != null) return current
            current = current.parent
        }
        return null
    }
}

/** A single generated `<Composable>__Preview*` function that a source composable maps to. */
internal data class GeneratedPreview(
    val psi: KtNamedFunction,
    val simpleName: String,
    val file: VirtualFile,
)

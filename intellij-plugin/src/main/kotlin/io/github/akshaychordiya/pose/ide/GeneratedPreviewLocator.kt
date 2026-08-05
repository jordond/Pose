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

        val packageDir = sourceFile.packageFqName.asString().replace('.', '/')
        val generatedRelPath = "kotlin/$packageDir/${sourceVFile.nameWithoutExtension}__Preview.kt"

        // KSP output layouts we need to cover:
        //   Android         →  build/generated/ksp/<variant>/kotlin/…             (1 level below kspRoot)
        //   KMP commonMain  →  build/generated/ksp/metadata/commonMain/kotlin/…   (2 levels)
        //   KMP <target>    →  build/generated/ksp/<target>/<sourceSet>/kotlin/…  (2 levels)
        // Bounded BFS keeps this cheap and works for any future layout up to MAX_DEPTH.
        val generatedVFile = findGeneratedFile(kspRoot, generatedRelPath)
            ?: run {
                if (logger.isDebugEnabled) {
                    logger.debug("[Pose] no generated file at kspRoot/*/$generatedRelPath under ${kspRoot.path}")
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

    /**
     * Bounded BFS under [kspRoot] looking for the first descendant that contains
     * [relPath]. Covers both single-nesting (Android's `<variant>/kotlin/…`) and
     * double-nesting (KMP's `<target>/<sourceSet>/kotlin/…`).
     *
     * Depth cap is deliberate — [kspRoot] can hold dozens of variant dirs and we
     * don't want a runaway walk if the file isn't there.
     */
    private fun findGeneratedFile(kspRoot: VirtualFile, relPath: String): VirtualFile? {
        val queue = ArrayDeque<Pair<VirtualFile, Int>>().apply { addLast(kspRoot to 0) }
        while (queue.isNotEmpty()) {
            val (dir, depth) = queue.removeFirst()
            dir.findFileByRelativePath(relPath)?.let { return it }
            if (depth >= MAX_KSP_NESTING) continue
            dir.children.forEach { child ->
                if (child.isDirectory) queue.addLast(child to depth + 1)
            }
        }
        return null
    }

    private const val MAX_KSP_NESTING = 3
}

/** A single generated `<Composable>__Preview*` function that a source composable maps to. */
internal data class GeneratedPreview(
    val psi: KtNamedFunction,
    val simpleName: String,
    val file: VirtualFile,
)

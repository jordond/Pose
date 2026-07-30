package io.github.akshaychordiya.pose.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo

/**
 * Renders [PreviewPlan]s to Kotlin source and writes one file per KSFile via KSP's
 * code generator. Each generated file contains every preview function + provider
 * class for composables declared in the source file, matching the naming scheme
 * `<SourceFileBase>__Preview.kt`.
 */
public class PreviewFileEmitter(
    private val codeGenerator: CodeGenerator,
    private val options: Options,
) {

    /**
     * Emit one generated file for every source file that owns at least one plan.
     * Plans are grouped by [KSFunctionDeclaration.containingFile]; source files
     * with no plans are skipped.
     */
    public fun emitAll(plans: List<PreviewPlan>) {
        plans.groupBy { it.function.containingFile }
            .filterKeys { it != null }
            .forEach { (containingFile, filePlans) ->
                emitForFile(containingFile!!, filePlans)
            }
    }

    private fun emitForFile(containingFile: KSFile, plans: List<PreviewPlan>) {
        val pkg = plans.first().function.packageName.asString()
        val sourceBase = containingFile.fileName.substringBeforeLast('.')
        val fileName = sourceBase + "__Preview"

        val fileBuilder = FileSpec.builder(pkg, fileName)
            .addFileComment(fileHeader(plans))
            .addAnnotation(
                AnnotationSpec.builder(ClassName("kotlin", "Suppress"))
                    .addMember("%S, %S, %S", "unused", "RedundantVisibilityModifier", "UnusedImport")
                    .useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
                    .build()
            )

        for (plan in plans) {
            fileBuilder.addFunction(previewFun(plan, pkg))
            plan.providerSlot?.let { fileBuilder.addType(providerType(it)) }
        }

        fileBuilder.build().writeTo(
            codeGenerator = codeGenerator,
            dependencies = Dependencies(aggregating = false, containingFile),
        )
    }

    private fun previewFun(plan: PreviewPlan, pkg: String): FunSpec {
        val baseName = plan.annotation.name.ifEmpty { plan.function.simpleName.asString() + "__Preview" } +
            plan.nameSuffix
        val builder = FunSpec.builder(baseName)
            .addModifiers(KModifier.INTERNAL)
            .addAnnotation(ClassName("androidx.compose.runtime", "Composable"))

        plan.annotation.previewAnnotationFqns.forEach { fqn ->
            builder.addAnnotation(fqnToClassName(fqn))
        }

        plan.providerSlot?.let { slot ->
            val providerFqn = ClassName(pkg, slot.providerSimpleName)
            val previewParamAnn = AnnotationSpec.builder(PreviewParameterAnn)
                .addMember("%T::class", providerFqn)
                .build()
            builder.addParameter(
                ParameterSpec.builder(slot.paramName, slot.paramType)
                    .addAnnotation(previewParamAnn)
                    .build()
            )
        }

        builder.addCode(bodyFor(plan))
        return builder.build()
    }

    private fun providerType(slot: ProviderSlot): TypeSpec {
        val providerBase = PreviewParameterProvider.parameterizedBy(slot.paramType)
        val sequenceOf = SequenceClassName.parameterizedBy(slot.paramType)

        val valuesProperty = PropertySpec.builder("values", sequenceOf)
            .addModifiers(KModifier.OVERRIDE)
            .initializer(slot.valuesExpr)
            .build()

        return TypeSpec.classBuilder(slot.providerSimpleName)
            .addModifiers(KModifier.INTERNAL)
            .addSuperinterface(providerBase)
            .addProperty(valuesProperty)
            .build()
    }

    private fun bodyFor(plan: PreviewPlan): CodeBlock {
        val target = plan.function
        val call = CodeBlock.builder()
            .add("%M(", MemberName(target.packageName.asString(), target.simpleName.asString()))

        val callArgs = buildList {
            plan.providerSlot?.let { add(it.paramName to CodeBlock.of("%L", it.paramName)) }
            plan.otherArgs.forEach { add(it.paramName to it.expr) }
        }
        if (callArgs.isEmpty()) {
            call.add(")\n")
        } else {
            call.add("\n  ⇥")
            callArgs.forEachIndexed { i, (name, expr) ->
                if (i > 0) call.add(",\n")
                call.add("%L = %L", name, expr)
            }
            call.add(",\n⇤)\n")
        }
        val inner = call.build()

        return if (plan.annotation.wrapInTheme && options.themeFqName != null) {
            CodeBlock.builder()
                .add("%M {\n  ⇥", memberFromFqn(options.themeFqName))
                .add(inner)
                .add("⇤}\n")
                .build()
        } else {
            inner
        }
    }

    private fun fileHeader(plans: List<PreviewPlan>): String = buildString {
        appendLine("GENERATED by io.github.akshaychordiya.pose — DO NOT EDIT")
        val firstFile = plans.first().function.containingFile?.fileName
        if (firstFile != null) appendLine("Source: $firstFile")
        for ((i, plan) in plans.withIndex()) {
            if (i > 0) appendLine()
            val fn = plan.function
            val line = (fn.location as? FileLocation)?.lineNumber ?: -1
            appendLine("Target: ${fn.qualifiedName?.asString()}${if (line > 0) " (line $line)" else ""}")
            appendLine("  Resolution:")
            plan.resolutionNotes.forEach { note ->
                appendLine("    ${note.paramName} -> ${note.tier} (${note.note})")
            }
        }
    }

    private fun memberFromFqn(fqn: String): MemberName {
        val idx = fqn.lastIndexOf('.')
        return MemberName(fqn.substring(0, idx), fqn.substring(idx + 1))
    }

    private fun fqnToClassName(fqn: String): ClassName {
        val idx = fqn.lastIndexOf('.')
        return ClassName(fqn.substring(0, idx), fqn.substring(idx + 1))
    }

    private companion object {
        val PreviewParameterAnn = ClassName("androidx.compose.ui.tooling.preview", "PreviewParameter")
        val PreviewParameterProvider = ClassName("androidx.compose.ui.tooling.preview", "PreviewParameterProvider")
        val SequenceClassName = ClassName("kotlin.sequences", "Sequence")
    }
}

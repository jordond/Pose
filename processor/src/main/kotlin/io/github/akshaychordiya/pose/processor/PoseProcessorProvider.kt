package io.github.akshaychordiya.pose.processor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/** SPI entry point loaded by KSP via `META-INF/services`. */
public class PoseProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        PoseProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            options = Options.from(environment.options),
            rawOptions = environment.options,
        )
}

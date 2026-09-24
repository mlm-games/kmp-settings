package io.github.mlmgames.settings.ksp

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class SettingsProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val processor = SettingsProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
        )
        environment.registerProcessorForNewFeatures(processor)
        return processor
    }
}
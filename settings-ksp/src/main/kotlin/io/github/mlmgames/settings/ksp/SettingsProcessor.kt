package io.github.mlmgames.settings.ksp

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSValueArgument
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Variance
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.ksp.TypeParameterResolver
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName as ksToTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import kotlin.math.abs

class SettingsProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    private companion object {
        const val SETTING_ANNOTATION = "io.github.mlmgames.settings.core.annotations.Setting"
        const val PERSISTED_ANNOTATION = "io.github.mlmgames.settings.core.annotations.Persisted"
        const val SERIALIZED_ANNOTATION = "io.github.mlmgames.settings.core.annotations.Serialized"
        const val CATEGORY_DEF_ANNOTATION = "io.github.mlmgames.settings.core.annotations.CategoryDefinition"
        const val ACTION_HANDLER_ANNOTATION = "io.github.mlmgames.settings.core.annotations.ActionHandler"
        const val RANGE_ANNOTATION = "io.github.mlmgames.settings.core.annotations.Range"
        const val LENGTH_ANNOTATION = "io.github.mlmgames.settings.core.annotations.Length"
        const val PATTERN_ANNOTATION = "io.github.mlmgames.settings.core.annotations.Pattern"
        const val REQUIRED_ANNOTATION = "io.github.mlmgames.settings.core.annotations.Required"
        const val REQUIRES_CONFIRMATION_ANNOTATION = "io.github.mlmgames.settings.core.annotations.RequiresConfirmation"
        const val NO_RESET_ANNOTATION = "io.github.mlmgames.settings.core.annotations.NoReset"
        const val CONFIRM_RESET_ANNOTATION = "io.github.mlmgames.settings.core.annotations.ConfirmReset"
        const val SERIALIZED_WITH_ANNOTATION = "io.github.mlmgames.settings.core.annotations.SerializedWith"
        const val VALIDATED_BY_ANNOTATION = "io.github.mlmgames.settings.core.annotations.ValidatedBy"
        const val SCHEMA_VERSION_ANNOTATION = "io.github.mlmgames.settings.core.annotations.SchemaVersion"
        const val RENAMED_FROM_ANNOTATION = "io.github.mlmgames.settings.core.annotations.RenamedFrom"
        const val ADDED_IN_VERSION_ANNOTATION = "io.github.mlmgames.settings.core.annotations.AddedInVersion"
        const val DEPRECATED_SETTING_ANNOTATION = "io.github.mlmgames.settings.core.annotations.DeprecatedSetting"
        const val KOTLINX_SERIALIZABLE = "kotlinx.serialization.Serializable"

        val PROPERTY_ANNOTATIONS = listOf(
            SERIALIZED_ANNOTATION,
            SERIALIZED_WITH_ANNOTATION,
            RANGE_ANNOTATION,
            LENGTH_ANNOTATION,
            PATTERN_ANNOTATION,
            REQUIRED_ANNOTATION,
            VALIDATED_BY_ANNOTATION,
            ACTION_HANDLER_ANNOTATION,
            REQUIRES_CONFIRMATION_ANNOTATION,
            NO_RESET_ANNOTATION,
            CONFIRM_RESET_ANNOTATION,
            RENAMED_FROM_ANNOTATION,
            ADDED_IN_VERSION_ANNOTATION,
            DEPRECATED_SETTING_ANNOTATION,
        )

        val RESERVED_KEYS = setOf(
            "__schema_version__",
            "__settings_lock_enabled__",
            "__settings_pin_hash__",
            "__settings_lock_timeout__",
            "__settings_last_unlock__",
        )
        const val UNKNOWN_BACKUP_PREFIX = "__unknown_backup__:"
    }

    private val corePackage = "io.github.mlmgames.settings.core"
    private val fieldsPackage = "$corePackage.fields"
    private val typesPackage = "$corePackage.types"
    private val annotationsPackage = "$corePackage.annotations"

    private val settingsSchema = ClassName(corePackage, "SettingsSchema")
    private val settingField = ClassName(corePackage, "SettingField")
    private val schemaFieldMetadataClass = ClassName(corePackage, "SchemaFieldMetadata")
    private val validationRules = ClassName(corePackage, "ValidationRules")
    private val confirmationConfig = ClassName(corePackage, "ConfirmationConfig")
    private val valueKindClass = ClassName(corePackage, "ValueKind")
    private val settingPlatformClass = ClassName(annotationsPackage, "SettingPlatform")
    private val kSerializerClass = ClassName("kotlinx.serialization", "KSerializer")
    private val serialDescriptorClass = ClassName("kotlinx.serialization.descriptors", "SerialDescriptor")
    private val primitiveKindClass = ClassName("kotlinx.serialization.descriptors", "PrimitiveKind")
    private val encoderClass = ClassName("kotlinx.serialization.encoding", "Encoder")
    private val decoderClass = ClassName("kotlinx.serialization.encoding", "Decoder")
    private val primitiveSerialDescriptor = MemberName(
        "kotlinx.serialization.descriptors",
        "PrimitiveSerialDescriptor",
    )
    private val serializerFunction = MemberName("kotlinx.serialization", "serializer")

    private val booleanField = ClassName(fieldsPackage, "BooleanField")
    private val intField = ClassName(fieldsPackage, "IntField")
    private val longField = ClassName(fieldsPackage, "LongField")
    private val floatField = ClassName(fieldsPackage, "FloatField")
    private val doubleField = ClassName(fieldsPackage, "DoubleField")
    private val stringField = ClassName(fieldsPackage, "StringField")
    private val stringSetField = ClassName(fieldsPackage, "StringSetField")
    private val unitField = ClassName(fieldsPackage, "UnitField")

    private val nullableBooleanField = ClassName(fieldsPackage, "NullableBooleanField")
    private val nullableIntField = ClassName(fieldsPackage, "NullableIntField")
    private val nullableLongField = ClassName(fieldsPackage, "NullableLongField")
    private val nullableFloatField = ClassName(fieldsPackage, "NullableFloatField")
    private val nullableDoubleField = ClassName(fieldsPackage, "NullableDoubleField")
    private val nullableStringField = ClassName(fieldsPackage, "NullableStringField")

    private val stringListField = ClassName(fieldsPackage, "StringListField")
    private val intListField = ClassName(fieldsPackage, "IntListField")
    private val longListField = ClassName(fieldsPackage, "LongListField")
    private val stringMapField = ClassName(fieldsPackage, "StringMapField")
    private val stringLongMapField = ClassName(fieldsPackage, "StringLongMapField")
    private val stringIntMapField = ClassName(fieldsPackage, "StringIntMapField")
    private val stringFloatMapField = ClassName(fieldsPackage, "StringFloatMapField")
    private val stringDoubleMapField = ClassName(fieldsPackage, "StringDoubleMapField")
    private val stringBooleanMapField = ClassName(fieldsPackage, "StringBooleanMapField")
    private val configuredSettingField = ClassName(fieldsPackage, "ConfiguredSettingField")
    private val intStringMapField = ClassName(fieldsPackage, "IntStringMapField")
    private val intIntMapField = ClassName(fieldsPackage, "IntIntMapField")
    private val intLongMapField = ClassName(fieldsPackage, "IntLongMapField")
    private val longStringMapField = ClassName(fieldsPackage, "LongStringMapField")
    private val longLongMapField = ClassName(fieldsPackage, "LongLongMapField")
    private val longIntMapField = ClassName(fieldsPackage, "LongIntMapField")

    private val serializedField = ClassName(fieldsPackage, "SerializedField")
    private val nullableSerializedField = ClassName(fieldsPackage, "NullableSerializedField")
    private val enumField = ClassName(fieldsPackage, "EnumField")
    private val nullableEnumField = ClassName(fieldsPackage, "NullableEnumField")

    private val generatedClasses = mutableSetOf<String>()
    private val blockedClasses = mutableSetOf<String>()
    private val schemaNames = mutableMapOf<String, String>()
    private val usedSchemaNames = mutableMapOf<String, String>()

    private enum class AnnotationKind { SETTING, PERSISTED }

    private enum class UiKind {
        TOGGLE,
        DROPDOWN,
        SLIDER,
        BUTTON,
        TEXT_INPUT,
        TIME_PICKER,
        CUSTOM,
    }

    private enum class FieldKind {
        PRIMITIVE,
        COLLECTION,
        ENUM,
        SERIALIZED,
        UNIT,
    }

    private data class FieldPlan(
        val property: KSPropertyDeclaration,
        val propertyName: String,
        val propertyType: KSType,
        val baseType: KSType,
        val nullable: Boolean,
        val kind: FieldKind,
        val fieldClass: ClassName,
        val logicalKey: String,
        val physicalKeys: List<String>,
        val valueKind: String,
        val enumTypeName: String?,
        val serializer: CustomSerializerSpec?,
        val validator: CustomValidatorSpec?,
    )

    private data class CustomValidatorSpec(
        val className: ClassName,
        val declaration: KSClassDeclaration,
        val instance: CodeBlock,
    )

    private data class CustomSerializerSpec(
        val className: ClassName,
        val declaration: KSClassDeclaration,
        val instance: CodeBlock,
    )

    private data class ValidationMessage(val text: String, val resource: Int, val key: String)

    private data class SettingConfig(
        val title: String,
        val description: String,
        val titleRes: Int,
        val descriptionRes: Int,
        val titleKey: String,
        val descriptionKey: String,
        val categoryClass: ClassName,
        val categoryOrder: Int,
        val categoryTitleRes: Int,
        val categoryTitleKey: String,
        val typeClass: ClassName,
        val uiKind: UiKind,
        val key: String,
        val dependsOn: String,
        val min: Float,
        val max: Float,
        val step: Float,
        val options: List<String>,
        val optionsRes: Int,
        val optionsKey: String,
        val actionClass: ClassName?,
        val platforms: List<String>,
        val validationMessage: ValidationMessage?,
    )

    private data class FieldMetadata(
        val renamedFrom: String?,
        val renamedSinceVersion: Int?,
        val addedInVersion: Int?,
        val deprecated: Boolean,
        val deprecationMessage: String?,
        val removeInVersion: Int?,
    )

    private data class ClassAnalysis(
        val plans: List<FieldPlan>,
        val settingConfigs: Map<String, SettingConfig>,
        val metadata: Map<String, FieldMetadata>,
        val schemaVersion: Int,
        val categoryTitleResources: Map<ClassName, Int>,
        val categoryTitleKeys: Map<ClassName, String>,
    )

    private class Diagnostics(private val logger: KSPLogger) {
        var hasErrors: Boolean = false
            private set

        fun error(message: String, symbol: KSNode? = null) {
            hasErrors = true
            if (symbol == null) logger.error(message) else logger.error(message, symbol)
        }

        fun warn(message: String, symbol: KSNode? = null) {
            if (symbol == null) logger.warn(message) else logger.warn(message, symbol)
        }
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val allProperties = collectAnnotatedProperties(resolver)
        val propertyKeys = allProperties.mapTo(mutableSetOf()) { propertyKey(it) }
        validateStandaloneAnnotations(resolver, propertyKeys)

        val deferred = mutableListOf<KSAnnotated>()
        val topLevel = allProperties.filter { it.parentDeclaration !is KSClassDeclaration }
        for (property in topLevel) {
            logger.error(
                "@Setting/@Persisted must be a primary-constructor property of a data class; '${property.simpleName.asString()}' is not inside a data class",
                property,
            )
        }

        val grouped = allProperties
            .filter { it.parentDeclaration is KSClassDeclaration }
            .groupBy { it.parentDeclaration as KSClassDeclaration }

        for ((klass, properties) in grouped) {
            val classId = classId(klass)
            if (classId in generatedClasses || classId in blockedClasses) continue

            if (!classReady(klass) || properties.any { !propertyReady(it) }) {
                deferred.addAll(properties)
                continue
            }

            val analysis = analyzeClass(klass, properties, resolver)
            if (analysis == null) {
                blockedClasses += classId
                continue
            }

            val schemaName = schemaNameFor(klass)
            if (schemaName == null) {
                logger.error("Cannot choose a unique schema name for ${canonicalName(klass)}", klass)
                blockedClasses += classId
                continue
            }
            if (schemaNameConflictsWithSource(resolver, klass, schemaName)) {
                logger.error(
                    "Generated schema '$schemaName' collides with an existing declaration in ${klass.packageName.asString()}",
                    klass,
                )
                blockedClasses += classId
                continue
            }

            try {
                generateSchema(klass, properties, analysis, schemaName, resolver)
                generatedClasses += classId
            } catch (error: Exception) {
                logger.error("Failed to generate schema for ${canonicalName(klass)}: ${error.message}", klass)
                blockedClasses += classId
            }
        }

        return deferred.distinctBy { propertyKey(it as KSPropertyDeclaration) }
    }

    private fun collectAnnotatedProperties(resolver: Resolver): List<KSPropertyDeclaration> {
        val result = linkedMapOf<String, KSPropertyDeclaration>()
        for (annotation in listOf(SETTING_ANNOTATION, PERSISTED_ANNOTATION)) {
            val symbols = runCatching {
                resolver.getSymbolsWithAnnotation(annotation).filterIsInstance<KSPropertyDeclaration>()
            }.getOrDefault(emptySequence())
            for (property in symbols) result.putIfAbsent(propertyKey(property), property)
        }
        return result.values.toList()
    }

    private fun validateStandaloneAnnotations(resolver: Resolver, propertyKeys: Set<String>) {
        for (annotation in PROPERTY_ANNOTATIONS) {
            val symbols = runCatching {
                resolver.getSymbolsWithAnnotation(annotation).filterIsInstance<KSPropertyDeclaration>()
            }.getOrDefault(emptySequence())
            for (property in symbols) {
                if (propertyKey(property) !in propertyKeys) {
                    logger.error(
                        "@${annotation.substringAfterLast('.')} requires a property annotated with @Setting or @Persisted",
                        property,
                    )
                }
            }
        }

        val classesWithSchemaVersion = runCatching {
            resolver.getSymbolsWithAnnotation(SCHEMA_VERSION_ANNOTATION)
                .filterIsInstance<KSClassDeclaration>()
                .toList()
        }.getOrDefault(emptyList())
        val processedClasses = runCatching {
            resolver.getSymbolsWithAnnotation(SETTING_ANNOTATION)
                .filterIsInstance<KSPropertyDeclaration>()
                .mapNotNull { it.parentDeclaration as? KSClassDeclaration }
                .toSet() +
                resolver.getSymbolsWithAnnotation(PERSISTED_ANNOTATION)
                    .filterIsInstance<KSPropertyDeclaration>()
                    .mapNotNull { it.parentDeclaration as? KSClassDeclaration }
                    .toSet()
        }.getOrDefault(emptySet())
        for (klass in classesWithSchemaVersion) {
            if (klass !in processedClasses) {
                logger.error(
                    "@SchemaVersion is only supported on a class containing @Setting/@Persisted properties",
                    klass,
                )
            }
        }
    }

    private fun classReady(klass: KSClassDeclaration): Boolean {
        if (!runCatching { klass.validate(enableNewFeatures = true) }.getOrDefault(false)) return false
        if (klass.primaryConstructor?.parameters.orEmpty().any { runCatching { it.type.resolve() }.getOrNull()?.isError == true }) {
            return false
        }
        return true
    }

    private fun propertyReady(property: KSPropertyDeclaration): Boolean {
        if (!runCatching { property.validate(enableNewFeatures = true) }.getOrDefault(false)) return false
        val type = runCatching { property.type.resolve() }.getOrNull() ?: return false
        if (type.isError || containsErrorType(type)) return false
        return property.annotations.all { annotationReady(it) }
    }

    private fun annotationReady(annotation: KSAnnotation): Boolean {
        if (!runCatching { annotation.annotationType.resolve().declaration.validate(enableNewFeatures = true) }.getOrDefault(false)) {
            return false
        }
        return annotation.arguments.all { argument ->
            when (val value = argument.value) {
                is KSType -> !containsErrorType(value)
                is List<*> -> value.all { it !is KSType || !containsErrorType(it) }
                is Array<*> -> value.all { it !is KSType || !containsErrorType(it) }
                else -> true
            }
        }
    }

    private fun containsErrorType(type: KSType): Boolean {
        if (type.isError) return true
        return type.arguments.any { argument ->
            val argumentType = argument.type?.resolve() ?: return@any true
            containsErrorType(argumentType)
        }
    }

    private fun analyzeClass(
        klass: KSClassDeclaration,
        properties: List<KSPropertyDeclaration>,
        resolver: Resolver,
    ): ClassAnalysis? {
        val diagnostics = Diagnostics(logger)
        if (klass.packageName.asString().isBlank()) {
            diagnostics.error("Settings models must be top-level or named nested classes", klass)
        }
        if (Modifier.DATA !in klass.modifiers) {
            diagnostics.error("@Setting/@Persisted can only be used in a data class", klass)
        }
        if (Modifier.INNER in klass.modifiers) {
            diagnostics.error("Inner settings models are not supported because the schema must construct a no-arg instance", klass)
        }
        if (!isAccessible(klass)) {
            diagnostics.error("Settings model ${canonicalName(klass)} must be public or internal", klass)
        }
        if (hasInaccessibleParent(klass)) {
            diagnostics.error("Settings model ${canonicalName(klass)} is enclosed by a private or protected declaration", klass)
        }
        if (klass.typeParameters.isNotEmpty() || hasGenericParent(klass)) {
            diagnostics.error(
                "Generic settings models are not supported by the object-based schema API (${canonicalName(klass)})",
                klass,
            )
        }

        val constructor = klass.primaryConstructor
        if (constructor == null) {
            diagnostics.error("Settings model ${canonicalName(klass)} must have a primary constructor", klass)
        } else {
            if (Modifier.PRIVATE in constructor.modifiers || Modifier.PROTECTED in constructor.modifiers) {
                diagnostics.error("Settings model constructor must be accessible from generated code", klass)
            }
            val missingDefaults = constructor.parameters
                .filter { !it.hasDefault }
                .mapNotNull { it.name?.asString() }
            if (missingDefaults.isNotEmpty()) {
                diagnostics.error(
                    "${klass.simpleName.asString()} must provide defaults for all constructor parameters (missing: ${missingDefaults.joinToString()})",
                    klass,
                )
            }
        }

        val plans = mutableListOf<FieldPlan>()
        val settingConfigs = mutableMapOf<String, SettingConfig>()
        val metadata = mutableMapOf<String, FieldMetadata>()

        for (property in properties) {
            val propertyName = property.simpleName.asString()
            val hasSetting = property.hasAnnotation(SETTING_ANNOTATION)
            val hasPersisted = property.hasAnnotation(PERSISTED_ANNOTATION)
            if (hasSetting && hasPersisted) {
                diagnostics.error("@Setting and @Persisted are mutually exclusive on '$propertyName'", property)
            }
            if (!isPrimaryConstructorProperty(property)) {
                diagnostics.error(
                    "'$propertyName' must be a primary-constructor property; body properties cannot be represented by copy()",
                    property,
                )
            }
            if (!isAccessibleProperty(property)) {
                diagnostics.error("'$propertyName' must be public or internal for generated access", property)
            }
            if (property.isDelegated()) {
                diagnostics.error("Delegated property '$propertyName' is not supported", property)
            }

            val fieldPlan = planField(property, if (hasSetting) AnnotationKind.SETTING else AnnotationKind.PERSISTED, resolver, diagnostics)
            if (fieldPlan != null) {
                plans += fieldPlan
            }

            if (hasSetting) {
                val config = parseSettingConfig(property, resolver, diagnostics)
                if (config != null && fieldPlan != null) {
                    settingConfigs[propertyName] = config
                    validateSetting(property, fieldPlan, config, resolver, diagnostics)
                }
            } else if (hasPersisted) {
                validatePersisted(property, diagnostics)
                validateResetAndConfirmation(property, diagnostics)
            }

            metadata[propertyName] = parseMetadata(property, fieldPlan, diagnostics)
        }

        validateDependencies(properties, diagnostics)
        val schemaVersion = parseSchemaVersion(klass, diagnostics)
        validateRenames(
            metadata,
            plans,
            schemaVersion,
            klass.getAnnotation(SCHEMA_VERSION_ANNOTATION) != null,
            diagnostics,
        )
        validateKeys(plans, diagnostics)
        validateGeneratedTypeReferences(plans, diagnostics)
        if (diagnostics.hasErrors) return null
        return ClassAnalysis(
            plans = plans,
            settingConfigs = settingConfigs,
            metadata = metadata,
            schemaVersion = schemaVersion,
            categoryTitleResources = settingConfigs.values
                .associate { it.categoryClass to it.categoryTitleRes },
            categoryTitleKeys = settingConfigs.values
                .associate { it.categoryClass to it.categoryTitleKey }
                .filterValues { it.isNotBlank() },
        )
    }

    private fun planField(
        property: KSPropertyDeclaration,
        kind: AnnotationKind,
        resolver: Resolver,
        diagnostics: Diagnostics,
    ): FieldPlan? {
        val propertyName = property.simpleName.asString()
        val propertyType = runCatching { property.type.resolve() }.getOrNull()
        if (propertyType == null || propertyType.isError) {
            diagnostics.error("Cannot resolve type of '$propertyName'", property)
            return null
        }

        val baseType = resolveType(propertyType.makeNotNullable(), diagnostics, property)
        if (baseType == null || baseType.isError) {
            diagnostics.error("Cannot resolve the non-null backing type of '$propertyName'", property)
            return null
        }
        if (baseType.declaration is KSTypeParameter) {
            diagnostics.error("Type-parameter properties are not supported ('$propertyName')", property)
            return null
        }
        if (hasInvalidTypeArguments(baseType)) {
            diagnostics.error("Star or invalid type projections are not supported ('$propertyName')", property)
            return null
        }

        val nullable = propertyType.isMarkedNullable
        val baseName = baseType.declaration.qualifiedName?.asString()
        val hasSerialized = property.hasAnnotation(SERIALIZED_ANNOTATION)
        val hasCustomSerializer = property.hasAnnotation(SERIALIZED_WITH_ANNOTATION)
        val customSerializer = if (hasCustomSerializer) {
            if (!hasSerialized) {
                diagnostics.error("@SerializedWith requires @Serialized on '$propertyName'", property)
            }
            parseCustomSerializer(property, resolver, diagnostics)
        } else {
            null
        }

        val validator = if (kind == AnnotationKind.SETTING) {
            parseCustomValidator(property, resolver, diagnostics)
        } else {
            null
        }

        if (baseName == "kotlin.Unit") {
            if (kind == AnnotationKind.PERSISTED) {
                diagnostics.error("@Persisted cannot back a Unit property ('$propertyName')", property)
            }
            if (hasSerialized || hasCustomSerializer) {
                diagnostics.error("Unit property '$propertyName' cannot use serialized storage", property)
            }
            if (nullable) {
                diagnostics.error("Nullable Unit is not supported ('$propertyName')", property)
            }
            val key = keyFor(property, kind)
            return FieldPlan(
                property = property,
                propertyName = propertyName,
                propertyType = propertyType,
                baseType = baseType,
                nullable = false,
                kind = FieldKind.UNIT,
                fieldClass = unitField,
                logicalKey = key,
                physicalKeys = emptyList(),
                valueKind = "NONE",
                enumTypeName = null,
                serializer = null,
                validator = validator,
            )
        }

        val serializable = isSerializableType(baseType, resolver, mutableSetOf())
        val fieldKind: FieldKind
        val fieldClass: ClassName?
        val serializerSpec = customSerializer

        if (hasCustomSerializer) {
            if (customSerializer == null) return null
            fieldKind = FieldKind.SERIALIZED
            fieldClass = if (nullable) nullableSerializedField else serializedField
        } else {
            when {
                isCollectionType(baseName) -> {
                    if (nullable && !hasSerialized) {
                        diagnostics.error(
                            "Nullable collections require @Serialized or @SerializedWith ('$propertyName')",
                            property,
                        )
                        return null
                    }
                    if (hasSerialized) {
                        if (!serializable) {
                            diagnostics.error(
                                "@Serialized type for '$propertyName' is not serializable; add @Serializable or @SerializedWith",
                                property,
                            )
                            return null
                        }
                        fieldKind = FieldKind.SERIALIZED
                        fieldClass = if (nullable) nullableSerializedField else serializedField
                    } else {
                        val native = nativeCollectionField(baseType, nullable)
                        if (native != null) {
                            fieldKind = FieldKind.COLLECTION
                            fieldClass = native
                        } else if (serializable) {
                            fieldKind = FieldKind.SERIALIZED
                            fieldClass = if (nullable) nullableSerializedField else serializedField
                        } else {
                            diagnostics.error(
                                "Unsupported collection type for '$propertyName'; add @Serialized or @SerializedWith",
                                property,
                            )
                            return null
                        }
                    }
                }

                isEnumType(baseType) -> {
                    if (hasSerialized) {
                        if (!serializable) {
                            diagnostics.error(
                                "@Serialized enum '$propertyName' must be @Serializable or use @SerializedWith",
                                property,
                            )
                            return null
                        }
                        fieldKind = FieldKind.SERIALIZED
                        fieldClass = if (nullable) nullableSerializedField else serializedField
                    } else {
                        fieldKind = FieldKind.ENUM
                        fieldClass = if (nullable) nullableEnumField else enumField
                    }
                }

                isPrimitiveType(baseName) -> {
                    if (hasSerialized) {
                        fieldKind = FieldKind.SERIALIZED
                        fieldClass = if (nullable) nullableSerializedField else serializedField
                    } else {
                        fieldKind = FieldKind.PRIMITIVE
                        fieldClass = primitiveField(baseName, nullable)
                    }
                }

                serializable -> {
                    fieldKind = FieldKind.SERIALIZED
                    fieldClass = if (nullable) nullableSerializedField else serializedField
                }

                else -> {
                    diagnostics.error(
                        "Unsupported type for '$propertyName' (${baseName ?: "unknown"}); add @Serializable, @Serialized, or @SerializedWith",
                        property,
                    )
                    return null
                }
            }
        }

        if (fieldClass == null) {
            diagnostics.error("No backing field supports '$propertyName' (${baseName ?: "unknown"})", property)
            return null
        }

        val (valueKind, valueKindEnumName) = valueKindFor(baseType)
        val enumTypeName = if (isEnumType(baseType)) {
            valueKindEnumName ?: baseType.toClassNameOrNull()?.canonicalName
        } else {
            null
        }
        val logicalKey = keyFor(property, kind)
        val physicalKeys = physicalKeysFor(logicalKey, fieldKind, baseName, nullable, fieldClass)
        return FieldPlan(
            property = property,
            propertyName = propertyName,
            propertyType = propertyType,
            baseType = baseType,
            nullable = nullable,
            kind = fieldKind,
            fieldClass = fieldClass,
            logicalKey = logicalKey,
            physicalKeys = physicalKeys,
            valueKind = valueKind,
            enumTypeName = enumTypeName,
            serializer = serializerSpec,
            validator = validator,
        )
    }

    private fun parseCustomValidator(
        property: KSPropertyDeclaration,
        resolver: Resolver,
        diagnostics: Diagnostics,
    ): CustomValidatorSpec? {
        val annotation = property.getAnnotation(VALIDATED_BY_ANNOTATION) ?: return null
        val validatorType = annotation.argument("validator")?.value as? KSType
        if (validatorType == null || validatorType.isError) {
            diagnostics.error("Cannot resolve @ValidatedBy validator for '${property.simpleName.asString()}'", property)
            return null
        }
        val declaration = validatorType.declaration as? KSClassDeclaration
        if (declaration == null) {
            diagnostics.error("@ValidatedBy must name a validator class", property)
            return null
        }
        if (declaration.typeParameters.isNotEmpty()) {
            diagnostics.error("Generic @ValidatedBy validator classes are not supported ('${property.simpleName.asString()}')", property)
            return null
        }
        if (Modifier.INNER in declaration.modifiers || Modifier.ABSTRACT in declaration.modifiers) {
            diagnostics.error("@ValidatedBy validator must be a concrete top-level or nested class", property)
            return null
        }
        if (!isAccessible(declaration) || hasInaccessibleParent(declaration) || hasGenericParent(declaration)) {
            diagnostics.error("@ValidatedBy validator ${canonicalName(declaration)} must be public or internal", property)
            return null
        }
        val className = validatorType.toClassNameOrNull()
        if (className == null) {
            diagnostics.error("Cannot resolve validator class for '${property.simpleName.asString()}'", property)
            return null
        }
        if (!validatorTypeMatchesProperty(declaration, property, resolver)) {
            diagnostics.error(
                "@ValidatedBy type must match '${property.simpleName.asString()}' (${property.type.resolve()})",
                property,
            )
            return null
        }
        if (declaration.classKind == ClassKind.OBJECT) {
            return CustomValidatorSpec(className, declaration, CodeBlock.of("%T", className))
        }
        if (declaration.classKind != ClassKind.CLASS) {
            diagnostics.error("@ValidatedBy validator must be an object or a class", property)
            return null
        }
        if (!hasAccessibleNoArgConstructor(declaration)) {
            diagnostics.error("@ValidatedBy validator ${canonicalName(declaration)} needs an accessible no-arg constructor", property)
            return null
        }
        return CustomValidatorSpec(className, declaration, CodeBlock.of("%T()", className))
    }

    private fun validatorTypeMatchesProperty(
        declaration: KSClassDeclaration,
        property: KSPropertyDeclaration,
        resolver: Resolver,
    ): Boolean {
        val argument = findSupertypeTypeArgument(
            declaration,
            "io.github.mlmgames.settings.core.annotations.SettingValidator",
            resolver,
        ) ?: return false
        val propertyType = property.type.resolve()
        return !argument.isError && argument.isAssignableFrom(propertyType)
    }

    private fun findSupertypeTypeArgument(
        declaration: KSClassDeclaration,
        targetName: String,
        resolver: Resolver,
        seen: MutableSet<String> = mutableSetOf(),
    ): KSType? {
        val id = canonicalName(declaration)
        if (!seen.add(id)) return null
        for (reference in declaration.superTypes) {
            val resolved = runCatching { reference.resolve() }.getOrNull() ?: continue
            val qualifiedName = resolved.declaration.qualifiedName?.asString()
            if (qualifiedName == targetName) {
                return resolved.arguments.firstOrNull()?.type?.resolve()
            }
            val parent = resolved.declaration as? KSClassDeclaration ?: continue
            val nested = findSupertypeTypeArgument(parent, targetName, resolver, seen) ?: continue
            if (resolved.arguments.size < parent.typeParameters.size) return nested
            return nested.replace(resolved.arguments)
        }
        return null
    }

    private fun hasAccessibleNoArgConstructor(declaration: KSClassDeclaration): Boolean {
        val constructors = buildList<KSFunctionDeclaration> {
            declaration.primaryConstructor?.let(::add)
            addAll(
                declaration.declarations
                    .filterIsInstance<KSFunctionDeclaration>()
                    .filter { it.simpleName.asString() == "<init>" },
            )
        }
        return constructors.any { constructor ->
            Modifier.PRIVATE !in constructor.modifiers &&
                Modifier.PROTECTED !in constructor.modifiers &&
                constructor.parameters.all { it.hasDefault }
        }
    }

    private fun serializerTypeMatchesProperty(
        declaration: KSClassDeclaration,
        property: KSPropertyDeclaration,
        resolver: Resolver,
    ): Boolean {
        val argument = findSupertypeTypeArgument(
            declaration,
            "io.github.mlmgames.settings.core.annotations.SettingSerializer",
            resolver,
        ) ?: return false
        val propertyType = property.type.resolve().makeNotNullable()
        return !argument.isError && argument.isAssignableFrom(propertyType)
    }

    private fun parseCustomSerializer(
        property: KSPropertyDeclaration,
        resolver: Resolver,
        diagnostics: Diagnostics,
    ): CustomSerializerSpec? {
        val annotation = property.getAnnotation(SERIALIZED_WITH_ANNOTATION) ?: return null
        val serializerType = annotation.argument("serializer")?.value as? KSType
        if (serializerType == null || serializerType.isError) {
            diagnostics.error("Cannot resolve @SerializedWith serializer for '${property.simpleName.asString()}'", property)
            return null
        }
        val declaration = serializerType.declaration as? KSClassDeclaration
        if (declaration == null) {
            diagnostics.error("@SerializedWith must name a serializer class", property)
            return null
        }
        if (declaration.typeParameters.isNotEmpty()) {
            diagnostics.error("Generic @SerializedWith serializer classes are not supported ('${property.simpleName.asString()}')", property)
            return null
        }
        if (Modifier.INNER in declaration.modifiers || Modifier.ABSTRACT in declaration.modifiers) {
            diagnostics.error("@SerializedWith serializer must be a concrete top-level or nested class", property)
            return null
        }
        if (!isAccessible(declaration) || hasInaccessibleParent(declaration) || hasGenericParent(declaration)) {
            diagnostics.error("@SerializedWith serializer ${canonicalName(declaration)} must be public or internal", property)
            return null
        }
        val className = serializerType.toClassNameOrNull()
            ?: declaration.toClassNameCompat().also {
                diagnostics.error("Cannot resolve serializer class for '${property.simpleName.asString()}'", property)
            }
        if (!serializerTypeMatchesProperty(declaration, property, resolver)) {
            diagnostics.error(
                "@SerializedWith type must match '${property.simpleName.asString()}' (${property.type.resolve()})",
                property,
            )
            return null
        }
        if (declaration.classKind == ClassKind.OBJECT) {
            return CustomSerializerSpec(className, declaration, CodeBlock.of("%T", className))
        }
        if (declaration.classKind != ClassKind.CLASS) {
            diagnostics.error("@SerializedWith serializer ${canonicalName(declaration)} must be an object or a class", property)
            return null
        }
        if (!hasAccessibleNoArgConstructor(declaration)) {
            diagnostics.error("@SerializedWith serializer ${canonicalName(declaration)} needs an accessible no-arg constructor", property)
            return null
        }
        return CustomSerializerSpec(className, declaration, CodeBlock.of("%T()", className))
    }

    private fun parseSettingConfig(
        property: KSPropertyDeclaration,
        resolver: Resolver,
        diagnostics: Diagnostics,
    ): SettingConfig? {
        val propertyName = property.simpleName.asString()
        val annotation = property.getAnnotation(SETTING_ANNOTATION) ?: return null
        val categoryType = annotation.argument("category")?.value as? KSType
        val categoryDeclaration = categoryType?.declaration as? KSClassDeclaration
        if (categoryType == null || categoryDeclaration == null) {
            diagnostics.error("Missing or invalid category for '$propertyName'", property)
            return null
        }
        if (categoryDeclaration.classKind != ClassKind.OBJECT) {
            diagnostics.error("Category for '$propertyName' must be an object", property)
        }
        if (!isAccessible(categoryDeclaration)) {
            diagnostics.error("Category ${canonicalName(categoryDeclaration)} must be public or internal", property)
        }
        val categoryAnnotation = categoryDeclaration.getAnnotation(CATEGORY_DEF_ANNOTATION)
        if (categoryAnnotation == null) {
            diagnostics.error("Category ${categoryDeclaration.simpleName.asString()} lacks @CategoryDefinition", property)
        }
        val categoryTitleRes = categoryAnnotation?.argument("titleRes")?.intValue() ?: 0
        val categoryTitleKey = categoryAnnotation?.argument("titleKey")?.stringValue() ?: ""
        if (categoryTitleRes < 0) diagnostics.error("Category titleRes must not be negative ('$propertyName')", property)
        validateStringKey(categoryTitleKey, "category titleKey", propertyName, property, diagnostics)
        val categoryOrder = categoryAnnotation?.argument("order")?.intValue() ?: 0
        val categoryClass = categoryType.toClassNameOrNull()
        if (categoryClass == null) {
            diagnostics.error("Cannot resolve category for '$propertyName'", property)
            return null
        }

        val typeType = annotation.argument("type")?.value as? KSType
        val typeClass = typeType?.toClassNameOrNull()
            ?: ClassName(typesPackage, "Toggle")
        val uiKind = uiKind(typeClass)
        val title = annotation.argument("title")?.stringValue() ?: ""
        val description = annotation.argument("description")?.stringValue() ?: ""
        val titleRes = annotation.argument("titleRes")?.intValue() ?: 0
        val titleKey = annotation.argument("titleKey")?.stringValue() ?: ""
        val descriptionRes = annotation.argument("descriptionRes")?.intValue() ?: 0
        val descriptionKey = annotation.argument("descriptionKey")?.stringValue() ?: ""
        val key = keyFor(property, AnnotationKind.SETTING)
        val dependsOn = annotation.argument("dependsOn")?.stringValue() ?: ""
        val min = annotation.argument("min")?.floatValue() ?: 0f
        val max = annotation.argument("max")?.floatValue() ?: 100f
        val step = annotation.argument("step")?.floatValue() ?: 1f
        val options = stringListArgument(annotation, "options", diagnostics, propertyName)
        val optionsRes = annotation.argument("optionsRes")?.intValue() ?: 0
        val optionsKey = annotation.argument("optionsKey")?.stringValue() ?: ""
        validateStringKey(titleKey, "titleKey", propertyName, property, diagnostics)
        validateStringKey(descriptionKey, "descriptionKey", propertyName, property, diagnostics)
        validateStringKey(optionsKey, "optionsKey", propertyName, property, diagnostics)
        if (titleRes < 0 || descriptionRes < 0 || optionsRes < 0) {
            diagnostics.error("Resource IDs must not be negative ('$propertyName')", property)
        }
        val platforms = platformListArgument(annotation, propertyName, diagnostics)
        val actionClass = parseActionClass(property, diagnostics)
        val validationMessage = parseValidationMessage(property, diagnostics)
        return SettingConfig(
            title = title,
            description = description,
            titleRes = titleRes,
            descriptionRes = descriptionRes,
            titleKey = titleKey,
            descriptionKey = descriptionKey,
            categoryClass = categoryClass,
            categoryOrder = categoryOrder,
            categoryTitleRes = categoryTitleRes,
            categoryTitleKey = categoryTitleKey,
            typeClass = typeClass,
            uiKind = uiKind,
            key = key,
            dependsOn = dependsOn,
            min = min,
            max = max,
            step = step,
            options = options,
            optionsRes = optionsRes,
            optionsKey = optionsKey,
            actionClass = actionClass,
            platforms = platforms,
            validationMessage = validationMessage,
        )
    }

    private fun parseActionClass(property: KSPropertyDeclaration, diagnostics: Diagnostics): ClassName? {
        val annotation = property.getAnnotation(ACTION_HANDLER_ANNOTATION) ?: return null
        val actionType = annotation.argument("action")?.value as? KSType
        val declaration = actionType?.declaration as? KSClassDeclaration
        if (actionType == null || declaration == null) {
            diagnostics.error("Cannot resolve @ActionHandler for '${property.simpleName.asString()}'", property)
            return null
        }
        if (!isAccessible(declaration)) {
            diagnostics.error("@ActionHandler class ${canonicalName(declaration)} must be public or internal", property)
        }
        return actionType.toClassNameOrNull()
    }

    private fun parseValidationMessage(property: KSPropertyDeclaration, diagnostics: Diagnostics): ValidationMessage? {
        val messages = mutableListOf<ValidationMessage>()
        for (annotationName in listOf(RANGE_ANNOTATION, LENGTH_ANNOTATION, PATTERN_ANNOTATION, REQUIRED_ANNOTATION)) {
            val annotation = property.getAnnotation(annotationName) ?: continue
            val message = annotation.argument("errorMessage")?.stringValue() ?: defaultValidationMessage(annotationName)
            val resource = annotation.argument("errorMessageRes")?.intValue() ?: 0
            val explicitKey = annotation.argument("errorMessageKey")?.stringValue().orEmpty()
            val key = if (explicitKey.isNotBlank()) {
                explicitKey
            } else if (resource == 0 && message == defaultValidationMessage(annotationName)) {
                defaultValidationKey(annotationName)
            } else {
                ""
            }
            if (resource < 0) diagnostics.error("Validation errorMessageRes must not be negative ('${property.simpleName.asString()}')", property)
            validateStringKey(key, "errorMessageKey", property.simpleName.asString(), property, diagnostics)
            messages += ValidationMessage(message, resource, key)
        }
        val distinct = messages.distinct()
        if (distinct.size > 1) {
            diagnostics.error(
                "ValidationRules exposes one errorMessage/errorMessageRes/errorMessageKey tuple; use the same message for every rule on '${property.simpleName.asString()}'",
                property,
            )
        }
        return distinct.firstOrNull()
    }

    private fun defaultValidationMessage(annotationName: String): String = when (annotationName) {
        RANGE_ANNOTATION -> "Value out of range"
        LENGTH_ANNOTATION -> "Invalid length"
        PATTERN_ANNOTATION -> "Invalid format"
        else -> "This field is required"
    }

    private fun defaultValidationKey(annotationName: String): String = when (annotationName) {
        RANGE_ANNOTATION -> "settings_value_out_of_range"
        LENGTH_ANNOTATION -> "settings_invalid_length"
        PATTERN_ANNOTATION -> "settings_invalid_format"
        else -> "settings_required"
    }

    private fun effectiveLocalizationKey(
        key: String,
        resId: Int,
        text: String,
        defaultText: String,
        defaultKey: String,
    ): String = when {
        key.isNotBlank() -> key
        resId == 0 && text == defaultText -> defaultKey
        else -> ""
    }

    private fun validateSetting(
        property: KSPropertyDeclaration,
        plan: FieldPlan,
        config: SettingConfig,
        resolver: Resolver,
        diagnostics: Diagnostics,
    ) {
        val name = property.simpleName.asString()
        if (config.titleKey.isBlank() && config.titleRes == 0) {
            if (config.title.isBlank()) {
                diagnostics.warn(
                    "@Setting '$name' has no title metadata; provide title= or titleKey=",
                    property,
                )
            } else {
                diagnostics.warn(
                    "@Setting '$name' uses a literal title; provide titleKey= or titleRes= for localization",
                    property,
                )
            }
        }
        if (config.description.isNotBlank() && config.descriptionKey.isBlank() && config.descriptionRes == 0) {
            diagnostics.warn(
                "@Setting '$name' uses a literal description; provide descriptionKey= or descriptionRes= for localization",
                property,
            )
        }
        if (config.options.isNotEmpty() && config.optionsKey.isBlank() && config.optionsRes == 0) {
            diagnostics.warn(
                "@Setting '$name' uses literal dropdown options; provide optionsKey= or optionsRes= for localization",
                property,
            )
        }
        if (!config.min.isFinite() || !config.max.isFinite() || !config.step.isFinite()) {
            diagnostics.error("@Setting min, max, and step must be finite ('$name')", property)
        }
        if (config.min >= config.max) {
            diagnostics.error("@Setting min (${config.min}) must be < max (${config.max}): $name", property)
        }
        if (!(config.step > 0f) || config.step.isNaN()) {
            diagnostics.error("@Setting step (${config.step}) must be > 0: $name", property)
        }
        if (!(config.max - config.min).isFinite()) {
            diagnostics.error("@Setting slider range is too large or non-finite ('$name')", property)
        }

        validateUiCompatibility(property, plan, config, diagnostics)
        validateSettingValidationAnnotations(property, plan, diagnostics)
        validateResetAndConfirmation(property, diagnostics)

        if (config.uiKind == UiKind.DROPDOWN) {
            val baseName = plan.baseType.declaration.qualifiedName?.asString()
            if (baseName in setOf("kotlin.Int", "kotlin.Long", "kotlin.Float", "kotlin.Double", "kotlin.String")) {
                if (config.options.isEmpty() && config.optionsRes == 0 && config.optionsKey.isBlank()) {
                    diagnostics.error("Dropdown '$name' needs options=, optionsRes=, or optionsKey=", property)
                }
            }
            if (isEnumType(plan.baseType)) {
                val count = (plan.baseType.declaration as? KSClassDeclaration)
                    ?.declarations
                    ?.filterIsInstance<KSClassDeclaration>()
                    ?.count { it.classKind == ClassKind.ENUM_ENTRY }
                    ?: -1
                if (count >= 0 && config.options.isNotEmpty() && config.options.size != count) {
                    diagnostics.error(
                        "Dropdown options for enum '$name' must align by index with entries (${config.options.size} labels vs $count entries)",
                        property,
                    )
                }
            }
        }

        if (config.uiKind == UiKind.BUTTON && config.actionClass == null) {
            diagnostics.error("Button '$name' requires @ActionHandler", property)
        }
        if (config.uiKind != UiKind.BUTTON && config.actionClass != null) {
            diagnostics.error("@ActionHandler is only valid on Button type ('$name')", property)
        }
    }

    private fun validateUiCompatibility(
        property: KSPropertyDeclaration,
        plan: FieldPlan,
        config: SettingConfig,
        diagnostics: Diagnostics,
    ) {
        val name = property.simpleName.asString()
        val typeName = plan.baseType.declaration.qualifiedName?.asString()
        val serialized = plan.kind == FieldKind.SERIALIZED
        when (config.uiKind) {
            UiKind.TOGGLE -> {
                if (typeName != "kotlin.Boolean" || serialized) {
                    diagnostics.error("Toggle '$name' requires a Boolean backed by a primitive field", property)
                }
            }

            UiKind.SLIDER -> {
                if (typeName !in setOf("kotlin.Int", "kotlin.Long", "kotlin.Float", "kotlin.Double") || serialized) {
                    diagnostics.error("Slider '$name' requires a numeric primitive field", property)
                }
            }

            UiKind.DROPDOWN -> {
                if (isEnumType(plan.baseType)) {
                    if (serialized) {
                        diagnostics.error("Dropdown enum '$name' cannot use serialized storage without a custom UI handler", property)
                    }
                } else if (typeName !in setOf("kotlin.Int", "kotlin.Long", "kotlin.Float", "kotlin.Double", "kotlin.String") || serialized) {
                    diagnostics.error("Dropdown '$name' requires a numeric, String, or enum field", property)
                }
            }

            UiKind.TEXT_INPUT -> {
                if (typeName != "kotlin.String" || serialized) {
                    diagnostics.error("TextInput '$name' requires a String backed by the native string field", property)
                }
            }

            UiKind.TIME_PICKER -> {
                if (typeName != "kotlin.Int" || serialized) {
                    diagnostics.error("TimePickerType '$name' requires an Int", property)
                }
            }

            UiKind.BUTTON -> {
                if (typeName != "kotlin.Unit" || plan.nullable || serialized) {
                    diagnostics.error("Button '$name' must be backed by non-null Unit", property)
                }
            }

            UiKind.CUSTOM -> Unit
        }
    }

    private fun validateSettingValidationAnnotations(
        property: KSPropertyDeclaration,
        plan: FieldPlan,
        diagnostics: Diagnostics,
    ) {
        val name = property.simpleName.asString()
        val typeName = plan.baseType.declaration.qualifiedName?.asString()
        val numeric = typeName in setOf("kotlin.Int", "kotlin.Long", "kotlin.Float", "kotlin.Double")
        val string = typeName == "kotlin.String"
        if (property.hasAnnotation(RANGE_ANNOTATION) && !numeric) {
            diagnostics.error("@Range applies to numeric types only ('$name' is ${typeName ?: "unknown"})", property)
        }
        if (property.hasAnnotation(LENGTH_ANNOTATION) && !string) {
            diagnostics.error("@Length applies to String only ('$name' is ${typeName ?: "unknown"})", property)
        }
        if (property.hasAnnotation(PATTERN_ANNOTATION) && !string) {
            diagnostics.error("@Pattern applies to String only ('$name' is ${typeName ?: "unknown"})", property)
        }
        if (property.hasAnnotation(REQUIRED_ANNOTATION) && typeName == "kotlin.Unit") {
            diagnostics.error("@Required cannot be used on Unit/Button '$name'", property)
        }
        validateRuleBounds(property, diagnostics)
    }

    private fun validateRuleBounds(property: KSPropertyDeclaration, diagnostics: Diagnostics) {
        val name = property.simpleName.asString()
        property.getAnnotation(RANGE_ANNOTATION)?.let { annotation ->
            val min = annotation.argument("min")?.doubleValue() ?: -Double.MAX_VALUE
            val max = annotation.argument("max")?.doubleValue() ?: Double.MAX_VALUE
            if (!min.isFinite() || !max.isFinite()) {
                diagnostics.error("@Range bounds must be finite ('$name')", property)
            }
            if (min > max) {
                diagnostics.error("@Range min ($min) must be <= max ($max): $name", property)
            }
        }
        property.getAnnotation(LENGTH_ANNOTATION)?.let { annotation ->
            val min = annotation.argument("min")?.intValue() ?: 0
            val max = annotation.argument("max")?.intValue() ?: Int.MAX_VALUE
            if (min < 0 || max < 0 || min > max) {
                diagnostics.error("@Length requires 0 <= min <= max ('$name')", property)
            }
        }
        property.getAnnotation(PATTERN_ANNOTATION)?.let { annotation ->
            val regex = annotation.argument("regex")?.stringValue() ?: ""
            runCatching { Regex(regex) }.onFailure {
                diagnostics.error("Invalid @Pattern regex for '$name': ${it.message}", property)
            }
        }
    }

    private fun validateResetAndConfirmation(property: KSPropertyDeclaration, diagnostics: Diagnostics) {
        val name = property.simpleName.asString()
        if (property.hasAnnotation(NO_RESET_ANNOTATION) && property.hasAnnotation(CONFIRM_RESET_ANNOTATION)) {
            diagnostics.error("@NoReset and @ConfirmReset cannot be combined ('$name')", property)
        }
        property.getAnnotation(CONFIRM_RESET_ANNOTATION)?.let { annotation ->
            validateStringKey(
                annotation.argument("messageKey")?.stringValue().orEmpty(),
                "messageKey",
                name,
                property,
                diagnostics,
            )
        }
        if (property.hasAnnotation(REQUIRES_CONFIRMATION_ANNOTATION)) {
            val annotation = property.getAnnotation(REQUIRES_CONFIRMATION_ANNOTATION)!!
            val resources = listOf("titleRes", "messageRes", "confirmTextRes", "cancelTextRes")
            for (resource in resources) {
                val value = annotation.argument(resource)?.intValue() ?: 0
                if (value < 0) diagnostics.error("Confirmation resource IDs must not be negative ('$name')", property)
            }
            for (key in listOf("titleKey", "messageKey", "confirmTextKey", "cancelTextKey")) {
                validateStringKey(annotation.argument(key)?.stringValue().orEmpty(), key, name, property, diagnostics)
            }
        }
    }

    private fun validatePersisted(property: KSPropertyDeclaration, diagnostics: Diagnostics) {
        val name = property.simpleName.asString()
        val ignored = listOf(
            RANGE_ANNOTATION,
            LENGTH_ANNOTATION,
            PATTERN_ANNOTATION,
            REQUIRED_ANNOTATION,
            VALIDATED_BY_ANNOTATION,
            ACTION_HANDLER_ANNOTATION,
            REQUIRES_CONFIRMATION_ANNOTATION,
        )
        for (annotation in ignored) {
            if (property.hasAnnotation(annotation)) {
                val shortName = annotation.substringAfterLast('.')
                val message = if (annotation == NO_RESET_ANNOTATION || annotation == CONFIRM_RESET_ANNOTATION) {
                    "$shortName on @Persisted '$name' cannot be represented without UI metadata; move it to @Setting"
                } else {
                    "$shortName on @Persisted '$name' is not supported; move it to @Setting"
                }
                diagnostics.error(message, property)
            }
        }
    }

    private fun validateDependencies(properties: List<KSPropertyDeclaration>, diagnostics: Diagnostics) {
        val byName = properties.associateBy { it.simpleName.asString() }
        val edges = mutableMapOf<String, String>()
        for (property in properties) {
            val name = property.simpleName.asString()
            val setting = property.getAnnotation(SETTING_ANNOTATION) ?: continue
            val dependency = setting.argument("dependsOn")?.stringValue() ?: ""
            if (dependency.isBlank()) continue
            if (dependency == name) {
                diagnostics.error("dependsOn must not reference itself: '$name'", property)
                continue
            }
            if (dependency !in byName) {
                diagnostics.error("dependsOn='$dependency' does not name a property (field '$name')", property)
                continue
            }
            edges[name] = dependency
        }
        for (start in edges.keys) {
            val seen = mutableSetOf<String>()
            var cursor: String? = start
            while (cursor != null && edges.containsKey(cursor)) {
                if (!seen.add(cursor)) {
                    diagnostics.error("Cyclic dependsOn chain involving '$start'", byName[start])
                    break
                }
                cursor = edges[cursor]
            }
        }
    }

    private fun validateRenames(
        metadata: Map<String, FieldMetadata>,
        plans: List<FieldPlan>,
        schemaVersion: Int,
        hasSchemaVersion: Boolean,
        diagnostics: Diagnostics,
    ) {
        val byName = plans.associateBy { it.propertyName }
        val previousOwners = mutableMapOf<String, String>()
        metadata.forEach { (fieldName, details) ->
            val property = byName[fieldName]?.property
            val hasLifecycle = details.renamedFrom != null || details.addedInVersion != null || details.removeInVersion != null
            if (hasLifecycle && !hasSchemaVersion) {
                diagnostics.error("@SchemaVersion is required when lifecycle annotations are used ('$fieldName')", property)
            }
            if (details.addedInVersion != null && details.addedInVersion > schemaVersion) {
                diagnostics.error("@AddedInVersion exceeds @SchemaVersion ('$fieldName')", property)
            }
            if (details.removeInVersion != null && details.removeInVersion < schemaVersion) {
                diagnostics.error("@DeprecatedSetting removeInVersion is already reached ('$fieldName')", property)
            }
            val previousKey = details.renamedFrom ?: return@forEach
            if (previousKey in RESERVED_KEYS || previousKey.startsWith(UNKNOWN_BACKUP_PREFIX) || previousKey.startsWith("__kmp_settings_v2__:")) {
                diagnostics.error("@RenamedFrom key '$previousKey' is reserved", property)
            }
            val previousOwner = previousOwners.putIfAbsent(previousKey, fieldName)
            if (previousOwner != null) {
                diagnostics.error("Duplicate @RenamedFrom key '$previousKey' (fields '$previousOwner' and '$fieldName')", property)
            }
            val plan = byName[fieldName] ?: return@forEach
            val oldAliases = physicalKeysFor(
                key = previousKey,
                kind = plan.kind,
                baseName = plan.baseType.declaration.qualifiedName?.asString(),
                nullable = plan.nullable,
                fieldClass = plan.fieldClass,
            ).toSet()
            val conflict = plans.firstOrNull { other ->
                other.propertyName != fieldName && oldAliases.any { it in other.physicalKeys }
            }
            if (conflict != null) {
                diagnostics.error(
                    "@RenamedFrom physical alias '$previousKey' collides with field '${conflict.propertyName}'",
                    property,
                )
            }
            if (details.renamedSinceVersion != null && details.renamedSinceVersion > schemaVersion) {
                diagnostics.error("@RenamedFrom sinceVersion exceeds @SchemaVersion ('$fieldName')", property)
            }
        }
    }

    private fun validateKeys(plans: List<FieldPlan>, diagnostics: Diagnostics) {
        val logicalOwners = mutableMapOf<String, String>()
        val physicalOwners = mutableMapOf<String, String>()
        for (plan in plans) {
            val propertyName = plan.propertyName
            val key = plan.logicalKey
            if (key.isBlank() || key != key.trim()) {
                diagnostics.error("Persistence key '$key' must not be blank or contain surrounding whitespace ('$propertyName')", plan.property)
            }
            if (key in RESERVED_KEYS || key.startsWith(UNKNOWN_BACKUP_PREFIX) || key.startsWith("__kmp_settings_v2__:")) {
                diagnostics.error("Persistence key '$key' is reserved by the settings runtime ('$propertyName')", plan.property)
            }
            val previousLogical = logicalOwners.putIfAbsent(key, propertyName)
            if (previousLogical != null) {
                diagnostics.error(
                    "Duplicate persistence key '$key' (properties '$previousLogical' and '$propertyName')",
                    plan.property,
                )
            }
            for (physical in plan.physicalKeys) {
                if (physical in RESERVED_KEYS || physical.startsWith(UNKNOWN_BACKUP_PREFIX)) {
                    diagnostics.error("Physical persistence key '$physical' is reserved ('$propertyName')", plan.property)
                }
                val previousPhysical = physicalOwners.putIfAbsent(physical, propertyName)
                if (previousPhysical != null) {
                    diagnostics.error(
                        "Physical persistence key collision '$physical' (properties '$previousPhysical' and '$propertyName')",
                        plan.property,
                    )
                }
            }
        }
    }

    private fun validateGeneratedTypeReferences(
        plans: List<FieldPlan>,
        diagnostics: Diagnostics,
    ) {
        val references = mutableMapOf<String, KSDeclaration>()
        fun register(declaration: KSDeclaration, symbol: KSNode) {
            val name = canonicalName(declaration)
            val previous = references.putIfAbsent(name, declaration)
            if (previous != null && previous.qualifiedName?.asString() != declaration.qualifiedName?.asString()) {
                diagnostics.error("Type canonical name collision for '$name'", symbol)
            }
        }
        for (plan in plans) {
            register(plan.baseType.declaration, plan.property)
            plan.serializer?.let { register(it.declaration, plan.property) }
        }
    }

    private fun parseSchemaVersion(klass: KSClassDeclaration, diagnostics: Diagnostics): Int {
        val annotation = klass.getAnnotation(SCHEMA_VERSION_ANNOTATION) ?: return 0
        val version = annotation.argument("version")?.intValue() ?: 0
        if (version < 0) diagnostics.error("@SchemaVersion version must not be negative", klass)
        return version
    }

    private fun parseMetadata(
        property: KSPropertyDeclaration,
        plan: FieldPlan?,
        diagnostics: Diagnostics,
    ): FieldMetadata {
        val name = property.simpleName.asString()
        val renamed = property.getAnnotation(RENAMED_FROM_ANNOTATION)
        val added = property.getAnnotation(ADDED_IN_VERSION_ANNOTATION)
        val deprecated = property.getAnnotation(DEPRECATED_SETTING_ANNOTATION)
        val previousKey = renamed?.argument("previousKey")?.stringValue()
        val renamedSince = renamed?.argument("sinceVersion")?.intValue()
        val addedVersion = added?.argument("version")?.intValue()
        val removeVersion = deprecated?.argument("removeInVersion")?.intValue()
        val deprecationMessage = deprecated?.argument("message")?.stringValue()
        if (previousKey != null) {
            if (previousKey.isBlank() || previousKey != previousKey.trim()) {
                diagnostics.error("@RenamedFrom previousKey must not be blank or padded ('$name')", property)
            }
            if (plan != null && previousKey == plan.logicalKey) {
                diagnostics.error("@RenamedFrom previousKey must differ from the current key ('$name')", property)
            }
        }
        if (renamedSince != null && renamedSince < 1) {
            diagnostics.error("@RenamedFrom sinceVersion must be >= 1 ('$name')", property)
        }
        if (addedVersion != null && addedVersion < 1) {
            diagnostics.error("@AddedInVersion version must be >= 1 ('$name')", property)
        }
        if (removeVersion != null && removeVersion < 1) {
            diagnostics.error("@DeprecatedSetting removeInVersion must be >= 1 ('$name')", property)
        }
        if (addedVersion != null && removeVersion != null && removeVersion <= addedVersion) {
            diagnostics.error("@DeprecatedSetting removeInVersion must be greater than @AddedInVersion ('$name')", property)
        }
        return FieldMetadata(
            renamedFrom = previousKey,
            renamedSinceVersion = renamedSince,
            addedInVersion = addedVersion,
            deprecated = deprecated != null,
            deprecationMessage = deprecationMessage,
            removeInVersion = removeVersion,
        )
    }

    private fun generateSchema(
        klass: KSClassDeclaration,
        properties: List<KSPropertyDeclaration>,
        analysis: ClassAnalysis,
        schemaName: String,
        resolver: Resolver,
    ) {
        val packageName = klass.packageName.asString()
        val modelClass = klass.toClassName()
        val objectBuilder = TypeSpec.objectBuilder(schemaName)
            .addSuperinterface(settingsSchema.parameterizedBy(modelClass))
        if (effectiveVisibility(klass) == Modifier.INTERNAL) objectBuilder.addModifiers(KModifier.INTERNAL)

        objectBuilder.addProperty(
            PropertySpec.builder("default", modelClass)
                .addModifiers(KModifier.OVERRIDE)
                .initializer("%T()", modelClass)
                .build(),
        )

        val fieldsCode = CodeBlock.builder().add("listOf(\n").indent()
        for (plan in analysis.plans) {
            val config = analysis.settingConfigs[plan.propertyName]
            fieldsCode.add("%L,\n", generateFieldCode(plan, config, modelClass, klass, schemaName, resolver))
        }
        fieldsCode.unindent().add(")")
        objectBuilder.addProperty(
            PropertySpec.builder(
                "fields",
                ClassName("kotlin.collections", "List").parameterizedBy(
                    settingField.parameterizedBy(modelClass, com.squareup.kotlinpoet.STAR),
                ),
            )
                .addModifiers(KModifier.OVERRIDE)
                .initializer(fieldsCode.build())
                .build(),
        )

        objectBuilder.addProperty(
            PropertySpec.builder("schemaVersion", ClassName("kotlin", "Int"))
                .addModifiers(KModifier.OVERRIDE)
                .initializer("%L", analysis.schemaVersion)
                .build(),
        )
        objectBuilder.addProperty(
            PropertySpec.builder(
                "fieldMetadata",
                ClassName("kotlin.collections", "Map").parameterizedBy(
                    ClassName("kotlin", "String"),
                    schemaFieldMetadataClass,
                ),
            ).addModifiers(KModifier.OVERRIDE)
                .initializer(buildFieldMetadata(analysis)).build(),
        )
        objectBuilder.addProperty(
            PropertySpec.builder(
                "categoryTitleResources",
                ClassName("kotlin.collections", "Map").parameterizedBy(
                    ClassName("kotlin.reflect", "KClass").parameterizedBy(com.squareup.kotlinpoet.STAR),
                    ClassName("kotlin", "Int"),
                ),
            ).addModifiers(KModifier.OVERRIDE)
                .initializer(buildCategoryTitleResources(analysis)).build(),
        )

        objectBuilder.addProperty(
            PropertySpec.builder(
                "categoryTitleKeys",
                ClassName("kotlin.collections", "Map").parameterizedBy(
                    ClassName("kotlin.reflect", "KClass").parameterizedBy(com.squareup.kotlinpoet.STAR),
                    ClassName("kotlin", "String"),
                ),
            ).addModifiers(KModifier.OVERRIDE)
                .initializer(buildCategoryTitleKeys(analysis)).build(),
        )

        val fileSpec = com.squareup.kotlinpoet.FileSpec.builder(packageName, schemaName)
            .addType(objectBuilder.build())
            .build()
        val sourceFiles = linkedSetOf<KSFile>()
        klass.containingFile?.let(sourceFiles::add)
        properties.mapNotNull { it.containingFile }.forEach(sourceFiles::add)
        analysis.plans.forEach { plan ->
            plan.baseType.declaration.containingFile?.let(sourceFiles::add)
            plan.serializer?.declaration?.containingFile?.let(sourceFiles::add)
            plan.validator?.declaration?.containingFile?.let(sourceFiles::add)
        }
        analysis.settingConfigs.values.forEach { config ->
            resolver.getClassDeclarationByName(resolver.getKSNameFromString(config.categoryClass.canonicalName))?.containingFile?.let(sourceFiles::add)
            resolver.getClassDeclarationByName(resolver.getKSNameFromString(config.typeClass.canonicalName))?.containingFile?.let(sourceFiles::add)
            config.actionClass?.let { actionClass ->
                resolver.getClassDeclarationByName(resolver.getKSNameFromString(actionClass.canonicalName))?.containingFile?.let(sourceFiles::add)
            }
        }
        fileSpec.writeTo(codeGenerator, Dependencies(false, *sourceFiles.toTypedArray()))
    }

    private fun buildFieldMetadata(analysis: ClassAnalysis): CodeBlock {
        if (analysis.metadata.isEmpty()) return CodeBlock.of("emptyMap()")
        val code = CodeBlock.builder().add("mapOf(\n").indent()
        analysis.metadata.entries.forEachIndexed { index, entry ->
            val name = entry.key
            val metadata = entry.value
            if (index > 0) code.add(",\n")
            code.add("%S to %T(\n", name, schemaFieldMetadataClass)
            code.indent()
            code.add("renamedFrom = %L,\n", metadata.renamedFrom?.let { CodeBlock.of("%S", it) } ?: CodeBlock.of("null"))
            code.add("renamedSinceVersion = %L,\n", metadata.renamedSinceVersion?.toString() ?: "null")
            code.add("addedInVersion = %L,\n", metadata.addedInVersion?.toString() ?: "null")
            code.add("deprecated = %L,\n", metadata.deprecated)
            code.add("deprecationMessage = %L,\n", metadata.deprecationMessage?.let { CodeBlock.of("%S", it) } ?: CodeBlock.of("null"))
            code.add("removeInVersion = %L,\n", metadata.removeInVersion?.toString() ?: "null")
            code.unindent().add(")")
        }
        code.unindent().add(")")
        return code.build()
    }

    private fun buildCategoryTitleKeys(analysis: ClassAnalysis): CodeBlock {
        val entries = analysis.categoryTitleKeys
        if (entries.isEmpty()) return CodeBlock.of("emptyMap()")
        val code = CodeBlock.builder().add("mapOf(\n").indent()
        var index = 0
        for ((className, key) in entries) {
            if (index++ > 0) code.add(",\n")
            code.add("%T::class to %S", className, key)
        }
        code.unindent().add(")")
        return code.build()
    }

    private fun buildCategoryTitleResources(analysis: ClassAnalysis): CodeBlock {
        val entries = analysis.categoryTitleResources
        if (entries.isEmpty()) return CodeBlock.of("emptyMap()")
        val code = CodeBlock.builder().add("mapOf(\n").indent()
        var index = 0
        for ((className, resource) in entries) {
            if (index++ > 0) code.add(",\n")
            code.add("%T::class to %L", className, resource)
        }
        code.unindent().add(")")
        return code.build()
    }

    private fun generateFieldCode(
        plan: FieldPlan,
        config: SettingConfig?,
        modelClass: ClassName,
        modelDeclaration: KSClassDeclaration,
        schemaName: String,
        resolver: Resolver,
    ): CodeBlock {
        val meta = config?.let { buildMetaBlock(plan, it) }
        val fieldCode = when (plan.kind) {
            FieldKind.UNIT, FieldKind.PRIMITIVE -> buildSimpleFieldCode(
                fieldClass = plan.fieldClass,
                modelClass = modelClass,
                propertyName = plan.propertyName,
                keyName = plan.logicalKey,
                meta = meta,
                setterValue = CodeBlock.of("v"),
            )

            FieldKind.COLLECTION -> buildSimpleFieldCode(
                fieldClass = plan.fieldClass,
                modelClass = modelClass,
                propertyName = plan.propertyName,
                keyName = plan.logicalKey,
                meta = meta,
                setterValue = collectionSetterValue(plan.baseType),
            )

            FieldKind.ENUM -> buildEnumFieldCode(plan, modelClass, meta, resolver)

            FieldKind.SERIALIZED -> buildSerializedFieldCode(
                plan = plan,
                modelClass = modelClass,
                meta = meta,
                modelDeclaration = modelDeclaration,
                schemaName = schemaName,
                resolver = resolver,
            )
        }
        val noReset = plan.property.hasAnnotation(NO_RESET_ANNOTATION)
        val resetConfirmation = confirmResetMessage(plan.property)
        if (!noReset && resetConfirmation == null) return fieldCode
        return CodeBlock.builder()
            .add("%T(delegate = %L, isResettable = %L, resetConfirmation = %L)", configuredSettingField, fieldCode, !noReset, resetConfirmation?.let { CodeBlock.of("%S", it) } ?: CodeBlock.of("null"))
            .build()
    }

    private fun buildSimpleFieldCode(
        fieldClass: ClassName,
        modelClass: ClassName,
        propertyName: String,
        keyName: String,
        meta: CodeBlock?,
        setterValue: CodeBlock,
    ): CodeBlock = CodeBlock.builder()
        .add("%T<%T>(\n", fieldClass, modelClass)
        .indent()
        .add("name = %S,\n", propertyName)
        .add("keyName = %S,\n", keyName)
        .add("meta = %L,\n", meta ?: CodeBlock.of("null"))
        .add("getter = { it.%N },\n", propertyName)
        .add("setter = { m, v -> m.copy(%N = %L) },\n", propertyName, setterValue)
        .unindent()
        .add(")")
        .build()

    private fun buildEnumFieldCode(
        plan: FieldPlan,
        modelClass: ClassName,
        meta: CodeBlock?,
        resolver: Resolver,
    ): CodeBlock {
        val enumClass = plan.baseType.toClassNameOrNull()
            ?: error("Cannot generate enum field for '${plan.propertyName}'")
        val fieldClass = if (plan.nullable) nullableEnumField else enumField
        val code = CodeBlock.builder()
            .add("%T<%T, %T>(\n", fieldClass, modelClass, enumClass)
            .indent()
            .add("name = %S,\n", plan.propertyName)
            .add("keyName = %S,\n", plan.logicalKey)
            .add("meta = %L,\n", meta ?: CodeBlock.of("null"))
            .add("getter = { it.%N },\n", plan.propertyName)
            .add("setter = { m, v -> m.copy(%N = v) },\n", plan.propertyName)
            .add("enumValues = %T.values(),\n", enumClass)
        if (!plan.nullable) code.add("defaultValue = %T().%N,\n", modelClass, plan.propertyName)
        return code.unindent().add(")").build()
    }

    private fun buildSerializedFieldCode(
        plan: FieldPlan,
        modelClass: ClassName,
        meta: CodeBlock?,
        modelDeclaration: KSClassDeclaration,
        schemaName: String,
        resolver: Resolver,
    ): CodeBlock {
        val valueType = plan.baseType.generatedTypeName()
        val fieldClass = if (plan.nullable) nullableSerializedField else serializedField
        val serializer = plan.serializer?.let { spec ->
            buildCustomKSerializer(
                spec = spec,
                valueType = valueType,
                descriptorName = "${canonicalName(modelDeclaration)}.${plan.propertyName}.${spec.className.canonicalName}",
            )
        } ?: CodeBlock.of("%M<%T>()", serializerFunction, valueType)
        val code = CodeBlock.builder()
            .add("%T<%T, %T>(\n", fieldClass, modelClass, valueType)
            .indent()
            .add("name = %S,\n", plan.propertyName)
            .add("keyName = %S,\n", plan.logicalKey)
            .add("meta = %L,\n", meta ?: CodeBlock.of("null"))
            .add("getter = { it.%N },\n", plan.propertyName)
            .add("setter = { m, v -> m.copy(%N = v) },\n", plan.propertyName)
            .add("serializer = %L,\n", serializer)
        if (!plan.nullable) code.add("defaultValue = %T().%N,\n", modelClass, plan.propertyName)
        return code.unindent().add(")").build()
    }

    private fun buildCustomKSerializer(
        spec: CustomSerializerSpec,
        valueType: TypeName,
        descriptorName: String,
    ): CodeBlock {
        val safeDescriptorName = descriptorName.ifBlank { "settings.Custom" }
        return CodeBlock.builder()
            .add("object : %T<%T> {\n", kSerializerClass, valueType)
            .indent()
            .add("override val descriptor: %T = %M(%S, %T.%L)\n", serialDescriptorClass, primitiveSerialDescriptor, safeDescriptorName, primitiveKindClass, "STRING")
            .add("override fun serialize(encoder: %T, value: %T) {\n", encoderClass, valueType)
            .indent()
            .add("encoder.encodeString(%L.serialize(value))\n", spec.instance)
            .unindent()
            .add("}\n")
            .add("override fun deserialize(decoder: %T): %T {\n", decoderClass, valueType)
            .indent()
            .add("return %L.deserialize(decoder.decodeString())\n", spec.instance)
            .unindent()
            .add("}\n")
            .unindent()
            .add("}")
            .build()
    }

    private fun buildMetaBlock(plan: FieldPlan, config: SettingConfig): CodeBlock = CodeBlock.builder()
        .add("%T(\n", ClassName(corePackage, "SettingMeta"))
        .indent()
        .add("title = %S,\n", config.title)
        .add("description = %S,\n", config.description)
        .add("titleRes = %L,\n", config.titleRes)
        .add("descriptionRes = %L,\n", config.descriptionRes)
        .add("titleKey = %S,\n", config.titleKey)
        .add("descriptionKey = %S,\n", config.descriptionKey)
        .add("category = %T::class,\n", config.categoryClass)
        .add("categoryOrder = %L,\n", config.categoryOrder)
        .add("type = %T::class,\n", config.typeClass)
        .add("valueKind = %T.%L,\n", valueKindClass, plan.valueKind)
        .add("enumTypeName = %L,\n", plan.enumTypeName?.let { CodeBlock.of("%S", it) } ?: CodeBlock.of("null"))
        .add("key = %S,\n", config.key)
        .add("dependsOn = %S,\n", config.dependsOn)
        .add("min = %Lf,\n", config.min)
        .add("max = %Lf,\n", config.max)
        .add("step = %Lf,\n", config.step)
        .add("options = listOf(")
        .apply {
            config.options.forEachIndexed { index, option ->
                if (index > 0) add(", ")
                add("%S", option)
            }
        }
        .add("),\n")
        .add("optionsRes = %L,\n", config.optionsRes)
        .add("optionsKey = %S,\n", config.optionsKey)
        .add("confirmResetKey = %S,\n", confirmResetKey(plan.property))
        .add("actionClass = %L,\n", config.actionClass?.let { CodeBlock.of("%T::class", it) } ?: CodeBlock.of("null"))
        .add("validation = %L,\n", buildValidationBlock(plan.property, config.validationMessage, plan.validator))
        .add("confirmation = %L,\n", buildConfirmationBlock(plan.property))
        .add("noReset = %L,\n", plan.property.hasAnnotation(NO_RESET_ANNOTATION))
        .add("confirmReset = %L,\n", confirmResetMessage(plan.property)?.let { CodeBlock.of("%S", it) } ?: CodeBlock.of("null"))
        .add("platforms = setOf(")
        .apply {
            if (config.platforms.isEmpty()) {
                add("%T.ALL", settingPlatformClass)
            } else {
                config.platforms.forEachIndexed { index, platform ->
                    if (index > 0) add(", ")
                    add("%T.%L", settingPlatformClass, platform)
                }
            }
        }
        .add("),\n")
        .unindent()
        .add(")")
        .build()

    private fun buildValidationBlock(
        property: KSPropertyDeclaration,
        message: ValidationMessage?,
        validator: CustomValidatorSpec?,
    ): CodeBlock {
        val hasRange = property.hasAnnotation(RANGE_ANNOTATION)
        val hasLength = property.hasAnnotation(LENGTH_ANNOTATION)
        val hasPattern = property.hasAnnotation(PATTERN_ANNOTATION)
        val hasRequired = property.hasAnnotation(REQUIRED_ANNOTATION)
        if (!hasRange && !hasLength && !hasPattern && !hasRequired && validator == null) return CodeBlock.of("null")
        val code = CodeBlock.builder().add("%T(\n", validationRules).indent()
        val range = property.getAnnotation(RANGE_ANNOTATION)
        val rangeMin = range?.argument("min")?.doubleValue() ?: -Double.MAX_VALUE
        val rangeMax = range?.argument("max")?.doubleValue() ?: Double.MAX_VALUE
        if (hasRange) code.add("range = %L..%L,\n", rangeMin, rangeMax) else code.add("range = null,\n")
        val length = property.getAnnotation(LENGTH_ANNOTATION)
        val lengthMin = length?.argument("min")?.intValue() ?: 0
        val lengthMax = length?.argument("max")?.intValue() ?: Int.MAX_VALUE
        if (hasLength) code.add("length = %L..%L,\n", lengthMin, lengthMax) else code.add("length = null,\n")
        val pattern = property.getAnnotation(PATTERN_ANNOTATION)
        val regex = pattern?.argument("regex")?.stringValue() ?: ".*"
        if (hasPattern) code.add("pattern = Regex(%S),\n", regex) else code.add("pattern = null,\n")
        code.add("required = %L,\n", hasRequired)
        code.add("errorMessage = %S,\n", message?.text ?: "Value out of range")
        code.add("errorMessageRes = %L,\n", message?.resource ?: 0)
        code.add("errorMessageKey = %S,\n", message?.key ?: "")
        if (validator == null) {
            code.add("customValidators = emptyList(),\n")
        } else {
            code.add("customValidators = listOf(%L),\n", validator.instance)
        }
        return code.unindent().add(")").build()
    }

    private fun buildConfirmationBlock(property: KSPropertyDeclaration): CodeBlock {
        val annotation = property.getAnnotation(REQUIRES_CONFIRMATION_ANNOTATION) ?: return CodeBlock.of("null")
        val title = annotation.argument("title")?.stringValue() ?: "Confirm Change"
        val message = annotation.argument("message")?.stringValue() ?: "Are you sure you want to change this setting?"
        val titleRes = annotation.argument("titleRes")?.intValue() ?: 0
        val titleKey = effectiveLocalizationKey(
            key = annotation.argument("titleKey")?.stringValue().orEmpty(),
            resId = titleRes,
            text = title,
            defaultText = "Confirm Change",
            defaultKey = "settings_confirm_change",
        )
        val messageRes = annotation.argument("messageRes")?.intValue() ?: 0
        val messageKey = effectiveLocalizationKey(
            key = annotation.argument("messageKey")?.stringValue().orEmpty(),
            resId = messageRes,
            text = message,
            defaultText = "Are you sure you want to change this setting?",
            defaultKey = "settings_confirm_change_message",
        )
        val confirmText = annotation.argument("confirmText")?.stringValue() ?: "Confirm"
        val confirmTextRes = annotation.argument("confirmTextRes")?.intValue() ?: 0
        val confirmTextKey = effectiveLocalizationKey(
            key = annotation.argument("confirmTextKey")?.stringValue().orEmpty(),
            resId = confirmTextRes,
            text = confirmText,
            defaultText = "Confirm",
            defaultKey = "settings_confirm",
        )
        val cancelText = annotation.argument("cancelText")?.stringValue() ?: "Cancel"
        val cancelTextRes = annotation.argument("cancelTextRes")?.intValue() ?: 0
        val cancelTextKey = effectiveLocalizationKey(
            key = annotation.argument("cancelTextKey")?.stringValue().orEmpty(),
            resId = cancelTextRes,
            text = cancelText,
            defaultText = "Cancel",
            defaultKey = "settings_cancel",
        )
        val dangerous = annotation.argument("isDangerous")?.booleanValue() ?: false
        return CodeBlock.builder()
            .add("%T(\n", confirmationConfig)
            .indent()
            .add("title = %S,\n", title)
            .add("message = %S,\n", message)
            .add("titleRes = %L,\n", titleRes)
            .add("titleKey = %S,\n", titleKey)
            .add("messageRes = %L,\n", messageRes)
            .add("messageKey = %S,\n", messageKey)
            .add("confirmText = %S,\n", confirmText)
            .add("confirmTextRes = %L,\n", confirmTextRes)
            .add("confirmTextKey = %S,\n", confirmTextKey)
            .add("cancelText = %S,\n", cancelText)
            .add("cancelTextRes = %L,\n", cancelTextRes)
            .add("cancelTextKey = %S,\n", cancelTextKey)
            .add("isDangerous = %L,\n", dangerous)
            .unindent()
            .add(")")
            .build()
    }

    private fun confirmResetMessage(property: KSPropertyDeclaration): String? {
        val annotation = property.getAnnotation(CONFIRM_RESET_ANNOTATION) ?: return null
        return annotation.argument("message")?.stringValue() ?: "Are you sure you want to reset this setting?"
    }

    private fun confirmResetKey(property: KSPropertyDeclaration): String {
        val annotation = property.getAnnotation(CONFIRM_RESET_ANNOTATION) ?: return ""
        val message = annotation.argument("message")?.stringValue() ?: "Are you sure you want to reset this setting?"
        val key = annotation.argument("messageKey")?.stringValue().orEmpty()
        return if (key.isBlank() && message == "Are you sure you want to reset this setting?") {
            "settings_confirm_reset_message"
        } else {
            key
        }
    }

    private fun collectionSetterValue(type: KSType): CodeBlock = when (type.declaration.qualifiedName?.asString()) {
        "kotlin.collections.MutableList" -> CodeBlock.of("v.toMutableList()")
        "kotlin.collections.ArrayList" -> CodeBlock.of("%T(v)", ClassName("kotlin.collections", "ArrayList"))
        "kotlin.collections.MutableSet" -> CodeBlock.of("v.toMutableSet()")
        "kotlin.collections.LinkedHashSet" -> CodeBlock.of("%T(v)", ClassName("kotlin.collections", "LinkedHashSet"))
        "kotlin.collections.HashSet" -> CodeBlock.of("%T(v)", ClassName("kotlin.collections", "HashSet"))
        "kotlin.collections.MutableMap" -> CodeBlock.of("v.toMutableMap()")
        "kotlin.collections.LinkedHashMap" -> CodeBlock.of("%T(v)", ClassName("kotlin.collections", "LinkedHashMap"))
        "kotlin.collections.HashMap" -> CodeBlock.of("%T(v)", ClassName("kotlin.collections", "HashMap"))
        else -> CodeBlock.of("v")
    }

    private fun physicalKeysFor(
        key: String,
        kind: FieldKind,
        baseName: String?,
        nullable: Boolean,
        fieldClass: ClassName?,
    ): List<String> {
        if (kind == FieldKind.UNIT) return emptyList()
        val storageKind = when (kind) {
            FieldKind.PRIMITIVE -> when (baseName) {
                "kotlin.Boolean" -> if (nullable) "nullable_boolean" else "boolean"
                "kotlin.Int" -> if (nullable) "nullable_int" else "int"
                "kotlin.Long" -> if (nullable) "nullable_long" else "long"
                "kotlin.Float" -> if (nullable) "nullable_float" else "float"
                "kotlin.Double" -> if (nullable) "nullable_double" else "double"
                "kotlin.String" -> if (nullable) "nullable_string" else "string"
                else -> error("Unknown primitive kind '$baseName'")
            }
            FieldKind.ENUM -> if (nullable) "nullable_enum" else "enum"
            FieldKind.SERIALIZED -> if (nullable) "nullable_serialized" else "serialized"
            FieldKind.COLLECTION -> when (fieldClass?.simpleName) {
                "StringListField" -> "string_list"
                "IntListField" -> "int_list"
                "LongListField" -> "long_list"
                "StringSetField" -> "string_set"
                "StringMapField", "StringLongMapField", "StringIntMapField", "StringFloatMapField", "StringDoubleMapField", "StringBooleanMapField",
                "IntStringMapField", "IntIntMapField", "IntLongMapField", "LongStringMapField", "LongLongMapField", "LongIntMapField" -> "map"
                else -> error("Unknown collection field '${fieldClass?.simpleName}'")
            }
            FieldKind.UNIT -> return emptyList()
        }
        val canonical = "__kmp_settings_v2__:$storageKind:${key.length}:$key"
        val legacyVariants = if (nullable) {
            when (fieldClass?.simpleName) {
                "NullableLongField" -> listOf(key, "${key}_nullable_long")
                "NullableBooleanField", "NullableIntField", "NullableFloatField", "NullableDoubleField", "NullableStringField" -> listOf(key, "${key}_nullable")
                else -> listOf(key)
            }
        } else {
            listOf(key)
        }
        if (!nullable) return (listOf(canonical) + legacyVariants).distinct()
        val marker = "__kmp_settings_v2__:null:$storageKind:${key.length}:$key"
        return (listOf(canonical, marker) + legacyVariants).distinct()
    }

    private fun nativeCollectionField(type: KSType, nullable: Boolean): ClassName? {
        if (nullable) return null
        val qname = type.declaration.qualifiedName?.asString()
        val args = type.arguments
        if (args.any { it.variance != Variance.INVARIANT }) return null
        val first = args.getOrNull(0)?.type?.resolve()?.let(::resolveTypeForInspection)
        val second = args.getOrNull(1)?.type?.resolve()?.let(::resolveTypeForInspection)
        if (args.isNotEmpty() && (first == null || first.isMarkedNullable)) return null
        if (args.size > 1 && second?.isMarkedNullable == true) return null
        return when (qname) {
            "kotlin.collections.Set", "kotlin.collections.MutableSet", "kotlin.collections.LinkedHashSet", "kotlin.collections.HashSet" -> {
                if (first?.declaration?.qualifiedName?.asString() == "kotlin.String") stringSetField else null
            }
            "kotlin.collections.List", "kotlin.collections.MutableList", "kotlin.collections.ArrayList" -> {
                when (first?.declaration?.qualifiedName?.asString()) {
                    "kotlin.String" -> stringListField
                    "kotlin.Int" -> intListField
                    "kotlin.Long" -> longListField
                    else -> null
                }
            }
            "kotlin.collections.Map", "kotlin.collections.MutableMap", "kotlin.collections.LinkedHashMap", "kotlin.collections.HashMap" -> {
                if (args.size != 2) null else getMapFieldClass(
                    first?.declaration?.qualifiedName?.asString(),
                    second?.declaration?.qualifiedName?.asString(),
                )
            }
            else -> null
        }
    }

    private fun primitiveField(typeName: String?, nullable: Boolean): ClassName? = if (nullable) {
        when (typeName) {
            "kotlin.Boolean" -> nullableBooleanField
            "kotlin.Int" -> nullableIntField
            "kotlin.Long" -> nullableLongField
            "kotlin.Float" -> nullableFloatField
            "kotlin.Double" -> nullableDoubleField
            "kotlin.String" -> nullableStringField
            else -> null
        }
    } else {
        when (typeName) {
            "kotlin.Boolean" -> booleanField
            "kotlin.Int" -> intField
            "kotlin.Long" -> longField
            "kotlin.Float" -> floatField
            "kotlin.Double" -> doubleField
            "kotlin.String" -> stringField
            else -> null
        }
    }

    private fun getMapFieldClass(keyType: String?, valueType: String?): ClassName? = when (keyType) {
        "kotlin.String" -> when (valueType) {
            "kotlin.String" -> stringMapField
            "kotlin.Int" -> stringIntMapField
            "kotlin.Long" -> stringLongMapField
            "kotlin.Float" -> stringFloatMapField
            "kotlin.Double" -> stringDoubleMapField
            "kotlin.Boolean" -> stringBooleanMapField
            else -> null
        }
        "kotlin.Int" -> when (valueType) {
            "kotlin.String" -> intStringMapField
            "kotlin.Int" -> intIntMapField
            "kotlin.Long" -> intLongMapField
            else -> null
        }
        "kotlin.Long" -> when (valueType) {
            "kotlin.String" -> longStringMapField
            "kotlin.Long" -> longLongMapField
            "kotlin.Int" -> longIntMapField
            else -> null
        }
        else -> null
    }

    private fun isSerializableType(type: KSType, resolver: Resolver, seen: MutableSet<String>): Boolean {
        if (type.isError || type.declaration is KSTypeParameter) return false
        val name = type.declaration.qualifiedName?.asString() ?: return false
        if (name in setOf(
                "kotlin.Boolean", "kotlin.Byte", "kotlin.Short", "kotlin.Int", "kotlin.Long",
                "kotlin.Float", "kotlin.Double", "kotlin.Char", "kotlin.String",
            )) return true
        if (!seen.add(name)) return true
        try {
            if (isCollectionType(name)) {
                return type.arguments.all { argument ->
                    val resolved = argument.type?.resolve() ?: return@all false
                    isSerializableType(resolveTypeForInspection(resolved), resolver, seen)
                }
            }
            if (type.declaration.annotations.any { it.annotationQName() == KOTLINX_SERIALIZABLE }) return true
            return false
        } finally {
            seen.remove(name)
        }
    }

    private fun resolveType(type: KSType, diagnostics: Diagnostics, symbol: KSNode): KSType? {
        var current = type
        val seen = mutableSetOf<String>()
        while (true) {
            val alias = current.declaration as? KSTypeAlias ?: return current
            val id = canonicalName(alias)
            if (!seen.add(id)) {
                diagnostics.error("Cyclic type alias while resolving '${symbol.toString()}'", symbol)
                return null
            }
            if (alias.typeParameters.isNotEmpty()) {
                diagnostics.error("Generic type aliases are not supported in settings properties", symbol)
                return null
            }
            val underlying = runCatching { alias.type.resolve() }.getOrNull() ?: return null
            current = if (current.isMarkedNullable && !underlying.isMarkedNullable) underlying.makeNullable() else underlying
        }
    }

    private fun resolveTypeForInspection(type: KSType): KSType {
        var current = type
        val seen = mutableSetOf<String>()
        while (true) {
            val declaration = current.declaration
            if (declaration !is KSTypeAlias || declaration.typeParameters.isNotEmpty()) break
            val id = canonicalName(declaration)
            if (!seen.add(id)) break
            val next = runCatching { declaration.type.resolve() }.getOrNull() ?: break
            current = if (current.isMarkedNullable && !next.isMarkedNullable) next.makeNullable() else next
        }
        return current
    }

    private fun hasInvalidTypeArguments(type: KSType): Boolean = type.arguments.any { argument ->
        argument.variance == Variance.STAR || argument.type == null || containsErrorType(argument.type!!.resolve())
    }

    private fun isCollectionType(qname: String?): Boolean = qname in setOf(
        "kotlin.collections.List",
        "kotlin.collections.MutableList",
        "kotlin.collections.ArrayList",
        "kotlin.collections.Set",
        "kotlin.collections.MutableSet",
        "kotlin.collections.LinkedHashSet",
        "kotlin.collections.HashSet",
        "kotlin.collections.Map",
        "kotlin.collections.MutableMap",
        "kotlin.collections.LinkedHashMap",
        "kotlin.collections.HashMap",
    )

    private fun isPrimitiveType(qname: String?): Boolean = qname in setOf(
        "kotlin.Boolean", "kotlin.Int", "kotlin.Long", "kotlin.Float", "kotlin.Double", "kotlin.String",
    )

    private fun isEnumType(type: KSType): Boolean =
        (type.declaration as? KSClassDeclaration)?.classKind == ClassKind.ENUM_CLASS

    private fun valueKindFor(type: KSType): Pair<String, String?> {
        val qname = type.declaration.qualifiedName?.asString()
        return when (qname) {
            "kotlin.Boolean" -> "BOOLEAN" to null
            "kotlin.Int" -> "INT" to null
            "kotlin.Long" -> "LONG" to null
            "kotlin.Float" -> "FLOAT" to null
            "kotlin.Double" -> "DOUBLE" to null
            "kotlin.String" -> "STRING" to null
            else -> if (isEnumType(type)) {
                "ENUM" to type.toClassNameOrNull()?.canonicalName
            } else {
                "NONE" to null
            }
        }
    }

    private fun uiKind(typeClass: ClassName): UiKind = when (typeClass.canonicalName) {
        "$typesPackage.Toggle" -> UiKind.TOGGLE
        "$typesPackage.Dropdown" -> UiKind.DROPDOWN
        "$typesPackage.Slider" -> UiKind.SLIDER
        "$typesPackage.Button" -> UiKind.BUTTON
        "$typesPackage.TextInput" -> UiKind.TEXT_INPUT
        "$typesPackage.TimePickerType" -> UiKind.TIME_PICKER
        else -> UiKind.CUSTOM
    }

    private fun validateStringKey(
        value: String,
        field: String,
        propertyName: String,
        symbol: KSNode,
        diagnostics: Diagnostics,
    ) {
        if (value.isBlank()) return
        if (value != value.trim() || value.any { it.isWhitespace() }) {
            diagnostics.error("$field must not contain whitespace or surrounding padding ('$propertyName')", symbol)
        }
    }

    private fun keyFor(property: KSPropertyDeclaration, kind: AnnotationKind): String {
        val annotation = property.getAnnotation(
            if (kind == AnnotationKind.SETTING) SETTING_ANNOTATION else PERSISTED_ANNOTATION,
        ) ?: return toSnakeCase(property.simpleName.asString())
        val explicit = annotation.argument("key")?.stringValue().orEmpty()
        return explicit.ifBlank { toSnakeCase(property.simpleName.asString()) }
    }

    private fun toSnakeCase(value: String): String = buildString {
        value.forEachIndexed { index, character ->
            if (character.isUpperCase() && index != 0) append('_')
            append(character.lowercaseChar())
        }
    }

    private fun KSPropertyDeclaration.hasAnnotation(fqcn: String): Boolean = getAnnotation(fqcn) != null

    private fun KSAnnotated.getAnnotation(fqcn: String): KSAnnotation? = annotations.firstOrNull {
        it.annotationQName() == fqcn
    }

    private fun KSAnnotation.argument(name: String): KSValueArgument? = arguments.firstOrNull {
        it.name?.asString() == name
    }

    private fun KSAnnotation.annotationQName(): String? = runCatching {
        annotationType.resolve().declaration.qualifiedName?.asString()
    }.getOrNull()

    private fun KSValueArgument.intValue(): Int? = when (val value = value) {
        is Int -> value
        is Number -> value.toInt()
        else -> null
    }

    private fun KSValueArgument.floatValue(): Float? = when (val value = value) {
        is Float -> value
        is Number -> value.toFloat()
        else -> null
    }

    private fun KSValueArgument.doubleValue(): Double? = when (val value = value) {
        is Double -> value
        is Number -> value.toDouble()
        else -> null
    }

    private fun KSValueArgument.stringValue(): String? = value as? String

    private fun KSValueArgument.booleanValue(): Boolean? = value as? Boolean

    private fun stringListArgument(
        annotation: KSAnnotation,
        name: String,
        diagnostics: Diagnostics,
        propertyName: String,
    ): List<String> {
        val argument = annotation.argument(name) ?: return emptyList()
        val values = when (val value = argument.value) {
            is List<*> -> value
            is Array<*> -> value.toList()
            else -> null
        }
        if (values == null) {
            diagnostics.error("@Setting $name must be an array of strings ('$propertyName')")
            return emptyList()
        }
        val result = mutableListOf<String>()
        for (value in values) {
            if (value !is String) {
                diagnostics.error("@Setting $name must contain only strings ('$propertyName')")
                return emptyList()
            }
            result += value
        }
        return result
    }

    private fun platformListArgument(
        annotation: KSAnnotation,
        propertyName: String,
        diagnostics: Diagnostics,
    ): List<String> {
        val argument = annotation.argument("platforms") ?: return listOf("ALL")
        val values = when (val value = argument.value) {
            is List<*> -> value
            is Array<*> -> value.toList()
            else -> null
        }
        if (values == null) {
            diagnostics.error("@Setting platforms must be an array of SettingPlatform ('$propertyName')")
            return listOf("ALL")
        }
        val valid = setOf("ALL", "ANDROID", "IOS", "DESKTOP", "JVM", "LINUX", "WEB")
        val result = mutableListOf<String>()
        for (value in values) {
            val name = when (value) {
                is KSType -> value.declaration.simpleName.asString().takeIf {
                    value.declaration.parentDeclaration?.qualifiedName?.asString() == annotationsPackage + ".SettingPlatform"
                }
                is KSClassDeclaration -> value.simpleName.asString().takeIf {
                    value.parentDeclaration?.qualifiedName?.asString() == annotationsPackage + ".SettingPlatform"
                }
                else -> value?.toString()?.substringAfterLast('.')
            }
            if (name == null || name !in valid) {
                diagnostics.error("Unknown platform '$value' on '$propertyName'")
            } else if (name !in result) {
                result += name
            }
        }
        return result.ifEmpty { listOf("ALL") }
    }

    private fun isPrimaryConstructorProperty(property: KSPropertyDeclaration): Boolean {
        val parent = property.parentDeclaration as? KSClassDeclaration ?: return false
        return parent.primaryConstructor?.parameters.orEmpty().any {
            it.name?.asString() == property.simpleName.asString()
        }
    }

    private fun isAccessible(declaration: KSDeclaration): Boolean =
        Modifier.PRIVATE !in declaration.modifiers && Modifier.PROTECTED !in declaration.modifiers

    private fun isAccessibleProperty(property: KSPropertyDeclaration): Boolean {
        if (!isAccessible(property)) return false
        val getterVisibility = property.getter?.modifiers.orEmpty()
        return Modifier.PRIVATE !in getterVisibility && Modifier.PROTECTED !in getterVisibility
    }

    private fun hasInaccessibleParent(klass: KSClassDeclaration): Boolean {
        var parent = klass.parentDeclaration
        while (parent is KSClassDeclaration) {
            if (!isAccessible(parent)) return true
            parent = parent.parentDeclaration
        }
        return false
    }

    private fun hasGenericParent(klass: KSClassDeclaration): Boolean {
        var parent = klass.parentDeclaration
        while (parent is KSClassDeclaration) {
            if (parent.typeParameters.isNotEmpty()) return true
            parent = parent.parentDeclaration
        }
        return false
    }

    private fun effectiveVisibility(klass: KSClassDeclaration): Modifier =
        if (Modifier.INTERNAL in klass.modifiers || hasInternalParent(klass) || hasGenericParent(klass)) Modifier.INTERNAL else Modifier.PUBLIC

    private fun hasInternalParent(klass: KSClassDeclaration): Boolean {
        var parent = klass.parentDeclaration
        while (parent is KSClassDeclaration) {
            if (Modifier.INTERNAL in parent.modifiers) return true
            parent = parent.parentDeclaration
        }
        return false
    }

    private fun classId(klass: KSClassDeclaration): String = canonicalName(klass)

    private fun canonicalName(declaration: KSDeclaration): String {
        val names = mutableListOf<String>()
        var current: KSDeclaration? = declaration
        while (current != null && current !is KSFile) {
            names.add(0, current.simpleName.asString())
            current = current.parentDeclaration
        }
        val packageName = declaration.packageName.asString()
        return if (packageName.isBlank()) names.joinToString(".") else "${packageName}.${names.joinToString(".")}"
    }

    private fun KSClassDeclaration.toClassNameCompat(): ClassName {
        val names = mutableListOf<String>()
        var current: KSDeclaration? = this
        while (current != null && current !is KSFile) {
            names.add(0, current.simpleName.asString())
            current = current.parentDeclaration
        }
        return ClassName(packageName.asString(), names)
    }

    private fun KSType.toClassNameOrNull(): ClassName? = runCatching { toClassName() }.getOrNull()

    private fun KSType.generatedTypeName(): TypeName = this.ksToTypeName(TypeParameterResolver.EMPTY)

    private fun propertyKey(property: KSPropertyDeclaration): String {
        val parent = property.parentDeclaration
        val prefix = if (parent is KSDeclaration) canonicalName(parent) else property.toString()
        return "$prefix#${property.simpleName.asString()}"
    }

    private fun schemaNameFor(klass: KSClassDeclaration): String? {
        val id = classId(klass)
        schemaNames[id]?.let { return it }
        val chain = mutableListOf<String>()
        var current: KSDeclaration? = klass
        while (current != null && current !is KSFile) {
            chain.add(0, current.simpleName.asString())
            current = current.parentDeclaration
        }
        val base = sanitizeIdentifier(chain.joinToString("_") + "Schema")
        var candidate = base
        fun key(name: String): String = "${klass.packageName.asString()}:$name"
        if (usedSchemaNames.containsKey(key(candidate)) && usedSchemaNames[key(candidate)] != id) {
            candidate = "${base}_${stableSuffix(id)}"
        }
        var index = 2
        while (usedSchemaNames.containsKey(key(candidate)) && usedSchemaNames[key(candidate)] != id) {
            candidate = "${base}_${stableSuffix(id)}_$index"
            index++
        }
        usedSchemaNames[key(candidate)] = id
        schemaNames[id] = candidate
        return candidate
    }

    private fun sanitizeIdentifier(value: String): String {
        val builder = StringBuilder()
        for (character in value) {
            if (character.isLetterOrDigit() || character == '_') builder.append(character)
            else builder.append('_')
        }
        if (builder.isEmpty() || builder.first().isDigit()) builder.insert(0, '_')
        return builder.toString()
    }

    private fun stableSuffix(value: String): String = value.hashCode().toUInt().toString(16)

    @OptIn(KspExperimental::class)
    private fun schemaNameConflictsWithSource(
        resolver: Resolver,
        klass: KSClassDeclaration,
        schemaName: String,
    ): Boolean {
        val packageName = klass.packageName.asString()
        val qname = if (packageName.isBlank()) schemaName else "$packageName.$schemaName"
        val existing = runCatching { resolver.getClassDeclarationByName(resolver.getKSNameFromString(qname)) }.getOrNull()
        if (existing != null && canonicalName(existing) != classId(klass)) return true
        return resolver.getDeclarationsFromPackage(packageName)
            .filter { it.simpleName.asString() == schemaName }
            .any { it !is KSClassDeclaration || canonicalName(it) != classId(klass) }
    }

}

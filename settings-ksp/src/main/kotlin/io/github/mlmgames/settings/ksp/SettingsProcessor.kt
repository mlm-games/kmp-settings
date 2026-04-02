package io.github.mlmgames.settings.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

class SettingsProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    companion object {
        private const val SETTING_ANNOTATION = "io.github.mlmgames.settings.core.annotations.Setting"
        private const val PERSISTED_ANNOTATION = "io.github.mlmgames.settings.core.annotations.Persisted"
        private const val SERIALIZED_ANNOTATION = "io.github.mlmgames.settings.core.annotations.Serialized"
        private const val CATEGORY_DEF_ANNOTATION = "io.github.mlmgames.settings.core.annotations.CategoryDefinition"
        private const val ACTION_HANDLER_ANNOTATION = "io.github.mlmgames.settings.core.annotations.ActionHandler"
        private const val RANGE_ANNOTATION = "io.github.mlmgames.settings.core.annotations.Range"
        private const val LENGTH_ANNOTATION = "io.github.mlmgames.settings.core.annotations.Length"
        private const val PATTERN_ANNOTATION = "io.github.mlmgames.settings.core.annotations.Pattern"
        private const val REQUIRED_ANNOTATION = "io.github.mlmgames.settings.core.annotations.Required"
        private const val REQUIRES_CONFIRMATION_ANNOTATION = "io.github.mlmgames.settings.core.annotations.RequiresConfirmation"
        private const val NO_RESET_ANNOTATION = "io.github.mlmgames.settings.core.annotations.NoReset"
        private const val CONFIRM_RESET_ANNOTATION = "io.github.mlmgames.settings.core.annotations.ConfirmReset"
        private const val KOTLINX_SERIALIZABLE = "kotlinx.serialization.Serializable"
    }

    private val corePackage = "io.github.mlmgames.settings.core"
    private val fieldsPackage = "$corePackage.fields"
    private val settingPlatformClass = ClassName("io.github.mlmgames.settings.core.annotations", "SettingPlatform")
    private val valueKindClass = ClassName(corePackage, "ValueKind")

    // Core types
    private val settingsSchema = ClassName(corePackage, "SettingsSchema")
    private val settingMeta = ClassName(corePackage, "SettingMeta")
    private val settingField = ClassName(corePackage, "SettingField")
    private val validationRules = ClassName(corePackage, "ValidationRules")
    private val confirmationConfig = ClassName(corePackage, "ConfirmationConfig")

    // Primitive fields
    private val booleanField = ClassName(fieldsPackage, "BooleanField")
    private val intField = ClassName(fieldsPackage, "IntField")
    private val longField = ClassName(fieldsPackage, "LongField")
    private val floatField = ClassName(fieldsPackage, "FloatField")
    private val doubleField = ClassName(fieldsPackage, "DoubleField")
    private val stringField = ClassName(fieldsPackage, "StringField")
    private val stringSetField = ClassName(fieldsPackage, "StringSetField")
    private val unitField = ClassName(fieldsPackage, "UnitField")

    // Nullable fields
    private val nullableBooleanField = ClassName(fieldsPackage, "NullableBooleanField")
    private val nullableIntField = ClassName(fieldsPackage, "NullableIntField")
    private val nullableLongField = ClassName(fieldsPackage, "NullableLongField")
    private val nullableFloatField = ClassName(fieldsPackage, "NullableFloatField")
    private val nullableDoubleField = ClassName(fieldsPackage, "NullableDoubleField")
    private val nullableStringField = ClassName(fieldsPackage, "NullableStringField")

    // Collection fields
    private val stringListField = ClassName(fieldsPackage, "StringListField")
    private val intListField = ClassName(fieldsPackage, "IntListField")
    private val stringMapField = ClassName(fieldsPackage, "StringMapField")
    private val stringLongMapField = ClassName(fieldsPackage, "StringLongMapField")
    private val stringIntMapField = ClassName(fieldsPackage, "StringIntMapField")

    private val stringFloatMapField = ClassName(fieldsPackage, "StringFloatMapField")
    private val stringDoubleMapField = ClassName(fieldsPackage, "StringDoubleMapField")
    private val stringBooleanMapField = ClassName(fieldsPackage, "StringBooleanMapField")
    private val intStringMapField = ClassName(fieldsPackage, "IntStringMapField")
    private val intIntMapField = ClassName(fieldsPackage, "IntIntMapField")
    private val longStringMapField = ClassName(fieldsPackage, "LongStringMapField")
    private val longLongMapField = ClassName(fieldsPackage, "LongLongMapField")


    // Complex fields
    private val serializedField = ClassName(fieldsPackage, "SerializedField")
    private val nullableSerializedField = ClassName(fieldsPackage, "NullableSerializedField")
    private val enumField = ClassName(fieldsPackage, "EnumField")
    private val nullableEnumField = ClassName(fieldsPackage, "NullableEnumField")

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val settingProps = resolver
            .getSymbolsWithAnnotation(SETTING_ANNOTATION)
            .filterIsInstance<KSPropertyDeclaration>()
            .toList()

        val persistedProps = resolver
            .getSymbolsWithAnnotation(PERSISTED_ANNOTATION)
            .filterIsInstance<KSPropertyDeclaration>()
            .toList()

        val allProps = settingProps + persistedProps
        val invalid = allProps.filterNot { it.validate() }

        val byClass = allProps.groupBy { it.parentDeclaration as? KSClassDeclaration }

        for ((klass, props) in byClass) {
            if (klass == null) continue
            generateSchemaForClass(klass, props, resolver)
        }

        return invalid
    }

    private fun generateSchemaForClass(
        klass: KSClassDeclaration,
        allProps: List<KSPropertyDeclaration>,
        resolver: Resolver,
    ) {
        val pkg = klass.packageName.asString()
        val className = klass.simpleName.asString()

        if (Modifier.DATA !in klass.modifiers) {
            logger.error("@Setting/@Persisted can only be used on data class properties.", klass)
            return
        }

        val schemaName = "${className}Schema"
        val modelClass = klass.toClassName()

        val settingProps = allProps.filter { prop ->
            prop.annotations.any {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == SETTING_ANNOTATION
            }
        }
        val persistedProps = allProps.filter { prop ->
            prop.annotations.any {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == PERSISTED_ANNOTATION
            }
        }

        val fieldsCode = CodeBlock.builder()
        fieldsCode.add("listOf(\n")
        fieldsCode.indent()

        for (prop in settingProps) {
            val fieldCode = generateSettingField(prop, modelClass, resolver)
            if (fieldCode != null) {
                fieldsCode.add(fieldCode)
                fieldsCode.add(",\n")
            }
        }

        for (prop in persistedProps) {
            val fieldCode = generatePersistedField(prop, modelClass, resolver)
            if (fieldCode != null) {
                fieldsCode.add(fieldCode)
                fieldsCode.add(",\n")
            }
        }

        fieldsCode.unindent()
        fieldsCode.add(")\n")

        val typeSpec = TypeSpec.objectBuilder(schemaName)
            .addSuperinterface(settingsSchema.parameterizedBy(modelClass))
            .addProperty(
                PropertySpec.builder("default", modelClass)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer("%T()", modelClass)
                    .build()
            )
            .addProperty(
                PropertySpec.builder(
                    "fields",
                    List::class.asClassName().parameterizedBy(
                        settingField.parameterizedBy(modelClass, STAR)
                    )
                )
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer(fieldsCode.build())
                    .build()
            )
            .build()

        val fileSpec = FileSpec.builder(pkg, schemaName)
            .addType(typeSpec)
            .build()

        fileSpec.writeTo(codeGenerator, Dependencies(false, klass.containingFile!!))
    }

    private fun generateSettingField(
        prop: KSPropertyDeclaration,
        modelClass: ClassName,
        resolver: Resolver,
    ): CodeBlock? {
        val propName = prop.simpleName.asString()
        val propType = prop.type.resolve()

        val ann = prop.annotations.first {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == SETTING_ANNOTATION
        }
        val args = ann.arguments.associateBy { it.name?.asString().orEmpty() }

        val hasSerialized = prop.hasAnnotation(SERIALIZED_ANNOTATION)

        // Extract annotation values
        val title = args["title"]?.value as? String ?: ""
        val description = args["description"]?.value as? String ?: ""
        val titleRes = args["titleRes"]?.value as? Int ?: 0
        val descriptionRes = args["descriptionRes"]?.value as? Int ?: 0
        val keyOverride = args["key"]?.value as? String ?: ""
        val dependsOn = args["dependsOn"]?.value as? String ?: ""
        val min = args["min"]?.value as? Float ?: 0f
        val max = args["max"]?.value as? Float ?: 100f
        val step = args["step"]?.value as? Float ?: 1f
        val options = (args["options"]?.value as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val optionsRes = args["optionsRes"]?.value as? Int ?: 0

        val categoryType = args["category"]?.value as? KSType
        val categoryClass = categoryType?.toClassName()
            ?: return null.also { logger.error("Missing category for $propName", prop) }

        val categoryOrder = getCategoryOrder(categoryType)

        val typeType = args["type"]?.value as? KSType
        val typeClass = typeType?.toClassName()
            ?: ClassName("io.github.mlmgames.settings.core.types", "Toggle")

        val keyName = keyOverride.ifBlank { toSnakeCase(propName) }

        // Determine value kind based on property type
        val (valueKindName, enumTypeName) = computeValueKindInfo(propType, resolver)

        // Action handler
        val actionClass = getActionClass(prop)

        // Validation
        val validationBlock = buildValidationBlock(prop)

        // Confirmation
        val confirmationBlock = buildConfirmationBlock(prop)

        // Reset behavior
        val noReset = prop.hasAnnotation(NO_RESET_ANNOTATION)
        val confirmReset = getConfirmResetMessage(prop)

        val platformNames = extractPlatformNames(args["platforms"], propName)

        val metaBlock = buildMetaBlock(
            title,
            description,
            titleRes,
            descriptionRes,
            categoryClass,
            categoryOrder,
            typeClass,
            keyName,
            dependsOn,
            min,
            max,
            step,
            options,
            optionsRes,
            actionClass,
            validationBlock,
            confirmationBlock,
            noReset,
            confirmReset,
            platformNames,
            valueKindName,
            enumTypeName
        )

        return generateFieldCode(prop, propType, modelClass, propName, keyName, metaBlock, hasSerialized, resolver)
    }

    private fun generatePersistedField(
        prop: KSPropertyDeclaration,
        modelClass: ClassName,
        resolver: Resolver,
    ): CodeBlock? {
        val propName = prop.simpleName.asString()
        val propType = prop.type.resolve()

        val ann = prop.annotations.first {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == PERSISTED_ANNOTATION
        }
        val args = ann.arguments.associateBy { it.name?.asString().orEmpty() }

        val hasSerialized = prop.hasAnnotation(SERIALIZED_ANNOTATION)

        val keyOverride = args["key"]?.value as? String ?: ""
        val keyName = keyOverride.ifBlank { toSnakeCase(propName) }

        return generateFieldCode(prop, propType, modelClass, propName, keyName, null, hasSerialized, resolver)
    }

    private fun generateFieldCode(
        prop: KSPropertyDeclaration,
        propType: KSType,
        modelClass: ClassName,
        propName: String,
        keyName: String,
        metaBlock: CodeBlock?,
        hasSerialized: Boolean,
        resolver: Resolver,
    ): CodeBlock? {
        val isNullable = propType.isMarkedNullable
        val baseType = propType.makeNotNullable()
        val qualifiedName = baseType.declaration.qualifiedName?.asString()

        // Check if it's an enum
        val isEnum = (baseType.declaration as? KSClassDeclaration)?.classKind == ClassKind.ENUM_CLASS

        // Check for built-in collection types
        when (qualifiedName) {
            "kotlin.collections.Set" -> {
                val typeArg = propType.arguments.firstOrNull()?.type?.resolve()
                if (typeArg?.declaration?.qualifiedName?.asString() == "kotlin.String") {
                    return buildSimpleFieldCode(stringSetField, modelClass, propName, keyName, metaBlock)
                }
            }

            "kotlin.collections.List" -> {
                val typeArg = propType.arguments.firstOrNull()?.type?.resolve()
                return when (typeArg?.declaration?.qualifiedName?.asString()) {
                    "kotlin.String" -> buildSimpleFieldCode(stringListField, modelClass, propName, keyName, metaBlock)
                    "kotlin.Int" -> buildSimpleFieldCode(intListField, modelClass, propName, keyName, metaBlock)
                    else -> {
                        if (hasSerialized || isSerializable(typeArg, resolver)) {
                            buildSerializedFieldCode(propType, modelClass, propName, keyName, metaBlock, isNullable)
                        } else {
                            logger.error("Unsupported List element type for $propName. Add @Serialized.", prop)
                            null
                        }
                    }
                }
            }

            "kotlin.collections.Map" -> {
                val keyArg = propType.arguments.getOrNull(0)?.type?.resolve()
                val valueArg = propType.arguments.getOrNull(1)?.type?.resolve()
                val keyTypeName = keyArg?.declaration?.qualifiedName?.asString()
                val valueTypeName = valueArg?.declaration?.qualifiedName?.asString()

                val mapFieldClass = getMapFieldClass(keyTypeName, valueTypeName)

                return if (mapFieldClass != null) {
                    buildSimpleFieldCode(mapFieldClass, modelClass, propName, keyName, metaBlock)
                } else if (hasSerialized) {
                    buildSerializedFieldCode(propType, modelClass, propName, keyName, metaBlock, isNullable)
                } else {
                    logger.error("Unsupported Map type for $propName: Map<$keyTypeName, $valueTypeName>. Add @Serialized for complex value types.", prop)
                    null
                }
            }
        }

        // Enum types
        if (isEnum) {
            return buildEnumFieldCode(baseType, modelClass, propName, keyName, metaBlock, isNullable)
        }

        // Primitive types
        val fieldClass = getFieldClass(baseType, isNullable)
        if (fieldClass != null) {
            return buildSimpleFieldCode(fieldClass, modelClass, propName, keyName, metaBlock)
        }

        // Complex types with @Serialized or @Serializable
        if (hasSerialized || isSerializable(baseType, resolver)) {
            return buildSerializedFieldCode(propType, modelClass, propName, keyName, metaBlock, isNullable)
        }

        logger.error("Unsupported type for $propName: $qualifiedName. Consider adding @Serialized.", prop)
        return null
    }

    private fun buildMetaBlock(
        title: String,
        description: String,
        titleRes: Int,
        descriptionRes: Int,
        categoryClass: ClassName,
        categoryOrder: Int,
        typeClass: ClassName,
        keyName: String,
        dependsOn: String,
        min: Float,
        max: Float,
        step: Float,
        options: List<String>,
        optionsRes: Int,
        actionClass: ClassName?,
        validationBlock: CodeBlock?,
        confirmationBlock: CodeBlock?,
        noReset: Boolean,
        confirmReset: String?,
        platformNames: List<String>,
        valueKindName: String,
        enumTypeName: String?,
    ): CodeBlock {
        return CodeBlock.builder()
            .add("%T(\n", settingMeta)
            .indent()
            .add("title = %S,\n", title)
            .add("description = %S,\n", description)
            .add("titleRes = %L,\n", titleRes)
            .add("descriptionRes = %L,\n", descriptionRes)
            .add("category = %T::class,\n", categoryClass)
            .add("categoryOrder = %L,\n", categoryOrder)
            .add("type = %T::class,\n", typeClass)
            .add("valueKind = %T.%L,\n", valueKindClass, valueKindName)
            .apply {
                if (enumTypeName != null) {
                    add("enumTypeName = %S,\n", enumTypeName)
                } else {
                    add("enumTypeName = null,\n")
                }
            }
            .add("key = %S,\n", keyName)
            .add("dependsOn = %S,\n", dependsOn)
            .add("min = %Lf,\n", min)
            .add("max = %Lf,\n", max)
            .add("step = %Lf,\n", step)
            .add("options = listOf(")
            .apply {
                options.forEachIndexed { i, opt ->
                    if (i > 0) add(", ")
                    add("%S", opt)
                }
            }
            .add("),\n")
            .add("optionsRes = %L,\n", optionsRes)
            .apply {
                if (actionClass != null) {
                    add("actionClass = %T::class,\n", actionClass)
                } else {
                    add("actionClass = null,\n")
                }
            }
            .apply {
                if (validationBlock != null) {
                    add("validation = ").add(validationBlock).add(",\n")
                } else {
                    add("validation = null,\n")
                }
            }
            .apply {
                if (confirmationBlock != null) {
                    add("confirmation = ").add(confirmationBlock).add(",\n")
                } else {
                    add("confirmation = null,\n")
                }
            }
            .add("noReset = %L,\n", noReset)
            .apply {
                if (confirmReset != null) {
                    add("confirmReset = %S,\n", confirmReset)
                } else {
                    add("confirmReset = null,\n")
                }
            }
            .add("platforms = setOf(")
            .apply {
                val validPlatforms = platformNames.filter {
                    it in listOf("ALL", "ANDROID", "IOS", "DESKTOP", "JVM", "LINUX", "WEB")
                }

                if (validPlatforms.isEmpty()) {
                    add("%T.ALL", settingPlatformClass)
                } else {
                    validPlatforms.forEachIndexed { i, name ->
                        if (i > 0) add(", ")
                        add("%T.%L", settingPlatformClass, name)
                    }
                }
            }
            .add("),\n")
            .unindent()
            .add(")")
            .build()
    }

    private fun buildValidationBlock(prop: KSPropertyDeclaration): CodeBlock? {
        val hasRange = prop.hasAnnotation(RANGE_ANNOTATION)
        val hasLength = prop.hasAnnotation(LENGTH_ANNOTATION)
        val hasPattern = prop.hasAnnotation(PATTERN_ANNOTATION)
        val hasRequired = prop.hasAnnotation(REQUIRED_ANNOTATION)

        if (!hasRange && !hasLength && !hasPattern && !hasRequired) {
            return null
        }

        val builder = CodeBlock.builder()
            .add("%T(\n", validationRules)
            .indent()

        // Range
        if (hasRange) {
            val ann = prop.getAnnotation(RANGE_ANNOTATION)!!
            val args = ann.arguments.associateBy { it.name?.asString().orEmpty() }
            val rangeMin = args["min"]?.value as? Double ?: Double.MIN_VALUE
            val rangeMax = args["max"]?.value as? Double ?: Double.MAX_VALUE
            val errorMsg = args["errorMessage"]?.value as? String ?: "Value out of range"
            val errorRes = args["errorMessageRes"]?.value as? Int ?: 0

            builder.add("range = %L..%L,\n", rangeMin, rangeMax)
            builder.add("errorMessage = %S,\n", errorMsg)
            builder.add("errorMessageRes = %L,\n", errorRes)
        } else {
            builder.add("range = null,\n")
        }

        // Length
        if (hasLength) {
            val ann = prop.getAnnotation(LENGTH_ANNOTATION)!!
            val args = ann.arguments.associateBy { it.name?.asString().orEmpty() }
            val lenMin = args["min"]?.value as? Int ?: 0
            val lenMax = args["max"]?.value as? Int ?: Int.MAX_VALUE
            val errorMsg = args["errorMessage"]?.value as? String ?: "Invalid length"
            val errorRes = args["errorMessageRes"]?.value as? Int ?: 0

            builder.add("length = %L..%L,\n", lenMin, lenMax)
            if (!hasRange) {
                builder.add("errorMessage = %S,\n", errorMsg)
                builder.add("errorMessageRes = %L,\n", errorRes)
            }
        } else {
            builder.add("length = null,\n")
        }

        // Pattern
        if (hasPattern) {
            val ann = prop.getAnnotation(PATTERN_ANNOTATION)!!
            val args = ann.arguments.associateBy { it.name?.asString().orEmpty() }
            val regex = args["regex"]?.value as? String ?: ".*"
            val errorMsg = args["errorMessage"]?.value as? String ?: "Invalid format"
            val errorRes = args["errorMessageRes"]?.value as? Int ?: 0

            builder.add("pattern = Regex(%S),\n", regex)
            if (!hasRange && !hasLength) {
                builder.add("errorMessage = %S,\n", errorMsg)
                builder.add("errorMessageRes = %L,\n", errorRes)
            }
        } else {
            builder.add("pattern = null,\n")
        }

        // Required
        if (hasRequired) {
            val ann = prop.getAnnotation(REQUIRED_ANNOTATION)!!
            val args = ann.arguments.associateBy { it.name?.asString().orEmpty() }
            val errorMsg = args["errorMessage"]?.value as? String ?: "This field is required"
            val errorRes = args["errorMessageRes"]?.value as? Int ?: 0

            builder.add("required = true,\n")
            if (!hasRange && !hasLength && !hasPattern) {
                builder.add("errorMessage = %S,\n", errorMsg)
                builder.add("errorMessageRes = %L,\n", errorRes)
            }
        } else {
            builder.add("required = false,\n")
        }

        builder.unindent()
        builder.add(")")

        return builder.build()
    }

    private fun buildConfirmationBlock(prop: KSPropertyDeclaration): CodeBlock? {
        val ann = prop.getAnnotation(REQUIRES_CONFIRMATION_ANNOTATION) ?: return null
        val args = ann.arguments.associateBy { it.name?.asString().orEmpty() }

        val title = args["title"]?.value as? String ?: "Confirm Change"
        val message = args["message"]?.value as? String ?: "Are you sure?"
        val titleRes = args["titleRes"]?.value as? Int ?: 0
        val messageRes = args["messageRes"]?.value as? Int ?: 0
        val confirmText = args["confirmText"]?.value as? String ?: "Confirm"
        val confirmTextRes = args["confirmTextRes"]?.value as? Int ?: 0
        val cancelText = args["cancelText"]?.value as? String ?: "Cancel"
        val cancelTextRes = args["cancelTextRes"]?.value as? Int ?: 0
        val isDangerous = args["isDangerous"]?.value as? Boolean ?: false

        return CodeBlock.builder()
            .add("%T(\n", confirmationConfig)
            .indent()
            .add("title = %S,\n", title)
            .add("message = %S,\n", message)
            .add("titleRes = %L,\n", titleRes)
            .add("messageRes = %L,\n", messageRes)
            .add("confirmText = %S,\n", confirmText)
            .add("confirmTextRes = %L,\n", confirmTextRes)
            .add("cancelText = %S,\n", cancelText)
            .add("cancelTextRes = %L,\n", cancelTextRes)
            .add("isDangerous = %L,\n", isDangerous)
            .unindent()
            .add(")")
            .build()
    }

    private fun getActionClass(prop: KSPropertyDeclaration): ClassName? {
        val ann = prop.getAnnotation(ACTION_HANDLER_ANNOTATION) ?: return null
        val args = ann.arguments.associateBy { it.name?.asString().orEmpty() }
        val actionType = args["action"]?.value as? KSType ?: return null
        return actionType.toClassName()
    }

    private fun getConfirmResetMessage(prop: KSPropertyDeclaration): String? {
        val ann = prop.getAnnotation(CONFIRM_RESET_ANNOTATION) ?: return null
        val args = ann.arguments.associateBy { it.name?.asString().orEmpty() }
        return args["message"]?.value as? String
    }

    private fun buildSimpleFieldCode(
        fieldClass: ClassName,
        modelClass: ClassName,
        propName: String,
        keyName: String,
        metaBlock: CodeBlock?,
    ): CodeBlock {
        return CodeBlock.builder()
            .add("%T<%T>(\n", fieldClass, modelClass)
            .indent()
            .add("name = %S,\n", propName)
            .add("keyName = %S,\n", keyName)
            .apply {
                if (metaBlock != null) {
                    add("meta = ").add(metaBlock).add(",\n")
                } else {
                    add("meta = null,\n")
                }
            }
            .add("getter = { it.%L },\n", propName)
            .add("setter = { m, v -> m.copy(%L = v) },\n", propName)
            .unindent()
            .add(")")
            .build()
    }

    private fun buildEnumFieldCode(
        enumType: KSType,
        modelClass: ClassName,
        propName: String,
        keyName: String,
        metaBlock: CodeBlock?,
        isNullable: Boolean,
    ): CodeBlock {
        val enumClass = enumType.toClassName()
        val fieldClass = if (isNullable) nullableEnumField else enumField

        val builder = CodeBlock.builder()
            .add("%T<%T, %T>(\n", fieldClass, modelClass, enumClass)
            .indent()
            .add("name = %S,\n", propName)
            .add("keyName = %S,\n", keyName)
            .apply {
                if (metaBlock != null) {
                    add("meta = ").add(metaBlock).add(",\n")
                } else {
                    add("meta = null,\n")
                }
            }
            .add("getter = { it.%L },\n", propName)
            .add("setter = { m, v -> m.copy(%L = v) },\n", propName)
            .add("enumValues = %T.entries.toTypedArray(),\n", enumClass)

        if (!isNullable) {
            builder.add("defaultValue = %T.entries.first(),\n", enumClass)
        }

        return builder
            .unindent()
            .add(")")
            .build()
    }

    private fun buildSerializedFieldCode(
        propType: KSType,
        modelClass: ClassName,
        propName: String,
        keyName: String,
        metaBlock: CodeBlock?,
        isNullable: Boolean,
    ): CodeBlock {
        val typeClassName = propType.makeNotNullable().toClassName()
        val fieldClass = if (isNullable) nullableSerializedField else serializedField

        val builder = CodeBlock.builder()
            .add("%T<%T, %T>(\n", fieldClass, modelClass, typeClassName)
            .indent()
            .add("name = %S,\n", propName)
            .add("keyName = %S,\n", keyName)
            .apply {
                if (metaBlock != null) {
                    add("meta = ").add(metaBlock).add(",\n")
                } else {
                    add("meta = null,\n")
                }
            }
            .add("getter = { it.%L },\n", propName)
            .add("setter = { m, v -> m.copy(%L = v) },\n", propName)
            .add("serializer = %M(),\n", MemberName("kotlinx.serialization", "serializer"))

        if (!isNullable) {
            builder.add("defaultValue = %T(),\n", typeClassName)
        }

        return builder
            .unindent()
            .add(")")
            .build()
    }

    private fun getCategoryOrder(categoryType: KSType?): Int {
        if (categoryType == null) return Int.MAX_VALUE

        val categoryDecl = categoryType.declaration
        val catDefAnn = categoryDecl.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == CATEGORY_DEF_ANNOTATION
        }

        return catDefAnn?.arguments
            ?.firstOrNull { it.name?.asString() == "order" }
            ?.value as? Int ?: 0
    }

    private fun getFieldClass(type: KSType, isNullable: Boolean): ClassName? {
        val qualifiedName = type.declaration.qualifiedName?.asString()

        return if (isNullable) {
            when (qualifiedName) {
                "kotlin.Boolean" -> nullableBooleanField
                "kotlin.Int" -> nullableIntField
                "kotlin.Long" -> nullableLongField
                "kotlin.Float" -> nullableFloatField
                "kotlin.Double" -> nullableDoubleField
                "kotlin.String" -> nullableStringField
                "kotlin.Unit" -> unitField
                else -> null
            }
        } else {
            when (qualifiedName) {
                "kotlin.Boolean" -> booleanField
                "kotlin.Int" -> intField
                "kotlin.Long" -> longField
                "kotlin.Float" -> floatField
                "kotlin.Double" -> doubleField
                "kotlin.String" -> stringField
                "kotlin.Unit" -> unitField
                else -> null
            }
        }
    }

    private fun isSerializable(type: KSType?, resolver: Resolver): Boolean {
        if (type == null) return false
        return type.declaration.annotations.any {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == KOTLINX_SERIALIZABLE
        }
    }

    private fun KSPropertyDeclaration.hasAnnotation(fqcn: String): Boolean =
        annotations.any { it.annotationType.resolve().declaration.qualifiedName?.asString() == fqcn }

    private fun KSPropertyDeclaration.getAnnotation(fqcn: String): KSAnnotation? =
        annotations.firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == fqcn }

    private fun KSType.toClassName(): ClassName {
        val decl = declaration
        return ClassName(decl.packageName.asString(), decl.simpleName.asString())
    }

    private fun toSnakeCase(s: String): String = buildString {
        s.forEachIndexed { i, c ->
            if (c.isUpperCase() && i != 0) append('_')
            append(c.lowercaseChar())
        }
    }

    private fun getMapFieldClass(keyType: String?, valueType: String?): ClassName? {
        return when (keyType) {
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
                else -> null
            }

            "kotlin.Long" -> when (valueType) {
                "kotlin.String" -> longStringMapField
                "kotlin.Long" -> longLongMapField
                else -> null
            }

            else -> null
        }
    }

    private fun extractPlatformNames(platformsArg: KSValueArgument?, propName: String): List<String> {
        if (platformsArg == null) {
            logger.info("$propName: platforms arg is null, using default")
            return emptyList()
        }

        val value = platformsArg.value
        logger.info("$propName: platforms raw value = $value (${value?.let { it::class.simpleName }})")

        if (value !is List<*>) {
            logger.warn("$propName: platforms is not a List: ${value?.let { it::class.simpleName }}")
            return emptyList()
        }

        val names = mutableListOf<String>()

        value.forEach { element ->
            logger.info("$propName: processing element $element (${element?.let { it::class.simpleName }})")

            when (element) {
                is KSType -> {
                    val decl = element.declaration
                    val name = decl.simpleName.asString()
                    logger.info("$propName: KSType -> declaration.simpleName = $name")

                    val parent = decl.parentDeclaration
                    if (parent?.qualifiedName?.asString() == "io.github.mlmgames.settings.core.annotations.SettingPlatform") {
                        names.add(name)
                        logger.info("$propName: Added platform: $name")
                    } else {
                        names.add(name)
                        logger.info("$propName: Added platform (unverified parent): $name")
                    }
                }
                is KSClassDeclaration -> {
                    val name = element.simpleName.asString()
                    names.add(name)
                    logger.info("$propName: KSClassDeclaration -> $name")
                }
                else -> {
                    logger.warn("$propName: Unknown element type: ${element?.let { it::class.qualifiedName }}")
                    element?.toString()?.let { str ->
                        val enumName = str.substringAfterLast('.')
                        if (enumName in listOf("ALL", "ANDROID", "IOS", "DESKTOP", "JVM", "LINUX", "WEB")) {
                            names.add(enumName)
                            logger.info("$propName: Extracted from toString: $enumName")
                        }
                    }
                }
            }
        }

        logger.info("$propName: Final platform names = $names")
        return names
    }

    private fun computeValueKindInfo(propType: KSType, resolver: Resolver): Pair<String, String?> {
        val baseType = propType.makeNotNullable()
        val qualifiedName = baseType.declaration.qualifiedName?.asString()

        return when (qualifiedName) {
            "kotlin.Boolean" -> "BOOLEAN" to null
            "kotlin.Int" -> "INT" to null
            "kotlin.Long" -> "LONG" to null
            "kotlin.Float" -> "FLOAT" to null
            "kotlin.Double" -> "DOUBLE" to null
            "kotlin.String" -> "STRING" to null
            else -> {
                val isEnum = (baseType.declaration as? KSClassDeclaration)?.classKind == ClassKind.ENUM_CLASS
                if (isEnum) {
                    val enumClass = baseType.toClassName()
                    "ENUM" to "${enumClass.packageName}.${enumClass.simpleName}"
                } else {
                    "NONE" to null
                }
            }
        }
    }

}
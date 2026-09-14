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
        private const val SERIALIZED_WITH_ANNOTATION = "io.github.mlmgames.settings.core.annotations.SerializedWith"
        private const val VALIDATED_BY_ANNOTATION = "io.github.mlmgames.settings.core.annotations.ValidatedBy"
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
    private val longListField = ClassName(fieldsPackage, "LongListField")
    private val stringMapField = ClassName(fieldsPackage, "StringMapField")
    private val stringLongMapField = ClassName(fieldsPackage, "StringLongMapField")
    private val stringIntMapField = ClassName(fieldsPackage, "StringIntMapField")

    private val stringFloatMapField = ClassName(fieldsPackage, "StringFloatMapField")
    private val stringDoubleMapField = ClassName(fieldsPackage, "StringDoubleMapField")
    private val stringBooleanMapField = ClassName(fieldsPackage, "StringBooleanMapField")
    private val intStringMapField = ClassName(fieldsPackage, "IntStringMapField")
    private val intIntMapField = ClassName(fieldsPackage, "IntIntMapField")
    private val intLongMapField = ClassName(fieldsPackage, "IntLongMapField")
    private val longStringMapField = ClassName(fieldsPackage, "LongStringMapField")
    private val longLongMapField = ClassName(fieldsPackage, "LongLongMapField")
    private val longIntMapField = ClassName(fieldsPackage, "LongIntMapField")


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

        // Only process validated symbols; deferred ones re-run next round.
        val validProps = allProps.filter { it.validate() }

        // A property carrying both annotations would generate two fields on one
        // key. Fail loudly per property instead of corrupting DataStore.
        for (prop in validProps) {
            val hasSetting = prop.annotations.any {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == SETTING_ANNOTATION
            }
            val hasPersisted = prop.annotations.any {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == PERSISTED_ANNOTATION
            }
            if (hasSetting && hasPersisted) {
                logger.error("@Setting and @Persisted are mutually exclusive on '${prop.simpleName.asString()}'", prop)
            }
        }

        val processable = validProps.filter { prop ->
            val hasSetting = prop.annotations.any {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == SETTING_ANNOTATION
            }
            val hasPersisted = prop.annotations.any {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == PERSISTED_ANNOTATION
            }
            !(hasSetting && hasPersisted)
        }

        val byClass = processable.groupBy { it.parentDeclaration as? KSClassDeclaration }

        for ((klass, props) in byClass) {
            if (klass == null) {
                for (prop in props) {
                    logger.error("@Setting/@Persisted must be inside a data class (found top-level '${prop.simpleName.asString()}')", prop)
                }
                continue
            }
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

        // No-arg construction: generated `default = Model()` and per-field
        // `Model().prop` defaults require every primary-constructor param to
        // carry a default. Without this the schema won't compile.
        val ctorParamsWithoutDefaults = klass.primaryConstructor
            ?.parameters?.filter { !it.hasDefault }?.mapNotNull { it.name?.asString() }
            .orEmpty()
        if (ctorParamsWithoutDefaults.isNotEmpty()) {
            logger.error(
                "${klass.simpleName.asString()} must provide defaults for all constructor params (missing: ${ctorParamsWithoutDefaults.joinToString()}), otherwise generated schema cannot construct defaults",
                klass
            )
            return
        }

        // Duplicate and colliding keys corrupt DataStore (keys compare by name
        // only). Detect explicit key= collisions and snake_case collisions here.
        val seenKeys = mutableMapOf<String, String>()
        for (prop in allProps) {
            val key = keyForProp(prop, SETTING_ANNOTATION, PERSISTED_ANNOTATION)
            val prev = seenKeys.putIfAbsent(key, prop.simpleName.asString())
            if (prev != null) {
                logger.error(
                    "Duplicate persistence key '$key' (properties '$prev' and '${prop.simpleName.asString()}'). Set explicit key= values.",
                    prop
                )
            }
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

        // Validate cross-field references once per class: every dependsOn must
        // name a sibling property, and the graph must be acyclic. Runtime
        // fail-open (isEnabled) can no longer hide typos.
        validateDependencies(allProps)

        // Validate each property's own configuration (slider bounds, dropdown
        // options, Button/action pairing, validation applicability...).
        val propByName = allProps.associateBy { it.simpleName.asString() }
        for (prop in settingProps) {
            validateSettingProp(prop, propByName, resolver)
        }
        for (prop in persistedProps) {
            validatePersistedProp(prop, resolver)
        }

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
                    // No-arg constructor required: every @Setting data class must
                    // provide defaults for all constructor params. Validated here
                    // so generated code always compiles.
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

        fileSpec.writeTo(codeGenerator, Dependencies(false, klass.containingFile ?: run {
            logger.error("Cannot determine containing file for ${klass.simpleName.asString()}", klass)
            return
        }))
    }

    private fun keyForProp(
        prop: KSPropertyDeclaration,
        settingAnnotation: String,
        persistedAnnotation: String,
    ): String {
        val propName = prop.simpleName.asString()
        val ann = prop.annotations.firstOrNull {
            val qname = it.annotationType.resolve().declaration.qualifiedName?.asString()
            qname == settingAnnotation || qname == persistedAnnotation
        }
        val args = ann?.arguments?.associateBy { it.name?.asString().orEmpty() }.orEmpty()
        val keyOverride = args["key"]?.value as? String ?: ""
        return keyOverride.ifBlank { toSnakeCase(propName) }
    }

    private fun validateDependencies(allProps: List<KSPropertyDeclaration>) {
        val names = allProps.map { it.simpleName.asString() }.toSet()
        val edges = mutableMapOf<String, String>()
        for (prop in allProps) {
            val propName = prop.simpleName.asString()
            val settingAnn = prop.annotations.firstOrNull {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == SETTING_ANNOTATION
            } ?: continue
            val dependsOn = settingAnn.arguments
                .firstOrNull { it.name?.asString() == "dependsOn" }?.value as? String ?: ""
            if (dependsOn.isBlank()) continue
            if (dependsOn == propName) {
                logger.error("dependsOn must not reference itself: '$propName'", prop)
                continue
            }
            if (dependsOn !in names) {
                logger.error("dependsOn='$dependsOn' does not match any property (field '$propName')", prop)
                continue
            }
            edges[propName] = dependsOn
        }
        // Cycle detection (iterative, no recursion).
        for (start in edges.keys) {
            val seen = mutableSetOf<String>()
            var cursor: String? = start
            while (cursor != null && edges.containsKey(cursor)) {
                if (!seen.add(cursor)) {
                    logger.error("Cyclic dependsOn chain involving '$start'", allProps.first { it.simpleName.asString() == start })
                    break
                }
                cursor = edges[cursor]
            }
        }
    }

    private fun validateSettingProp(
        prop: KSPropertyDeclaration,
        propByName: Map<String, KSPropertyDeclaration>,
        resolver: Resolver,
    ) {
        val propName = prop.simpleName.asString()
        val propType = prop.type.resolve()
        val baseType = propType.makeNotNullable()

        val ann = prop.annotations.first {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == SETTING_ANNOTATION
        }
        val args = ann.arguments.associateBy { it.name?.asString().orEmpty() }
        val min = args["min"]?.value as? Float ?: 0f
        val max = args["max"]?.value as? Float ?: 100f
        val step = args["step"]?.value as? Float ?: 1f
        val options = (args["options"]?.value as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val typeType = args["type"]?.value as? KSType
        val typeName = try {
            typeType?.toClassName()?.simpleName
        } catch (e: Exception) { null } ?: "Toggle"

        if (min >= max) {
            logger.error("@Setting min ($min) must be < max ($max): $propName", prop)
        }
        if (!(step > 0f) || step.isNaN()) {
            logger.error("@Setting step ($step) must be > 0: $propName", prop)
        }
        if (typeName == "TimePickerType") {
            val propTypeName = baseType.declaration.qualifiedName?.asString()
            if (propTypeName != "kotlin.Int") {
                logger.error("TimePickerType settings must use Int (minutes from midnight): $propName", prop)
            }
        } else if (typeName == "Dropdown") {
            val isEnum = (baseType.declaration as? KSClassDeclaration)?.classKind == ClassKind.ENUM_CLASS
            if (!isEnum && options.isEmpty()) {
                val optionsRes = args["optionsRes"]?.value as? Int ?: 0
                if (optionsRes == 0) {
                    logger.error("Dropdown '$propName' needs options= or optionsRes=", prop)
                }
            }
        }
        val isButton = typeName == "Button"
        val hasAction = prop.hasAnnotation(ACTION_HANDLER_ANNOTATION)
        if (isButton && !hasAction) {
            logger.error("Button '$propName' requires @ActionHandler", prop)
        }
        if (!isButton && hasAction) {
            logger.error("@ActionHandler is only valid on Button type (field '$propName')", prop)
        }
        if (isButton && baseType.declaration.qualifiedName?.asString() != "kotlin.Unit") {
            logger.error("Button '$propName' must be backed by Unit", prop)
        }
        if (baseType.declaration.qualifiedName?.asString() == "kotlin.Unit" && !isButton) {
            logger.error("Unit '$propName' must use Button type", prop)
        }

        // Validation applicability: @Range on numbers only, @Length/@Pattern on
        // String only, and range bounds ordered.
        val qname = baseType.declaration.qualifiedName?.asString()
        val isNumeric = qname in setOf("kotlin.Int", "kotlin.Long", "kotlin.Float", "kotlin.Double") ||
            propType.arguments.any { false }
        val isString = qname == "kotlin.String"
        if (prop.hasAnnotation(RANGE_ANNOTATION) && !(qname in setOf("kotlin.Int", "kotlin.Long", "kotlin.Float", "kotlin.Double"))) {
            logger.error("@Range applies to numeric types only (field '$propName' is $qname)", prop)
        }
        if (prop.hasAnnotation(LENGTH_ANNOTATION) && !isString) {
            logger.error("@Length applies to String only (field '$propName' is $qname)", prop)
        }
        if (prop.hasAnnotation(PATTERN_ANNOTATION) && !isString) {
            logger.error("@Pattern applies to String only (field '$propName' is $qname)", prop)
        }
        if (prop.hasAnnotation(RANGE_ANNOTATION)) {
            val rangeAnn = prop.getAnnotation(RANGE_ANNOTATION)!!
            val rArgs = rangeAnn.arguments.associateBy { it.name?.asString().orEmpty() }
            val rMin = rArgs["min"]?.value as? Double ?: -Double.MAX_VALUE
            val rMax = rArgs["max"]?.value as? Double ?: Double.MAX_VALUE
            if (rMin > rMax) {
                logger.error("@Range min ($rMin) must be <= max ($rMax): $propName", prop)
            }
        }
        if (prop.hasAnnotation(PATTERN_ANNOTATION)) {
            val regex = prop.getAnnotation(PATTERN_ANNOTATION)!!
                .arguments.firstOrNull { it.name?.asString() == "regex" }?.value as? String ?: ""
            try {
                Regex(regex)
            } catch (e: Exception) {
                logger.error("Invalid @Pattern regex for '$propName': ${e.message}", prop)
            }
        }

        // @SerializedWith requires @Serialized; @Serialized on a non-serializable
        // type without a custom serializer is a compile error later — fail here.
        if (prop.hasAnnotation(SERIALIZED_WITH_ANNOTATION) && !prop.hasAnnotation(SERIALIZED_ANNOTATION)) {
            logger.error("@SerializedWith requires @Serialized on '$propName'", prop)
        }

        // @ValidatedBy support is not implemented at runtime; fail loudly instead
        // of silently dropping the validator.
        if (prop.hasAnnotation(VALIDATED_BY_ANNOTATION)) {
            logger.error("@ValidatedBy is not supported yet (field '$propName'); remove it or implement runtime support", prop)
        }

        // Visibility: property must be a primary-constructor val accessible from
        // generated code (public/internal, non-private), otherwise copy()/getter
        // references won't compile.
        val containing = prop.parentDeclaration as? KSClassDeclaration
        val inPrimaryCtor = containing?.primaryConstructor
            ?.parameters?.any { it.name?.asString() == propName } == true
        if (!inPrimaryCtor) {
            logger.error("'$propName' must be a primary-constructor property for copy() codegen", prop)
        }
        if (Modifier.PRIVATE in prop.modifiers || Modifier.PROTECTED in prop.modifiers) {
            logger.error("'$propName' must not be private/protected (generated schema cannot access it)", prop)
        }
        if (isNumeric) { /* marker to keep branch explicit */ }
        validateCategory(prop, args)
    }

    private fun validatePersistedProp(prop: KSPropertyDeclaration, resolver: Resolver) {
        val propName = prop.simpleName.asString()
        for (fqcn in listOf(RANGE_ANNOTATION, LENGTH_ANNOTATION, PATTERN_ANNOTATION, REQUIRED_ANNOTATION, VALIDATED_BY_ANNOTATION)) {
            if (prop.hasAnnotation(fqcn)) {
                logger.error("${fqcn.substringAfterLast('.')} on @Persisted '$propName' is ignored; move it to @Setting", prop)
            }
        }
        if (prop.hasAnnotation(REQUIRES_CONFIRMATION_ANNOTATION)) {
            logger.error("@RequiresConfirmation on @Persisted '$propName' is ignored; move it to @Setting", prop)
        }
    }

    private fun validateCategory(prop: KSPropertyDeclaration, args: Map<String, KSValueArgument>) {
        val propName = prop.simpleName.asString()
        val categoryType = args["category"]?.value as? KSType ?: run {
            logger.error("Missing category for $propName", prop)
            return
        }
        val categoryDecl = categoryType.declaration as? KSClassDeclaration
        if (categoryDecl == null) {
            logger.error("category for '$propName' must be an object", prop)
            return
        }
        if (categoryDecl.classKind != ClassKind.OBJECT) {
            logger.error("category for '$propName' must be an object (${categoryDecl.simpleName.asString()})", prop)
        }
        if (!categoryDecl.annotations.any {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == CATEGORY_DEF_ANNOTATION
            }
        ) {
            logger.error("category ${categoryDecl.simpleName.asString()} lacks @CategoryDefinition", prop)
        }
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

        // Re-validate category here so generateSettingField is safe standalone.
        validateCategory(prop, args)
        val categoryOrder = getCategoryOrder(categoryType)

        val typeType = args["type"]?.value as? KSType
        val typeClass = typeType?.toClassName()
            ?: ClassName("io.github.mlmgames.settings.core.types", "Toggle")

        // Validate TimePickerType by qualified name (simpleName matching
        // false-positives on same-named custom types). Skip the field after
        // logging so broken config never generates a mismatched field.
        if (typeClass.canonicalName == "io.github.mlmgames.settings.core.types.TimePickerType") {
            val propTypeName = propType.makeNotNullable().declaration.qualifiedName?.asString()
            if (propTypeName != "kotlin.Int") {
                logger.error("TimePickerType settings must use Int (minutes from midnight): $propName", prop)
                return null
            }
        }

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
            "kotlin.collections.Set", "kotlin.collections.MutableSet", "kotlin.collections.LinkedHashSet", "kotlin.collections.HashSet" -> {
                val typeArg = propType.arguments.firstOrNull()?.type?.resolve()
                val argQname = typeArg?.declaration?.qualifiedName?.asString()
                if (argQname == "kotlin.String" && typeArg?.isMarkedNullable != true && !isNullable) {
                    return buildSimpleFieldCode(stringSetField, modelClass, propName, keyName, metaBlock)
                }
                if (isNullable || typeArg?.isMarkedNullable == true) {
                    logger.error("Nullable collections are not supported; use @Serialized on '$propName' (Set<String>? / Set<String?>)", prop)
                    return null
                }
                // Non-String element: serialized path or error below.
                if (argQname == "kotlin.String") {
                    return buildSimpleFieldCode(stringSetField, modelClass, propName, keyName, metaBlock)
                }
            }

            "kotlin.collections.List", "kotlin.collections.MutableList", "kotlin.collections.ArrayList" -> {
                val typeArg = propType.arguments.firstOrNull()?.type?.resolve()
                if (isNullable || typeArg?.isMarkedNullable == true) {
                    logger.error("Nullable collections are not supported; use @Serialized on '$propName' (List? / List<String?>)", prop)
                    return null
                }
                return when (typeArg?.declaration?.qualifiedName?.asString()) {
                    "kotlin.String" -> buildSimpleFieldCode(stringListField, modelClass, propName, keyName, metaBlock)
                    "kotlin.Int" -> buildSimpleFieldCode(intListField, modelClass, propName, keyName, metaBlock)
                    "kotlin.Long" -> buildSimpleFieldCode(longListField, modelClass, propName, keyName, metaBlock)
                    else -> {
                        if (hasSerialized || isSerializable(typeArg, resolver)) {
                            buildSerializedFieldCode(propType, modelClass, propName, keyName, metaBlock, prop, isNullable)
                        } else {
                            logger.error("Unsupported List element type for $propName. Add @Serialized.", prop)
                            null
                        }
                    }
                }
            }

            "kotlin.collections.Map", "kotlin.collections.MutableMap", "kotlin.collections.LinkedHashMap", "kotlin.collections.HashMap" -> {
                if (isNullable) {
                    logger.error("Nullable maps are not supported; use @Serialized on '$propName' (Map? with null values)", prop)
                    return null
                }
                val keyArg = propType.arguments.getOrNull(0)?.type?.resolve()
                val valueArg = propType.arguments.getOrNull(1)?.type?.resolve()
                if (keyArg?.isMarkedNullable == true || valueArg?.isMarkedNullable == true) {
                    logger.error("Nullable map key/value types are not supported; use @Serialized on '$propName'", prop)
                    return null
                }
                val keyTypeName = keyArg?.declaration?.qualifiedName?.asString()
                val valueTypeName = valueArg?.declaration?.qualifiedName?.asString()

                val mapFieldClass = getMapFieldClass(keyTypeName, valueTypeName)

                return if (mapFieldClass != null) {
                    buildSimpleFieldCode(mapFieldClass, modelClass, propName, keyName, metaBlock)
                } else if (hasSerialized || isSerializable(valueArg, resolver)) {
                    buildSerializedFieldCode(propType, modelClass, propName, keyName, metaBlock, prop, isNullable)
                } else {
                    logger.error("Unsupported Map type for $propName: Map<$keyTypeName, $valueTypeName>. Add @Serialized for complex value types.", prop)
                    null
                }
            }
        }

        if (isNullable && propType.makeNotNullable().declaration.qualifiedName?.asString() == "kotlin.Unit") {
            logger.error("Nullable Unit is not supported: $propName", prop)
            return null
        }

        // Enum types
        if (isEnum) {
            return buildEnumFieldCode(baseType, modelClass, propName, keyName, metaBlock, prop, isNullable)
        }

        // Primitive types
        val fieldClass = getFieldClass(baseType, isNullable)
        if (fieldClass != null) {
            return buildSimpleFieldCode(fieldClass, modelClass, propName, keyName, metaBlock)
        }

        // Complex types with @Serialized or @Serializable
        if (hasSerialized || isSerializable(baseType, resolver)) {
            if (hasSerialized && !isSerializable(baseType, resolver) && !prop.hasAnnotation(SERIALIZED_WITH_ANNOTATION)) {
                logger.error("'$propName' uses @Serialized but ${baseType.declaration.simpleName.asString()} is not @Serializable; add @Serializable or @SerializedWith", prop)
                return null
            }
            return buildSerializedFieldCode(propType, modelClass, propName, keyName, metaBlock, prop, isNullable)
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
            val rangeMin = args["min"]?.value as? Double ?: -Double.MAX_VALUE
            val rangeMax = args["max"]?.value as? Double ?: Double.MAX_VALUE
            if (rangeMin > rangeMax) {
                logger.error("@Range min ($rangeMin) must be <= max ($rangeMax)", prop)
            }
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

    private fun getCustomSerializerClass(prop: KSPropertyDeclaration): ClassName? {
        val ann = prop.getAnnotation(SERIALIZED_WITH_ANNOTATION) ?: return null
        val args = ann.arguments.associateBy { it.name?.asString().orEmpty() }
        val serializerType = args["serializer"]?.value as? KSType ?: return null
        return serializerType.toClassName()
    }

    private fun getConfirmResetMessage(prop: KSPropertyDeclaration): String? {
        val ann = prop.getAnnotation(CONFIRM_RESET_ANNOTATION) ?: return null
        val args = ann.arguments.associateBy { it.name?.asString().orEmpty() }
        return args["message"]?.value as? String
            ?: "Are you sure you want to reset this setting?"
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
        prop: KSPropertyDeclaration,
        isNullable: Boolean,
    ): CodeBlock {
        val enumClass = enumType.toClassName()
        val fieldClass = if (isNullable) nullableEnumField else enumField

        // Use the model's own default (getter(schema.default)) rather than
        // entries.first(): a model defaulting to DARK must persist DARK.
        // Generated via a default-value lambda is impossible here, so the
        // runtime field keeps an optional legacy default (null = use schema).
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
            .add("enumValues = %T.values(),\n", enumClass)

        if (!isNullable) {
            val modelDefault = propModelDefault(prop, modelClass, enumClass)
            builder.add("defaultValue = %L,\n", modelDefault)
        }

        return builder
            .unindent()
            .add(")")
            .build()
    }

    /**
     * Best-effort reference to the model's declared default for [propName]:
     * `%T().prop` on the no-arg model instance. Falls back to entries.first()
     * when the model lacks a no-arg constructor (an error is logged at the
     * schema-default site instead).
     */
    private fun propModelDefault(prop: KSPropertyDeclaration, modelClass: ClassName, enumClass: ClassName): CodeBlock {
        return CodeBlock.of("%T().%L", modelClass, prop.simpleName.asString())
    }

    private fun buildSerializedFieldCode(
        propType: KSType,
        modelClass: ClassName,
        propName: String,
        keyName: String,
        metaBlock: CodeBlock?,
        prop: KSPropertyDeclaration,
        isNullable: Boolean,
    ): CodeBlock {
        val typeClassName = propType.makeNotNullable().toTypeName()
        val fieldClass = if (isNullable) nullableSerializedField else serializedField
        val customSerializer = getCustomSerializerClass(prop)

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
            .apply {
                if (customSerializer != null) {
                    add("serializer = %T.serializer(),\n", customSerializer)
                } else {
                    add("serializer = %M<%T>(),\n", MemberName("kotlinx.serialization", "serializer"), typeClassName)
                }
            }

        if (!isNullable) {
            val defaultValue = buildModelPropDefault(modelClass, propName, typeClassName)
            builder.add("defaultValue = %L,\n", defaultValue)
        }

        return builder
            .unindent()
            .add(")")
            .build()
    }

    /**
     * Model-declared default (`Model().prop`) so generated defaults match the
     * data class initializer instead of emptyList()/first-entry guesses.
     */
    private fun buildModelPropDefault(modelClass: ClassName, propName: String, type: TypeName): CodeBlock {
        return CodeBlock.of("%T().%L", modelClass, propName)
    }

    /**
     * Convert a KSType to a TypeName, preserving nesting, argument nullability,
     * variance, and star projections.
     */
    private fun KSType.toTypeName(): TypeName {
        val className = toClassName()
        val typeArgs: List<TypeName> = arguments.map { arg ->
            when (arg.variance) {
                Variance.STAR -> STAR
                Variance.COVARIANT -> WildcardTypeName.producerOf(
                    arg.type?.resolve()?.toTypeName() ?: ANY
                )
                Variance.CONTRAVARIANT -> WildcardTypeName.consumerOf(
                    arg.type?.resolve()?.toTypeName() ?: ANY
                )
                else -> {
                    val resolved = arg.type?.resolve()
                    (resolved?.toTypeName() ?: ANY).copy(nullable = resolved?.isMarkedNullable == true)
                }
            }
        }
        return if (typeArgs.isNotEmpty()) {
            className.parameterizedBy(typeArgs).copy(nullable = isMarkedNullable)
        } else {
            className.copy(nullable = isMarkedNullable)
        }
    }

    /**
     * Generate a default value expression for a KSType.
     */
    private fun buildDefaultValue(type: KSType): CodeBlock {
        val qname = type.declaration.qualifiedName?.asString()
        return when (qname) {
            "kotlin.collections.List" -> CodeBlock.of("emptyList()")
            "kotlin.collections.Set" -> CodeBlock.of("emptySet()")
            "kotlin.collections.Map" -> CodeBlock.of("emptyMap()")
            else -> CodeBlock.of("%T()", type.toTypeName())
        }
    }

    private fun getCategoryOrder(categoryType: KSType?): Int {
        if (categoryType == null) return Int.MAX_VALUE

        val categoryDecl = categoryType.declaration
        val catDefAnn = categoryDecl.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == CATEGORY_DEF_ANNOTATION
        } ?: return Int.MAX_VALUE

        return catDefAnn.arguments
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
        // Nested-class-aware: walk parents so `Outer.Inner` keeps both names.
        // kotlinpoet-ksp's toClassName handles this too; this local version
        // also tolerates declarations without a resolvable parent chain.
        val decl = declaration
        val pkg = decl.packageName.asString()
        val names = mutableListOf(decl.simpleName.asString())
        var parent = decl.parentDeclaration
        while (parent != null && parent !is KSFile) {
            names.add(0, parent.simpleName.asString())
            parent = parent.parentDeclaration
        }
        return ClassName(pkg, names)
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
    }

    private fun extractPlatformNames(platformsArg: KSValueArgument?, propName: String): List<String> {
        if (platformsArg == null) {
            return emptyList()
        }

        val value = platformsArg.value
        if (value !is List<*>) {
            logger.error("$propName: platforms must be an array of SettingPlatform", null)
            return emptyList()
        }

        // Fail-open extraction (typo -> ALL) previously hid misconfigurations.
        // Only exact SettingPlatform entries are accepted; anything else is a
        // KSP error so the generated `platforms` set is always explicit.
        val valid = setOf("ALL", "ANDROID", "IOS", "DESKTOP", "JVM", "LINUX", "WEB")
        val names = mutableListOf<String>()

        value.forEach { element ->
            val name: String? = when (element) {
                is KSType -> {
                    val decl = element.declaration
                    val parent = decl.parentDeclaration
                    if (parent?.qualifiedName?.asString() == "io.github.mlmgames.settings.core.annotations.SettingPlatform") {
                        decl.simpleName.asString()
                    } else null
                }
                is KSClassDeclaration -> {
                    val parent = element.parentDeclaration
                    if (parent?.qualifiedName?.asString() == "io.github.mlmgames.settings.core.annotations.SettingPlatform") {
                        element.simpleName.asString()
                    } else null
                }
                else -> element?.toString()?.substringAfterLast('.')
                    ?.takeIf { it in valid }
            }
            if (name == null || name !in valid) {
                logger.error("$propName: unknown platform '$element'; expected one of $valid", null)
            } else {
                names.add(name)
            }
        }

        return names.distinct()
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
                    // Canonical nested name (Outer.Inner), not pkg+simpleName.
                    "ENUM" to enumClass.canonicalName
                } else {
                    "NONE" to null
                }
            }
        }
    }

}
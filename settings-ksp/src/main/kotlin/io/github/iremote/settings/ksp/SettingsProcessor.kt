package io.github.iremote.settings.ksp

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

class SettingsProcessor(
  env: SymbolProcessorEnvironment
) : SymbolProcessor {

  private val codeGenerator = env.codeGenerator
  private val logger = env.logger

  private val settingAnnotationFqcn = "io.github.iremote.settings.core.Setting"

  override fun process(resolver: Resolver): List<KSAnnotated> {
    val symbols = resolver.getSymbolsWithAnnotation(settingAnnotationFqcn).toList()
    val invalid = symbols.filterNot { it.validate() }

    val props = symbols.filterIsInstance<KSPropertyDeclaration>()
    val byClass = props.groupBy { it.parentDeclaration as? KSClassDeclaration }

    for ((klass, classProps) in byClass) {
      if (klass == null) continue
      generateSchemaForClass(klass, classProps)
    }

    return invalid
  }

  private fun generateSchemaForClass(
    klass: KSClassDeclaration,
    props: List<KSPropertyDeclaration>,
  ) {
    val pkg = klass.packageName.asString()
    val className = klass.simpleName.asString()

    if (Modifier.DATA !in klass.modifiers) {
      logger.error("@Setting can only be used on data classes (needs copy()).", klass)
      return
    }

    val schemaName = "${className}Schema"
    val schemaClass = ClassName(pkg, schemaName)
    val modelClass = klass.toClassName()

    val settingsSchema = ClassName("io.github.iremote.settings.core", "SettingsSchema")
    val settingMeta = ClassName("io.github.iremote.settings.core", "SettingMeta")
    val settingCategory = ClassName("io.github.iremote.settings.core", "SettingCategory")
    val settingType = ClassName("io.github.iremote.settings.core", "SettingType")

    val booleanField = ClassName("io.github.iremote.settings.core.fields", "BooleanField")
    val intField = ClassName("io.github.iremote.settings.core.fields", "IntField")
    val longField = ClassName("io.github.iremote.settings.core.fields", "LongField")
    val floatField = ClassName("io.github.iremote.settings.core.fields", "FloatField")
    val stringField = ClassName("io.github.iremote.settings.core.fields", "StringField")

    fun snakeCase(s: String): String =
      buildString {
        s.forEachIndexed { i, c ->
          val isUpper = c.isUpperCase()
          if (isUpper && i != 0) append('_')
          append(c.lowercaseChar())
        }
      }

    val typeToFieldClass: (KSType) -> ClassName? = { t ->
      val q = t.declaration.qualifiedName?.asString()
      when (q) {
        "kotlin.Boolean" -> booleanField
        "kotlin.Int" -> intField
        "kotlin.Long" -> longField
        "kotlin.Float" -> floatField
        "kotlin.String" -> stringField
        else -> null
      }
    }

    val fieldsCode = CodeBlock.builder()
    fieldsCode.add("listOf(\n")
    fieldsCode.indent()

    for (p in props) {
      val propName = p.simpleName.asString()
      val ann = p.annotations.firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == settingAnnotationFqcn }
      if (ann == null) continue

      val args = ann.arguments.associateBy { it.name?.asString().orEmpty() }

      fun argString(name: String, default: String = ""): String =
        (args[name]?.value as? String) ?: default

      fun argFloat(name: String, default: Float = 0f): Float =
        (args[name]?.value as? Float) ?: default

      fun argStringArray(name: String): List<String> =
        (args[name]?.value as? List<*>)?.filterIsInstance<String>().orEmpty()

      fun argEnumEntry(name: String, enumClass: ClassName): CodeBlock {
        val v = args[name]?.value as? KSType
        val entry = v?.declaration?.simpleName?.asString()
        return if (entry != null) CodeBlock.of("%T.%L", enumClass, entry) else CodeBlock.of("%T.%L", enumClass, "GENERAL")
      }

      val title = argString("title")
      val description = argString("description")
      val dependsOn = argString("dependsOn")
      val keyOverride = argString("key")
      val keyName = keyOverride.ifBlank { snakeCase(propName) }

      val min = argFloat("min", 0f)
      val max = argFloat("max", 100f)
      val step = argFloat("step", 1f)
      val options = argStringArray("options")

      val category = argEnumEntry("category", settingCategory)
      val type = argEnumEntry("type", settingType)

      val propType = p.type.resolve()
      val fieldClass = typeToFieldClass(propType)
      if (fieldClass == null) {
        logger.error("Unsupported @Setting property type for $propName: ${propType.declaration.qualifiedName?.asString()}", p)
        continue
      }

      val metaBlock = CodeBlock.builder()
        .add("%T(\n", settingMeta)
        .indent()
        .add("title = %S,\n", title)
        .add("description = %S,\n", description)
        .add("category = ")
        .add(category).add(",\n")
        .add("type = ")
        .add(type).add(",\n")
        .add("key = %S,\n", keyName)
        .add("dependsOn = %S,\n", dependsOn)
        .add("min = %Lf,\n", min)
        .add("max = %Lf,\n", max)
        .add("step = %Lf,\n", step)
        .add("options = listOf(")
        .add(options.joinToString(",") { "%S" }, *options.toTypedArray())
        .add("),\n")
        .unindent()
        .add(")")
        .build()

      fieldsCode.add("%T<%T>(\n", fieldClass, modelClass)
      fieldsCode.indent()
      fieldsCode.add("name = %S,\n", propName)
      fieldsCode.add("keyName = %S,\n", keyName)
      fieldsCode.add("meta = ").add(metaBlock).add(",\n")
      fieldsCode.add("getter = { it.%L },\n", propName)
      fieldsCode.add("setter = { m, v -> m.copy(%L = v) },\n", propName)
      fieldsCode.unindent()
      fieldsCode.add("),\n")
    }

    fieldsCode.unindent()
    fieldsCode.add(")\n")

    val typeSpec =
      TypeSpec.objectBuilder(schemaClass)
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
              ClassName("io.github.iremote.settings.core", "SettingField")
                .parameterizedBy(modelClass, STAR)
            )
          )
            .addModifiers(KModifier.OVERRIDE)
            .initializer(fieldsCode.build())
            .build()
        )
        .build()

    val fileSpec =
      FileSpec.builder(pkg, schemaName)
        .addType(typeSpec)
        .build()

    fileSpec.writeTo(codeGenerator, Dependencies(false, klass.containingFile!!))
  }
}
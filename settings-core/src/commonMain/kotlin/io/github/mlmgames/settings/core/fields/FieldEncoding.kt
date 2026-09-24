package io.github.mlmgames.settings.core.fields

internal object FieldEncoding {
    const val BOOLEAN = "b"
    const val NULLABLE_BOOLEAN = "b1"
    const val INT = "i"
    const val NULLABLE_INT = "i1"
    const val LONG = "l"
    const val NULLABLE_LONG = "l1"
    const val FLOAT = "f"
    const val NULLABLE_FLOAT = "f1"
    const val DOUBLE = "d"
    const val NULLABLE_DOUBLE = "d1"
    const val STRING = "s"
    const val NULLABLE_STRING = "s1"
    const val ENUM = "e"
    const val ENUM_ORDINAL = "eo"
    const val SERIALIZED = "j"
    const val NULLABLE_SERIALIZED = "j1"
    const val STRING_LIST = "ls"
    const val INT_LIST = "li"
    const val LONG_LIST = "ll"
    const val MAP = "m"
    const val STRING_SET = "ss"
    const val NULL = "n"

    fun encode(tag: String, payload: String): String = "$tag:$payload"

    fun tagged(encoded: String, vararg allowedTags: String): TaggedValue {
        val separator = encoded.indexOf(':')
        if (separator <= 0) {
            throw IllegalArgumentException("Missing type tag: $encoded")
        }

        val tag = encoded.substring(0, separator)
        if (tag !in allowedTags) {
            throw IllegalArgumentException(
                "Invalid type tag '$tag' for ${allowedTags.joinToString(",")}: $encoded",
            )
        }

        return TaggedValue(tag, encoded.substring(separator + 1))
    }

    fun parseBoolean(payload: String): Boolean = when (payload) {
        "true" -> true
        "false" -> false
        else -> throw IllegalArgumentException("Invalid Boolean payload: $payload")
    }

    fun parseInt(payload: String): Int = payload.toIntOrNull()
        ?: throw IllegalArgumentException("Invalid Int payload: $payload")

    fun parseLong(payload: String): Long = payload.toLongOrNull()
        ?: throw IllegalArgumentException("Invalid Long payload: $payload")

    fun parseFloat(payload: String): Float = payload.toFloatOrNull()
        ?: throw IllegalArgumentException("Invalid Float payload: $payload")

    fun parseDouble(payload: String): Double = payload.toDoubleOrNull()
        ?: throw IllegalArgumentException("Invalid Double payload: $payload")
}

internal data class TaggedValue(
    val tag: String,
    val payload: String,
)

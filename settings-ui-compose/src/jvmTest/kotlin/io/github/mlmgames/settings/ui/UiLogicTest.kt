package io.github.mlmgames.settings.ui

import io.github.mlmgames.settings.core.SettingField
import io.github.mlmgames.settings.core.SettingMeta
import io.github.mlmgames.settings.core.resources.StringResourceProvider
import io.github.mlmgames.settings.core.types.Dropdown
import kotlin.test.Test
import kotlin.test.assertEquals

private object TestCategory

private data class TestModel(val value: String)

private class TestField(
    override val name: String = "value",
    override val keyName: String = "value",
    override val meta: SettingMeta? = null,
    private val options: List<String>? = null,
) : SettingField<TestModel, String> {
    override fun get(model: TestModel): String = model.value
    override fun set(model: TestModel, value: String): TestModel = model.copy(value = value)
    override fun read(prefs: androidx.datastore.preferences.core.Preferences): String? = null
    override fun write(prefs: androidx.datastore.preferences.core.MutablePreferences, value: String) = Unit
    override fun getDropdownOptions(): List<String>? = options
}

private class TestResources(
    private val labels: List<String>,
    private val fail: Boolean = false,
    private val keyLabels: List<String> = emptyList(),
    private val strings: Map<String, String> = emptyMap(),
    private val resourceStrings: Map<Int, String> = emptyMap(),
) : StringResourceProvider {
    override fun getString(resId: Int): String = resourceStrings[resId].orEmpty()
    override fun getString(resId: Int, vararg formatArgs: Any): String = resourceStrings[resId].orEmpty()
    override fun getStringArray(resId: Int): List<String> {
        if (fail) error("resource failure")
        return labels
    }

    override fun getString(key: String): String = strings[key].orEmpty()
    override fun getStringArray(key: String): List<String> = keyLabels
}

private fun meta(
    options: List<String> = emptyList(),
    optionsRes: Int = 0,
    optionsKey: String = "",
): SettingMeta = SettingMeta(
    title = "Value",
    description = "",
    titleRes = 0,
    descriptionRes = 0,
    category = TestCategory::class,
    categoryOrder = 0,
    type = Dropdown::class,
    key = "value",
    dependsOn = "",
    min = 0f,
    max = 1f,
    step = 1f,
    options = options,
    optionsRes = optionsRes,
    optionsKey = optionsKey,
)

class UiLogicTest {
    @Test
    fun resourceOptionsAreUsedWhenCardinalityMatches() {
        val field = TestField(options = listOf("first", "second"))
        val resolved = resolveDropdownLabels(
            field,
            meta(options = listOf("one", "two"), optionsRes = 1),
            TestResources(listOf("localized first", "localized second")),
        )

        assertEquals(listOf("localized first", "localized second"), resolved)
    }

    @Test
    fun keyTextTakesPrecedenceOverLegacyResource() {
        val provider = TestResources(
            labels = emptyList(),
            strings = mapOf("settings.title" to "key title"),
            resourceStrings = mapOf(7 to "resource title"),
        )
        val resolved = meta().copy(titleKey = "settings.title", titleRes = 7).resolvedTitle(provider)

        assertEquals("key title", resolved)
    }

    @Test
    fun missingKeyFallsBackToLegacyResource() {
        val provider = TestResources(
            labels = emptyList(),
            resourceStrings = mapOf(7 to "resource title"),
        )
        val resolved = meta().copy(titleKey = "settings.missing", titleRes = 7).resolvedTitle(provider)

        assertEquals("resource title", resolved)
    }

    @Test
    fun keyOptionsTakePrecedenceOverResourceOptions() {
        val field = TestField(options = listOf("first", "second"))
        val resolved = resolveDropdownLabels(
            field,
            meta(
                options = listOf("one", "two"),
                optionsRes = 1,
                optionsKey = "settings.options",
            ),
            TestResources(
                labels = listOf("resource first", "resource second"),
                keyLabels = listOf("key first", "key second"),
            ),
        )

        assertEquals(listOf("key first", "key second"), resolved)
    }

    @Test
    fun mismatchedResourceOptionsFallBackToFieldLabels() {
        val field = TestField(options = listOf("first", "second"))
        val resolved = resolveDropdownLabels(
            field,
            meta(options = listOf("one", "two"), optionsRes = 1),
            TestResources(listOf("only one")),
        )

        assertEquals(listOf("first", "second"), resolved)
    }

    @Test
    fun resourceFailureFallsBackToDeclaredOptions() {
        val field = TestField(options = null)
        val resolved = resolveDropdownLabels(
            field,
            meta(options = listOf("one", "two"), optionsRes = 1),
            TestResources(emptyList(), fail = true),
        )

        assertEquals(listOf("one", "two"), resolved)
    }

    @Test
    fun sliderFormattingIsSafeForInvalidInput() {
        assertEquals("0", formatSliderValue(Float.NaN, 0f))
        assertEquals("-1", formatSliderValue(-1.4f, 1f))
    }
}

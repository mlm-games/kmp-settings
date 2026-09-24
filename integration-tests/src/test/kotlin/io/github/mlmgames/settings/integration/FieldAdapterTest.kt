package io.github.mlmgames.settings.integration

import kotlin.test.Test
import kotlin.test.assertEquals

class FieldAdapterTest {
    @Test
    fun supportsDocumentedNumericAndStringAdapters() {
        val settings = IntegrationSettingsSchema.default
        val longSlider = requireNotNull(IntegrationSettingsSchema.fieldByName("longLevel"))
        val doubleSlider = requireNotNull(IntegrationSettingsSchema.fieldByName("doubleLevel"))
        val textDropdown = requireNotNull(IntegrationSettingsSchema.fieldByName("textChoice"))

        assertEquals(8L, longSlider.fromUiSliderValue(7.6f))
        assertEquals(0.7, doubleSlider.fromUiSliderValue(0.7f))
        assertEquals(0, textDropdown.toUiDropdownIndex(settings))
        assertEquals("two", textDropdown.fromUiDropdownIndex(1))

        val nullableToggle = requireNotNull(NullableUISettingsSchema.fieldByName("toggle"))
        val nullableSlider = requireNotNull(NullableUISettingsSchema.fieldByName("slider"))
        assertEquals(true, nullableToggle.fromUiToggleValue(true))
        assertEquals(4, nullableSlider.fromUiSliderValue(4.2f))
    }
}

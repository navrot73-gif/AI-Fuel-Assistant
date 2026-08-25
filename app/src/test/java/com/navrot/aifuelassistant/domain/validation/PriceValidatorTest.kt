package com.navrot.aifuelassistant.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Test

class PriceValidatorTest {

    @Test
    fun `validate returns Empty when input is blank`() {
        assertEquals(PriceValidator.ValidationResult.Empty, PriceValidator.validate(""))
        assertEquals(PriceValidator.ValidationResult.Empty, PriceValidator.validate("   "))
    }

    @Test
    fun `validate returns InvalidFormat when input cannot be parsed to Double`() {
        assertEquals(PriceValidator.ValidationResult.InvalidFormat, PriceValidator.validate("abc"))
        assertEquals(PriceValidator.ValidationResult.InvalidFormat, PriceValidator.validate("12.34.56"))
    }

    @Test
    fun `validate returns TooLow when price is less than MIN_PRICE`() {
        assertEquals(PriceValidator.ValidationResult.TooLow, PriceValidator.validate("0.99"))
        assertEquals(PriceValidator.ValidationResult.TooLow, PriceValidator.validate("0"))
        assertEquals(PriceValidator.ValidationResult.TooLow, PriceValidator.validate("-50.0"))
    }

    @Test
    fun `validate returns TooHigh when price is greater than MAX_PRICE`() {
        assertEquals(PriceValidator.ValidationResult.TooHigh, PriceValidator.validate("999.01"))
        assertEquals(PriceValidator.ValidationResult.TooHigh, PriceValidator.validate("1000"))
    }

    @Test
    fun `validate returns Valid for prices within range with dot or comma`() {
        assertEquals(PriceValidator.ValidationResult.Valid, PriceValidator.validate("1.0"))
        assertEquals(PriceValidator.ValidationResult.Valid, PriceValidator.validate("999.0"))
        assertEquals(PriceValidator.ValidationResult.Valid, PriceValidator.validate("65,5"))
        assertEquals(PriceValidator.ValidationResult.Valid, PriceValidator.validate("55.40"))
    }
}

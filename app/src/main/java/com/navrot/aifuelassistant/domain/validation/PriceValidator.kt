package com.navrot.aifuelassistant.domain.validation

object PriceValidator {
    const val MIN_PRICE = 1.0
    const val MAX_PRICE = 999.0

    sealed class ValidationResult {
        object Valid : ValidationResult()
        object Empty : ValidationResult()
        object TooLow : ValidationResult()
        object TooHigh : ValidationResult()
        object InvalidFormat : ValidationResult()
    }

    fun validate(input: String): ValidationResult {
        if (input.isBlank()) return ValidationResult.Empty
        val price = input.replace(",", ".").toDoubleOrNull()
            ?: return ValidationResult.InvalidFormat
        return when {
            price < MIN_PRICE -> ValidationResult.TooLow
            price > MAX_PRICE -> ValidationResult.TooHigh
            else -> ValidationResult.Valid
        }
    }
}

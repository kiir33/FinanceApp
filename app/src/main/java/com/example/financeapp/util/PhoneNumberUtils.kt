package com.example.financeapp.util

/**
 * Single source of truth for phone-number normalization.
 *
 * Phone numbers are stored and compared in their digit-only form so that "+91 98765 43210",
 * "919876543210" and "9876543210" cannot slip past the unique index as separate members.
 */
fun normalizePhoneNumber(phoneNumber: String): String = phoneNumber.filter { it.isDigit() }

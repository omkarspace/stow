package com.cleansaver.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Primary Accents (Modern Electric Indigo & Violet)
val PrimaryIndigo = Color(0xFF6366F1)
val PrimaryIndigoVariant = Color(0xFF4F46E5)
val SecondaryViolet = Color(0xFF8B5CF6)
val TertiaryCyan = Color(0xFF06B6D4)

// Dark Theme Surfaces (Deep Slate & Midnight)
val DarkBackground = Color(0xFF0B0F19)
val DarkSurface = Color(0xFF111827)
val DarkSurfaceVariant = Color(0xFF1E293B)
val DarkCard = Color(0xFF1A2234)
val DarkBorder = Color(0xFF2E384D)

// Light Theme Surfaces (Soft Off-White & Canvas)
val LightBackground = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF1F5F9)
val LightCard = Color(0xFFFFFFFF)
val LightBorder = Color(0xFFE2E8F0)

// Text Colors
val DarkTextPrimary = Color(0xFFF9FAFB)
val DarkTextSecondary = Color(0xFF94A3B8)
val DarkTextMuted = Color(0xFF64748B)

val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF475569)
val LightTextMuted = Color(0xFF94A3B8)

// Status & Semantic Colors
val SuccessGreen = Color(0xFF10B981)
val SuccessGreenContainer = Color(0xFF064E3B)
val ErrorRed = Color(0xFFF43F5E)
val ErrorRedContainer = Color(0xFF4C0519)
val WarningAmber = Color(0xFFF59E0B)

// Gradients for CTAs & Badges
val BrandGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
)

val GlowGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF6366F1).copy(alpha = 0.35f), Color(0xFF8B5CF6).copy(alpha = 0.1f))
)

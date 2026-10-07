package com.inventoryscanner.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** MAF brand palette from Stock Compass: brown, gold, warm white. */
object Brand {
    val Brown = Color(0xFF6B3410)
    val Brown2 = Color(0xFF94481A)
    val BrownDark = Color(0xFF2A1609)
    val Gold = Color(0xFFB8860B)
    val GoldSoft = Color(0xFFF3E6C4)
    val Bg = Color(0xFFFAF7F2)
    val Line = Color(0xFFE8DFD2)
    val Ink = Color(0xFF2B2118)
    val Muted = Color(0xFF7A6A5A)
    val Good = Color(0xFF2E7D4F)
    val Warn = Color(0xFFB7791F)
    val Bad = Color(0xFFB3261E)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Brand.Brown, onPrimary = Color.White,
            primaryContainer = Brand.GoldSoft, onPrimaryContainer = Brand.BrownDark,
            secondary = Brand.Gold, onSecondary = Color.White,
            secondaryContainer = Brand.GoldSoft, onSecondaryContainer = Brand.BrownDark,
            background = Brand.Bg, onBackground = Brand.Ink,
            surface = Color.White, onSurface = Brand.Ink,
            surfaceVariant = Brand.GoldSoft, onSurfaceVariant = Brand.Muted,
            outline = Brand.Line, error = Brand.Bad,
        ),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun brandBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = Brand.Brown, titleContentColor = Color.White,
    navigationIconContentColor = Color.White, actionIconContentColor = Color.White,
)

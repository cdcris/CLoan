package org.example.project

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import org.example.project.shared.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val Forest = Color(0xFF153E35)
internal val Citron = Color(0xFFDAF39A)

private val lightPalette = lightColorScheme(
    primary = Color(0xFF205747), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EBDB), onPrimaryContainer = Forest,
    secondary = Color(0xFF4E6841), onSecondary = Color.White,
    secondaryContainer = Citron, onSecondaryContainer = Forest,
    tertiary = Color(0xFF795A26), tertiaryContainer = Color(0xFFF6E3BC),
    onTertiaryContainer = Color(0xFF543B12),
    background = Color(0xFFF5F5EE), onBackground = Color(0xFF182D25),
    surface = Color(0xFFFFFEF9), onSurface = Color(0xFF182D25),
    surfaceVariant = Color(0xFFE9EDE3), onSurfaceVariant = Color(0xFF526157),
    surfaceContainerLow = Color(0xFFF7F8F1), surfaceContainer = Color(0xFFF0F2E9),
    surfaceContainerHigh = Color(0xFFE9EDE3), surfaceContainerHighest = Color(0xFFE4E9DD),
    outline = Color(0xFF77857A), outlineVariant = Color(0xFFD3DACF),
)

private val darkPalette = darkColorScheme(
    primary = Color(0xFFA3D6BB), onPrimary = Forest,
    primaryContainer = Color(0xFF284E40), onPrimaryContainer = Color(0xFFD6EBDB),
    secondary = Citron, onSecondary = Forest,
    secondaryContainer = Color(0xFF42542E), onSecondaryContainer = Citron,
    tertiary = Color(0xFFE5C78C), tertiaryContainer = Color(0xFF564320),
    onTertiaryContainer = Color(0xFFF6E3BC),
    background = Color(0xFF101914), onBackground = Color(0xFFE6ECE3),
    surface = Color(0xFF1C2820), onSurface = Color(0xFFE6ECE3),
    surfaceVariant = Color(0xFF2E3B31), onSurfaceVariant = Color(0xFFBAC9BC),
    surfaceContainerLow = Color(0xFF17221B), surfaceContainer = Color(0xFF202C23),
    surfaceContainerHigh = Color(0xFF29362C), surfaceContainerHighest = Color(0xFF314035),
    outline = Color(0xFF89998C), outlineVariant = Color(0xFF405145),
)

private val baseType = Typography()
private val ledgerType = baseType.copy(
    headlineMedium = baseType.headlineMedium.copy(
        fontFamily = FontFamily.Serif, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-1).sp,
    ),
    titleLarge = baseType.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium),
    labelMedium = baseType.labelMedium.copy(letterSpacing = 0.6.sp),
    labelSmall = baseType.labelSmall.copy(fontWeight = FontWeight.Medium),
)

@Composable
internal fun LedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkPalette else lightPalette,
        typography = ledgerType,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}

@Composable
internal fun ledgerFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
)

@Composable
internal fun LedgerEmptyIcon(icon: Int) {
    Box(
        Modifier.padding(bottom = 12.dp).size(48.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
internal fun LedgerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled, shape = MaterialTheme.shapes.small, content = content)
}

@Composable
internal fun MenuSelector(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_expand), contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label)
        }
    }
}

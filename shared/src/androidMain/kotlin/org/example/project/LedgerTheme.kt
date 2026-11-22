package org.example.project

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.shared.R

internal val BrandBrown = Color(0xFF935539)
internal val LedgerInk = Color(0xFF232220)
private val charcoal = LedgerInk
private val lightCard = Color(0xFFF6F4F1)
private val peach = Color(0xFFF4DCCE)

private val lightPalette = lightColorScheme(
    primary = BrandBrown, onPrimary = Color.White,
    primaryContainer = peach, onPrimaryContainer = charcoal,
    secondary = BrandBrown, onSecondary = Color.White,
    secondaryContainer = peach, onSecondaryContainer = charcoal,
    tertiary = Color(0xFF77582A), tertiaryContainer = Color(0xFFF6E3BC),
    onTertiaryContainer = Color(0xFF543B12),
    background = Color.White, onBackground = charcoal,
    surface = lightCard, onSurface = charcoal,
    surfaceVariant = lightCard, onSurfaceVariant = Color.DarkGray,
    surfaceContainerLow = Color(0xFFFAF9F7), surfaceContainer = lightCard,
    surfaceContainerHigh = lightCard, surfaceContainerHighest = Color(0xFFE9E9E9),
    outline = Color.Gray, outlineVariant = Color(0xFFD8D8D8),
)

private val darkPalette = darkColorScheme(
    primary = Color(0xFFF0B69A), onPrimary = charcoal,
    primaryContainer = BrandBrown, onPrimaryContainer = Color.White,
    secondary = peach, onSecondary = charcoal,
    secondaryContainer = Color(0xFF583728), onSecondaryContainer = peach,
    tertiary = Color(0xFFE5C78C), tertiaryContainer = Color(0xFF564320),
    onTertiaryContainer = Color(0xFFF6E3BC),
    background = Color(0xFF171614), onBackground = Color.White,
    surface = Color(0xFF2D2D2D), onSurface = lightCard,
    surfaceVariant = Color(0xFF373737), onSurfaceVariant = Color.LightGray,
    surfaceContainerLow = charcoal, surfaceContainer = Color(0xFF2D2D2D),
    surfaceContainerHigh = Color(0xFF363636), surfaceContainerHighest = Color.DarkGray,
    outline = Color.LightGray, outlineVariant = Color(0xFF505050),
)

private val baseType = Typography()
private val ledgerType = baseType.copy(
    headlineMedium = baseType.headlineMedium.copy(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-1).sp,
    ),
    titleLarge = baseType.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    labelMedium = baseType.labelMedium.copy(letterSpacing = 0.2.sp),
    labelSmall = baseType.labelSmall.copy(fontWeight = FontWeight.Medium),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LedgerTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
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
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    unfocusedBorderColor = Color.Transparent,
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
    Button(onClick = onClick, modifier = modifier.defaultMinSize(minHeight = 48.dp), enabled = enabled, shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(containerColor = BrandBrown, contentColor = Color.White), content = content)
}

@Composable
internal fun LedgerFilter(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) },
        shape = MaterialTheme.shapes.small, border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            selectedContainerColor = BrandBrown, selectedLabelColor = Color.White,
        ),
    )
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

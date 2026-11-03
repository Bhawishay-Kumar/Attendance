package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SubjectCategory
import com.example.ui.theme.LocalGlassTheme
import com.example.ui.theme.SpaceGroteskFontFamily

@Composable
fun CategoryFilterChips(
    activeFilter: SubjectCategory,
    onFilterSelected: (SubjectCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassTheme.current
    val filterItems = listOf(
        SubjectCategory.ALL to "All Periods",
        SubjectCategory.MAJOR to "Major",
        SubjectCategory.MINOR to "Minor / Honours",
        SubjectCategory.LIBRARY to "Library & Auto"
    )

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filterItems) { (category, label) ->
            val isSelected = activeFilter == category
            val chipBg by animateColorAsState(
                targetValue = if (isSelected) Color(0xFF6366F1) else glass.cardSurface,
                label = "chipBg"
            )
            val chipText by animateColorAsState(
                targetValue = if (isSelected) Color.White else glass.textSecondary,
                label = "chipText"
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9999.dp))
                    .background(chipBg)
                    .border(
                        1.dp,
                        if (isSelected) Color(0xFF818CF8) else glass.cardBorder,
                        RoundedCornerShape(9999.dp)
                    )
                    .clickable { onFilterSelected(category) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
                    .testTag("filter_${category.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 11.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = chipText
                )
            }
        }
    }
}

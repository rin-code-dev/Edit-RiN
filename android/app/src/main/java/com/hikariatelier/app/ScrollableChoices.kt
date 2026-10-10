package com.hikariatelier.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
internal fun ScrollableChoiceGroup(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Surface(
                modifier = Modifier.selectable(
                    selected = selected,
                    onClick = { onSelect(index) },
                    role = Role.RadioButton
                ),
                shape = RoundedCornerShape(14.dp),
                color = if (selected) colors.primaryContainer else colors.surface,
                contentColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant)
            ) {
                Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

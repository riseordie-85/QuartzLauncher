package com.tim.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim.launcher.ui.Q

/**
 * Quartz's widgets as live pictures, on a four-column grid like the home screen's (a 1 × 1 takes one column, a
 * forecast all four), grouped, with buttons along the top to show just one group. [current] is outlined. [footer]
 * goes after them all (the other apps' widgets, in the Add a widget list).
 */
@Composable
internal fun WidgetGallery(
    kinds: List<WidgetKind>,
    current: WidgetKind?,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
    onPick: (WidgetKind) -> Unit,
) {
    var group by remember { mutableStateOf<WidgetGroup?>(null) }
    val groups = remember(kinds) { WidgetGroup.entries.filter { g -> kinds.any { it.group == g } } }
    Column(modifier) {
        if (groups.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (listOf<WidgetGroup?>(null) + groups).forEach { g ->
                    val on = g == group
                    Text(
                        g?.label ?: "All",
                        color = if (on) Q.Text else Q.TextDim,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (on) Q.Accent.copy(alpha = 0.85f) else Q.Glass)
                            .clickable { group = g }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
        LazyVerticalGrid(
            GridCells.Fixed(4),
            Modifier.fillMaxWidth().weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            (if (group == null) groups else listOf(group!!)).forEach { g ->
                val inGroup = kinds.filter { it.group == g }
                item(span = { GridItemSpan(maxLineSpan) }, key = "head-$g") {
                    Text(g.label, color = Q.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
                }
                items(inGroup, key = { it.name }, span = { GridItemSpan(it.w.coerceIn(1, 4)) }) { kind ->
                    WidgetTile(kind, kind == current) { onPick(kind) }
                }
            }
            if (footer != null && group == null) item(span = { GridItemSpan(maxLineSpan) }, key = "footer") { footer() }
        }
    }
}

/** One widget's picture (made-up weather and today's date), and its name. */
@Composable
private fun WidgetTile(kind: WidgetKind, current: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = if (current) 0.14f else 0.05f))
            .border(if (current) 2.dp else 0.dp, if (current) Q.Accent else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // About its shape on the home screen: cells are about half again as wide as tall
        val ratio = when (kind) {
            WidgetKind.CLOCK -> 2.6f
            WidgetKind.FLIP -> 2.07f
            WidgetKind.FLIP_WEATHER -> 1.89f
            WidgetKind.TIPS -> 2.4f
            else -> kind.w * 1.5f / kind.h
        }
        Box(Modifier.fillMaxWidth().aspectRatio(ratio).padding(2.dp), contentAlignment = Alignment.Center) {
            WidgetView(kind, Modifier.fillMaxWidth().aspectRatio(ratio), sample = true)
        }
        Text(
            kind.label,
            color = if (current) Q.Text else Q.TextDim,
            fontSize = if (kind.w <= 1) 11.sp else 13.sp,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

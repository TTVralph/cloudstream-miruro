@file:Suppress("UNUSED_PARAMETER")

package androidx.compose.foundation.layout

import androidx.compose.ui.Modifier

/**
 * Compatibility symbol for source files that explicitly import
 * androidx.compose.foundation.layout.weight.
 *
 * Compose's real RowScope/ColumnScope Modifier.weight member extension has
 * higher resolution priority inside Row and Column content, so actual layout
 * behavior remains unchanged. This declaration only prevents Kotlin from
 * resolving that explicit import to AndroidX's internal parent-data property.
 */
fun Modifier.weight(weight: Float, fill: Boolean = true): Modifier = this

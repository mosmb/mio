package io.morgan.mio.core.designsystem.accessibility

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

public fun Modifier.mioHeading(): Modifier = semantics { heading() }

public fun Modifier.mioSelected(selected: Boolean): Modifier = semantics { this.selected = selected }

public fun Modifier.mioDecorative(): Modifier = clearAndSetSemantics { }

public fun Modifier.mioSemanticGroup(): Modifier = semantics(mergeDescendants = true) { }

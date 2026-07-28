package com.patrykandpatrick.vico.compose.common.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.core.common.Dimensions
import com.patrykandpatrick.vico.core.common.component.LineComponent
import com.patrykandpatrick.vico.core.common.shape.Shape

@Composable
fun rememberUnboundedLineComponent(
    color: Color,
    thickness: Dp = 1.dp,
    shape: Shape = Shape.Rectangle,
    margins: Dimensions = Dimensions.Empty,
    verticalAddDrawSpace: Dp = 0.dp,
): LineComponent {
    return rememberLineComponent(
        color = color,
        thickness = thickness,
        shape = shape,
        margins = margins,
    )
}

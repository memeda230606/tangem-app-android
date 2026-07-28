package com.patrykandpatrick.vico.compose.cartesian.axis

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.BaseAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.common.Defaults
import com.patrykandpatrick.vico.core.common.component.LineComponent
import com.patrykandpatrick.vico.core.common.component.TextComponent

@Composable
fun rememberCustomStartAxis(
    line: LineComponent? = null,
    label: TextComponent? = null,
    labelRotationDegrees: Float = Defaults.AXIS_LABEL_ROTATION_DEGREES,
    horizontalLabelPosition: VerticalAxis.HorizontalLabelPosition = VerticalAxis.HorizontalLabelPosition.Outside,
    verticalLabelPosition: VerticalAxis.VerticalLabelPosition = VerticalAxis.VerticalLabelPosition.Center,
    valueFormatter: CartesianValueFormatter = CartesianValueFormatter.decimal(),
    tick: LineComponent? = null,
    tickLength: Dp = Defaults.AXIS_TICK_LENGTH.dp,
    guideline: LineComponent? = null,
    labelGuideline: LineComponent? = null,
    itemPlacer: VerticalAxis.ItemPlacer = VerticalAxis.ItemPlacer.step(),
    sizeConstraint: BaseAxis.SizeConstraint = BaseAxis.SizeConstraint.Auto(),
    titleComponent: TextComponent? = null,
    title: CharSequence? = null,
): VerticalAxis<Axis.Position.Vertical.Start> {
    return rememberStartAxis(
        line = line,
        label = label,
        labelRotationDegrees = labelRotationDegrees,
        horizontalLabelPosition = horizontalLabelPosition,
        verticalLabelPosition = verticalLabelPosition,
        valueFormatter = valueFormatter,
        tick = tick,
        tickLength = tickLength,
        guideline = guideline ?: labelGuideline,
        itemPlacer = itemPlacer,
        sizeConstraint = sizeConstraint,
        titleComponent = titleComponent,
        title = title,
    )
}

@Composable
fun rememberMinMaxStartAxis(
    line: LineComponent? = null,
    label: TextComponent? = null,
    labelRotationDegrees: Float = Defaults.AXIS_LABEL_ROTATION_DEGREES,
    horizontalLabelPosition: VerticalAxis.HorizontalLabelPosition = VerticalAxis.HorizontalLabelPosition.Outside,
    verticalLabelPosition: VerticalAxis.VerticalLabelPosition = VerticalAxis.VerticalLabelPosition.Center,
    valueFormatter: CartesianValueFormatter = CartesianValueFormatter.decimal(),
    tick: LineComponent? = null,
    tickLength: Dp = Defaults.AXIS_TICK_LENGTH.dp,
    guideline: LineComponent? = null,
    labelGuideline: LineComponent? = null,
    itemPlacer: VerticalAxis.ItemPlacer = VerticalAxis.ItemPlacer.step(),
    sizeConstraint: BaseAxis.SizeConstraint = BaseAxis.SizeConstraint.Auto(),
    titleComponent: TextComponent? = null,
    title: CharSequence? = null,
): VerticalAxis<Axis.Position.Vertical.Start> {
    return rememberCustomStartAxis(
        line = line,
        label = label,
        labelRotationDegrees = labelRotationDegrees,
        horizontalLabelPosition = horizontalLabelPosition,
        verticalLabelPosition = verticalLabelPosition,
        valueFormatter = valueFormatter,
        tick = tick,
        tickLength = tickLength,
        guideline = guideline,
        labelGuideline = labelGuideline,
        itemPlacer = itemPlacer,
        sizeConstraint = sizeConstraint,
        titleComponent = titleComponent,
        title = title,
    )
}

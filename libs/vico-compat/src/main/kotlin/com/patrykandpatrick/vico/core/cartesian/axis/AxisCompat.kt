package com.patrykandpatrick.vico.core.cartesian.axis

typealias AxisPosition = Axis.Position

class MidVerticalAxisItemPlacer(
    shiftExtremeLines: Boolean,
) : VerticalAxis.ItemPlacer by VerticalAxis.ItemPlacer.count({ 3 }, shiftExtremeLines)

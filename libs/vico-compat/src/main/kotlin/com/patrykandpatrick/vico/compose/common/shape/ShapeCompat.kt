package com.patrykandpatrick.vico.compose.common.shape

import androidx.compose.ui.unit.Dp
import com.patrykandpatrick.vico.core.common.shape.Shape

fun Shape.Companion.dashed(
    shape: Shape,
    dashLength: Dp,
    gapLength: Dp,
): Shape = shape

package com.patrykandpatrick.vico.compose.cartesian.layer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.Defaults
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.common.shader.DynamicShader

@Composable
fun rememberLine(
    shader: DynamicShader,
    thickness: Dp = Defaults.LINE_SPEC_THICKNESS_DP.dp,
    backgroundShader: DynamicShader? = null,
): LineCartesianLayer.Line {
    val fill = remember(shader) {
        LineCartesianLayer.LineFill.single(Fill(shader))
    }
    val areaFill = remember(backgroundShader) {
        backgroundShader?.let { LineCartesianLayer.AreaFill.single(Fill(it)) }
    }

    return rememberLine(
        fill = fill,
        thickness = thickness,
        areaFill = areaFill,
    )
}

@Composable
fun rememberSplitLine(
    shader: DynamicShader,
    backgroundShaderFirst: DynamicShader,
    backgroundShaderSecond: DynamicShader,
    xSplitFraction: Float,
    thickness: Dp = Defaults.LINE_SPEC_THICKNESS_DP.dp,
): LineCartesianLayer.Line {
    val lineFill = remember(shader) {
        LineCartesianLayer.LineFill.single(Fill(shader))
    }
    val areaFill = remember(backgroundShaderFirst, backgroundShaderSecond, xSplitFraction) {
        LineCartesianLayer.AreaFill.double(
            Fill(backgroundShaderFirst),
            Fill(backgroundShaderSecond),
        ) { xSplitFraction }
    }

    return rememberLine(
        fill = lineFill,
        thickness = thickness,
        areaFill = areaFill,
    )
}

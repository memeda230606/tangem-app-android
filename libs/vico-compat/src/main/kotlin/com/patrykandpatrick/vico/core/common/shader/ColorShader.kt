package com.patrykandpatrick.vico.core.common.shader

import android.graphics.LinearGradient
import android.graphics.RectF
import android.graphics.Shader
import com.patrykandpatrick.vico.core.common.DrawContext

class ColorShader(
    private val color: Int,
) : DynamicShader {

    override fun provideShader(context: DrawContext, bounds: RectF): Shader {
        return provideShader(context, bounds.left, bounds.top, bounds.right, bounds.bottom)
    }

    override fun provideShader(
        context: DrawContext,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): Shader {
        return LinearGradient(left, top, right, bottom, color, color, Shader.TileMode.CLAMP)
    }
}

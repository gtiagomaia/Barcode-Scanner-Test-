package com.shipnow.qrcodescannertest.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View


/**
 * Created by Tiago Maia on 15/02/2021.
 */
class BarcodeBoundsOverlay constructor(context: Context, attributeSet: AttributeSet?) :
    View(context, attributeSet) {

    private val barcodeBounds: MutableList<RectF> = mutableListOf()
    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.YELLOW
        strokeWidth = 10f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        barcodeBounds.forEach { canvas.drawRect(it, paint) }
    }

    fun drawBarcodeBounds(faceBounds: List<RectF>) {
        this.barcodeBounds.clear()
        this.barcodeBounds.addAll(faceBounds)
        invalidate()
    }
}
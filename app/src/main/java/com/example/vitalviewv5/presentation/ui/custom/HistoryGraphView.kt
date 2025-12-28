package com.example.vitalviewv5.presentation.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

class HistoryGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint().apply {
        color = Color.parseColor("#00E5FF") // Default Neon Cyan
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private val dotPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    // Grid lines (optional, keep it clean for now)
    private val gridPaint = Paint().apply {
        color = Color.DKGRAY
        strokeWidth = 2f
    }

    private var dataPoints: List<Float> = emptyList()
    private val path = Path()

    fun setData(data: List<Float>) {
        this.dataPoints = data
        invalidate()
    }
    
    fun setLineColor(color: Int) {
        linePaint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (dataPoints.isEmpty()) return

        val width = width.toFloat()
        val height = height.toFloat()
        val padding = 40f
        
        val plotWidth = width - (padding * 2)
        val plotHeight = height - (padding * 2)

        val maxVal = (dataPoints.maxOrNull() ?: 100f) * 1.1f // Add 10% headroom
        val minVal = (dataPoints.minOrNull() ?: 0f) * 0.9f
        val range = maxVal - minVal

        val stepX = if (dataPoints.size > 1) plotWidth / (dataPoints.size - 1) else 0f

        path.reset()
        
        dataPoints.forEachIndexed { index, value ->
            val x = padding + (index * stepX)
            // Invert Y axis because canvas 0 is at top
            val y = height - padding - ((value - minVal) / range * plotHeight)
            
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
            
            // Draw dot
            canvas.drawCircle(x, y, 8f, dotPaint)
        }

        canvas.drawPath(path, linePaint)
    }
}

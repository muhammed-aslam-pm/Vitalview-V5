package com.example.vitalviewv5.presentation.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.example.vitalviewv5.domain.model.SleepData
import com.example.vitalviewv5.domain.model.SleepLevel

class SleepChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var sleepStages: List<SleepData> = emptyList()
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barRect = RectF()

    // Colors from the image palette
    private val deepSleepColor = Color.parseColor("#4A148C") // Rich Purple
    private val lightSleepColor = Color.parseColor("#9575CD") // Light Purple
    private val remSleepColor = Color.parseColor("#F06292") // Pink/Magenta
    private val awakeColor = Color.parseColor("#E0E0E0") // Light Grey/White

    fun setSleepData(data: List<SleepData>) {
        this.sleepStages = data.sortedBy { it.timestamp }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (sleepStages.isEmpty()) return

        val width = width.toFloat()
        val height = height.toFloat()
        val totalStages = sleepStages.size
        val barWidth = width / totalStages

        sleepStages.forEachIndexed { index, data ->
            val left = index * barWidth
            val right = left + barWidth
            
            // Set height and color based on stage
            // Awake: Highest, Deep: Lowest
            val (topFactor, color) = when (data.sleepLevel) {
                SleepLevel.DEEP_SLEEP -> 0.8f to deepSleepColor
                SleepLevel.LIGHT_SLEEP -> 0.6f to lightSleepColor
                SleepLevel.REM -> 0.4f to remSleepColor
                SleepLevel.AWAKE -> 0.2f to awakeColor
            }

            paint.color = color
            barRect.set(left, height * topFactor, right, height)
            canvas.drawRect(barRect, paint)
        }
    }
}

package priv.lzy.andtestsuite.view

import android.graphics.Paint
import android.graphics.Rect
import androidx.annotation.ColorInt
import androidx.annotation.Px
import androidx.recyclerview.widget.RecyclerView

class GridDividerDecoration constructor(
    @Px dividerSize: Int,
    @ColorInt dividerColor: Int,
    val spanCount: Int
) : RecyclerView.ItemDecoration() {
    private val mPaint: Paint = Paint()
    private val bounds: Rect = Rect()
    init {
        mPaint.color = dividerColor
        mPaint.strokeWidth = dividerSize.toFloat()
        mPaint.style = Paint.Style.STROKE
        mPaint.isAntiAlias = true
    }
}
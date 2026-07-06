package com.quorvia.app.feature.explore

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.BitmapDescriptorFactory

object MapMarkerHelper {
    private var cachedUserLocation: BitmapDescriptor? = null
    private var cachedQuantumTarget: BitmapDescriptor? = null
    private var cachedOrigin: BitmapDescriptor? = null

    private fun dpToPx(context: Context, dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }

    @Synchronized
    fun getUserLocationMarker(context: Context): BitmapDescriptor {
        cachedUserLocation?.let { return it }

        val sizeDp = 36f
        val sizePx = dpToPx(context, sizeDp).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            isAntiAlias = true
        }

        val centerX = sizePx / 2f
        val centerY = sizePx / 2f

        // 1. 绘制外部半透明渐变蓝圈 (半径 14dp)
        paint.color = 0x332367F4.toInt() // 20% alpha
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, dpToPx(context, 14f), paint)

        // 2. 绘制白色描边底圈 (半径 7dp)
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(centerX, centerY, dpToPx(context, 7f), paint)

        // 3. 绘制核心亮蓝圆点 (半径 5.5dp)
        paint.color = 0xFF2367F4.toInt()
        canvas.drawCircle(centerX, centerY, dpToPx(context, 5.5f), paint)

        val descriptor = BitmapDescriptorFactory.fromBitmap(bitmap)
        cachedUserLocation = descriptor
        return descriptor
    }

    @Synchronized
    fun getQuantumTargetMarker(context: Context): BitmapDescriptor {
        cachedQuantumTarget?.let { return it }

        val sizeDp = 36f
        val sizePx = dpToPx(context, sizeDp).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            isAntiAlias = true
        }

        val centerX = sizePx / 2f
        val centerY = sizePx / 2f
        val neonMagenta = 0xFFD017F4.toInt() // 霓虹紫粉色，极具量子科技感

        // 1. 绘制核心小圆点 (半径 3.5dp)
        paint.color = neonMagenta
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, dpToPx(context, 3.5f), paint)

        // 2. 绘制中圈细线环 (半径 7dp, 细线宽 1dp，50%透明度)
        paint.color = 0x88D017F4.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dpToPx(context, 1f)
        canvas.drawCircle(centerX, centerY, dpToPx(context, 7f), paint)

        // 3. 绘制外侧主圆环 (半径 11dp, 线宽 2dp，不透明)
        paint.color = neonMagenta
        paint.strokeWidth = dpToPx(context, 2f)
        canvas.drawCircle(centerX, centerY, dpToPx(context, 11f), paint)

        // 4. 绘制四个量子准星延伸线 (从 11dp 延伸到 15dp, 线宽 2f)
        val rInner = dpToPx(context, 11f)
        val rOuter = dpToPx(context, 15f)

        // 上
        canvas.drawLine(centerX, centerY - rOuter, centerX, centerY - rInner, paint)
        // 下
        canvas.drawLine(centerX, centerY + rInner, centerX, centerY + rOuter, paint)
        // 左
        canvas.drawLine(centerX - rOuter, centerY, centerX - rInner, centerY, paint)
        // 右
        canvas.drawLine(centerX + rInner, centerY, centerX + rOuter, centerY, paint)

        val descriptor = BitmapDescriptorFactory.fromBitmap(bitmap)
        cachedQuantumTarget = descriptor
        return descriptor
    }

    @Synchronized
    fun getOriginMarker(context: Context): BitmapDescriptor {
        cachedOrigin?.let { return it }

        val sizeDp = 36f
        val sizePx = dpToPx(context, sizeDp).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            isAntiAlias = true
        }

        val centerX = sizePx / 2f
        val centerY = sizePx / 2f
        val slateGray = 0xFF78909C.toInt() // 蓝灰色，低调的起点表示

        // 1. 绘制核心小圆点
        paint.color = slateGray
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, dpToPx(context, 3f), paint)

        // 2. 绘制外侧圆环 (半径 10dp, 线宽 2dp)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dpToPx(context, 2f)
        canvas.drawCircle(centerX, centerY, dpToPx(context, 10f), paint)

        // 3. 绘制准星延伸线 (从 10dp 延伸到 13dp)
        val rInner = dpToPx(context, 10f)
        val rOuter = dpToPx(context, 13f)

        // 上
        canvas.drawLine(centerX, centerY - rOuter, centerX, centerY - rInner, paint)
        // 下
        canvas.drawLine(centerX, centerY + rInner, centerX, centerY + rOuter, paint)
        // 左
        canvas.drawLine(centerX - rOuter, centerY, centerX - rInner, centerY, paint)
        // 右
        canvas.drawLine(centerX + rInner, centerY, centerX + rOuter, centerY, paint)

        val descriptor = BitmapDescriptorFactory.fromBitmap(bitmap)
        cachedOrigin = descriptor
        return descriptor
    }
}

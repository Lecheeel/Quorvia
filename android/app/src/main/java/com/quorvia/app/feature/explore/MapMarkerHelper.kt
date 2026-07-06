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
        val blueColor = 0xFF2367F4.toInt()

        // 1. 绘制核心小圆点 (半径 3dp)
        paint.color = blueColor
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, dpToPx(context, 3f), paint)

        // 2. 绘制外侧亮蓝色圆环 (半径 10dp, 线宽 2dp)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dpToPx(context, 2f)
        canvas.drawCircle(centerX, centerY, dpToPx(context, 10f), paint)

        // 3. 绘制上下左右四个量子准星延伸线 (从 10dp 延伸到 14dp, 线宽 2f)
        val rInner = dpToPx(context, 10f)
        val rOuter = dpToPx(context, 14f)

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
        val cyanColor = 0xFF00B0FF.toInt() // 亮青色

        // 1. 绘制核心小圆点
        paint.color = cyanColor
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, dpToPx(context, 3f), paint)

        // 2. 绘制外侧青色圆环 (半径 10dp, 线宽 2dp)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dpToPx(context, 2f)
        canvas.drawCircle(centerX, centerY, dpToPx(context, 10f), paint)

        // 3. 绘制准星延伸线
        val rInner = dpToPx(context, 10f)
        val rOuter = dpToPx(context, 14f)

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

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

        val descriptor = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
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
        val blue = 0xFF1976D2.toInt()

        paint.style = Paint.Style.FILL
        paint.color = 0x221976D2
        canvas.drawCircle(centerX, centerY, dpToPx(context, 14f), paint)

        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(centerX, centerY, dpToPx(context, 7f), paint)

        paint.color = blue
        canvas.drawCircle(centerX, centerY, dpToPx(context, 4.8f), paint)

        val descriptor = BitmapDescriptorFactory.fromBitmap(bitmap)
        cachedOrigin = descriptor
        return descriptor
    }
}

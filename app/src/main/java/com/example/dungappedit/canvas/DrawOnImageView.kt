package com.example.dungappedit.canvas

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.withSave
import com.example.dungappedit.model.MovableItem
import com.example.dungappedit.model.TextStyle
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class DrawOnImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        private const val MAX_STICKER_RESOLUTION = 1024f
    }

    private val movableItems = mutableListOf<MovableItem>()
    private var currentDraggedItem: MovableItem? = null

    var isDrawingEnabled = false
    private var drawPath = Path()
    private var drawSize = 10f
    private var drawColor = Color.BLACK

    private val drawPaint = Paint().apply {
        color = drawColor
        isAntiAlias = true
        strokeWidth = 10f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val borderPaint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 10f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private var drawCanvas: Canvas? = null
    private var canvasBitmap: Bitmap? = null
    private var backgroundBitmap: Bitmap? = null
    private var originalBackgroundBitmap: Bitmap? = null
    private var isErasing = false
    private var showBorder = false

    private var touchOffsetX = 0f
    private var touchOffsetY = 0f

    var imageRectLeft: Float = 0f
        private set
    var imageRectTop: Float = 0f
        private set
    var imageRectRight: Float = 0f
        private set
    var imageRectBottom: Float = 0f
        private set

    interface OnImageDimensionsChangedListener {
        fun onImageDimensionsChanged(
            left: Float, top: Float, right: Float, bottom: Float, width: Int, height: Int
        )
    }

    private var imageDimensionsListener: OnImageDimensionsChangedListener? = null

    fun setOnImageDimensionsChangedListener(listener: OnImageDimensionsChangedListener) {
        imageDimensionsListener = listener
    }

    private class ItemControls(val item: MovableItem) {
        companion object {
            const val BUTTON_SIZE = 55f
            const val BUTTON_PADDING = 10f

            val DELETE_PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED; style = Paint.Style.FILL }
            val DELETE_X_PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 6f }
            val ROTATE_PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GREEN; style = Paint.Style.FILL }
            val ROTATE_ICON_PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 6f }
            val SELECTED_BORDER_PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLUE; style = Paint.Style.STROKE; strokeWidth = 2f }
        }

        private fun getBounds(item: MovableItem): RectF {
            return when (item) {
                is MovableItem.TextItem -> item.getBounds()
                is MovableItem.ImageItem -> RectF(item.x, item.y, item.x + item.bitmap.width, item.y + item.bitmap.height)
            }
        }

        fun getDeleteButtonRect(item: MovableItem): RectF {
            val bounds = getBounds(item)
            return RectF(bounds.right - BUTTON_SIZE * 0.75f, bounds.top - BUTTON_SIZE * 0.75f, bounds.right + BUTTON_SIZE * 0.75f, bounds.top + BUTTON_SIZE * 0.75f)
        }

        fun getRotateButtonRect(item: MovableItem): RectF {
            val bounds = getBounds(item)
            return RectF(bounds.left - BUTTON_SIZE * 0.75f, bounds.bottom - BUTTON_SIZE * 0.75f, bounds.left + BUTTON_SIZE * 0.75f, bounds.bottom + BUTTON_SIZE * 0.75f)
        }

        fun getSelectionRect(item: MovableItem): RectF {
            return getBounds(item).apply { inset(-BUTTON_PADDING, -BUTTON_PADDING) }
        }

        fun drawControls(canvas: Canvas) {
            if (!item.isSelected) return
            val bounds = getBounds(item)
            val centerX = bounds.centerX()
            val centerY = bounds.centerY()

            canvas.save()
            canvas.rotate(item.rotation, centerX, centerY)
            canvas.scale(item.scale, item.scale, centerX, centerY)
            val inverseScale = 1.0f / item.scale
            SELECTED_BORDER_PAINT.strokeWidth = 2f * inverseScale
            canvas.drawRect(getSelectionRect(item), SELECTED_BORDER_PAINT)
            val deleteRect = getDeleteButtonRect(item)
            canvas.drawCircle(deleteRect.centerX(), deleteRect.centerY(), (BUTTON_SIZE / 2) * inverseScale, DELETE_PAINT)
            val xSize = (BUTTON_SIZE / 3) * inverseScale
            DELETE_X_PAINT.strokeWidth = 6f * inverseScale
            canvas.drawLine(deleteRect.centerX() - xSize, deleteRect.centerY() - xSize, deleteRect.centerX() + xSize, deleteRect.centerY() + xSize, DELETE_X_PAINT)
            canvas.drawLine(deleteRect.centerX() + xSize, deleteRect.centerY() - xSize, deleteRect.centerX() - xSize, deleteRect.centerY() + xSize, DELETE_X_PAINT)
            val rotateRect = getRotateButtonRect(item)
            canvas.drawCircle(rotateRect.centerX(), rotateRect.centerY(), (BUTTON_SIZE / 2) * inverseScale, ROTATE_PAINT)
            val rSize = (BUTTON_SIZE / 2.5f) * inverseScale
            ROTATE_ICON_PAINT.strokeWidth = 6f * inverseScale
            val oval = RectF(rotateRect.centerX() - rSize, rotateRect.centerY() - rSize, rotateRect.centerX() + rSize, rotateRect.centerY() + rSize)
            canvas.drawArc(oval, 45f, 270f, false, ROTATE_ICON_PAINT)
            val arrowEndX = rotateRect.centerX() + rSize * 0.8f
            val arrowEndY = rotateRect.centerY() - rSize * 0.8f
            val arrowHeadSize = rSize * 0.6f
            canvas.drawLine(arrowEndX, arrowEndY, arrowEndX - arrowHeadSize, arrowEndY, ROTATE_ICON_PAINT)
            canvas.drawLine(arrowEndX, arrowEndY, arrowEndX, arrowEndY + arrowHeadSize, ROTATE_ICON_PAINT)
            canvas.restore()
        }
    }

    private val itemControls = mutableListOf<ItemControls>()
    private var isMultiTouch = false
    private var oldDist = 1f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var itemToZoom: MovableItem? = null
    private var isRotating = false
    private var itemToRotate: MovableItem? = null
    private var lastAngle = 0f
    private var itemCenterX = 0f
    private var itemCenterY = 0f
    private var onTextEditRequestListener: ((MovableItem.TextItem) -> Unit)? = null
    private var lastClickTime: Long = 0
    private val DOUBLE_CLICK_TIME_DELTA: Long = 300
    private var showClipBoundary = false
    private var clipHighlightAlpha = 0
    private val clipHighlightPaint = Paint().apply {
        color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    private val clipHighlightFadeDuration = 500L
    private val clipHighlightHandler = Handler(Looper.getMainLooper())
    private val clipHighlightRunnable = object : Runnable {
        override fun run() {
            if (clipHighlightAlpha > 0) {
                clipHighlightAlpha -= 5
                clipHighlightPaint.alpha = clipHighlightAlpha
                invalidate()
                clipHighlightHandler.postDelayed(this, 16)
            } else { showClipBoundary = false }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        backgroundBitmap?.let { canvas.drawBitmap(it, imageRectLeft, imageRectTop, null) }
        val imageClipRect = RectF(imageRectLeft, imageRectTop, imageRectRight, imageRectBottom)
        val saveCount = canvas.save()
        canvas.clipRect(imageClipRect)
        canvasBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        canvas.drawPath(drawPath, drawPaint)
        for (item in movableItems) {
            when (item) {
                is MovableItem.TextItem -> {
                    if (item.textStyle != null) {
                        item.applyStyle()
                        item.drawWithStyle(canvas)
                    } else {
                        canvas.withSave {
                            val bounds = item.getBounds()
                            rotate(item.rotation, bounds.centerX(), bounds.centerY())
                            scale(item.scale, item.scale, bounds.centerX(), bounds.centerY())
                            val lines = item.text.split('\n')
                            val lineHeight = item.paint.fontSpacing
                            var currentY = item.y
                            for (line in lines) {
                                drawText(line, item.x, currentY, item.paint)
                                currentY += lineHeight
                            }
                        }
                    }
                }
                is MovableItem.ImageItem -> {
                    canvas.withSave {
                        val centerX = item.x + item.bitmap.width / 2f
                        val centerY = item.y + item.bitmap.height / 2f
                        rotate(item.rotation, centerX, centerY)
                        scale(item.scale, item.scale, centerX, centerY)
                        drawBitmap(item.bitmap, item.x, item.y, imagePaint)
                    }
                }
            }
        }
        canvas.restoreToCount(saveCount)
        itemControls.forEach { it.drawControls(canvas) }
        if (showClipBoundary) {
            clipHighlightPaint.alpha = clipHighlightAlpha
            canvas.drawRect(imageClipRect, clipHighlightPaint)
        }
        if (showBorder) {
            if (backgroundBitmap != null) {
                canvas.drawRect(imageRectLeft, imageRectTop, imageRectRight, imageRectBottom, borderPaint)
            } else {
                canvas.drawRect(borderPaint.strokeWidth / 2, borderPaint.strokeWidth / 2, width - borderPaint.strokeWidth / 2, height - borderPaint.strokeWidth / 2, borderPaint)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (backgroundBitmap != null && (event.x < imageRectLeft || event.x > imageRectRight || event.y < imageRectTop || event.y > imageRectBottom)) {
            if (event.action == MotionEvent.ACTION_DOWN) deselectAllItems()
            return true
        }
        if (!isDrawingEnabled) {
            handleMovableItemsTouchEvent(event)
        } else {
            handleDrawingTouchEvent(event)
        }
        return true
    }

    // Helper mới: Tính góc của đường thẳng nối 2 ngón tay
    private fun angle(event: MotionEvent): Float {
        val deltaX = event.getX(0) - event.getX(1)
        val deltaY = event.getY(0) - event.getY(1)
        return atan2(deltaY, deltaX) * (180 / Math.PI.toFloat())
    }

    private fun handleMovableItemsTouchEvent(event: MotionEvent) {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                isMultiTouch = false
                isRotating = false
                currentDraggedItem = null

                val selectedItem = movableItems.find { it.isSelected }

                // SỬA LỖI HITBOX: Kiểm tra chạm vào control button ở tọa độ màn hình
                if (selectedItem != null && handleControlTouch(x, y, selectedItem)) {
                    // Đã chạm vào nút xóa hoặc bắt đầu xoay bằng nút, không làm gì thêm
                    return
                }

                val touchedItem = findTouchedItem(x, y)
                if (touchedItem != null) {
                    if (handleDoubleClick(touchedItem)) return

                    lastClickTime = System.currentTimeMillis()
                    currentDraggedItem = touchedItem
                    touchOffsetX = x - touchedItem.x
                    touchOffsetY = y - touchedItem.y
                    selectItem(touchedItem)
                } else {
                    deselectAllItems()
                }
                invalidate()
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Nếu ngón thứ 2 chạm vào, chuẩn bị cho zoom/xoay
                if (event.pointerCount == 2) {
                    itemToZoom = movableItems.find { it.isSelected }
                    if (itemToZoom != null) {
                        oldDist = spacing(event)
                        lastAngle = angle(event) // Lấy góc ban đầu của 2 ngón tay
                        if (oldDist > 10f) {
                            isMultiTouch = true
                            currentDraggedItem = null // Hủy chế độ kéo
                            isRotating = false      // Hủy chế độ xoay bằng nút
                        }
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                // TÍNH NĂNG MỚI: Xử lý đồng thời cả zoom và xoay 2 ngón tay
                if (isMultiTouch && event.pointerCount >= 2 && itemToZoom != null) {
                    // Tính toán zoom
                    val newDist = spacing(event)
                    if (newDist > 10f) {
                        val scale = newDist / oldDist
                        var newScale = itemToZoom!!.scale * scale
                        newScale = newScale.coerceIn(0.05f, 5.0f)
                        itemToZoom!!.scale = newScale
                        oldDist = newDist
                    }

                    // Tính toán xoay
                    val newAngle = angle(event)
                    val angleDiff = newAngle - lastAngle
                    itemToZoom!!.rotation = (itemToZoom!!.rotation + angleDiff) % 360f
                    lastAngle = newAngle

                    invalidate()
                }
                // Chế độ xoay bằng nút (vẫn giữ lại)
                else if (isRotating && itemToRotate != null) {
                    val newAngle = calculateAngle(itemCenterX, itemCenterY, x, y)
                    val angleDiff = newAngle - lastAngle
                    itemToRotate!!.rotation = (itemToRotate!!.rotation + angleDiff) % 360f
                    lastAngle = newAngle
                    invalidate()
                }
                // Chế độ kéo bằng 1 ngón tay
                else if (currentDraggedItem != null) {
                    moveItemWithConstraints(currentDraggedItem!!, x, y)
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                isMultiTouch = false
                isRotating = false
                currentDraggedItem = null
                itemToZoom = null
                itemToRotate = null
            }
        }
    }

    private fun handleDrawingTouchEvent(event: MotionEvent) {
        val x = event.x; val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> drawPath.moveTo(x, y)
            MotionEvent.ACTION_MOVE -> {
                drawPath.lineTo(x, y)
                if (isErasing) {
                    drawCanvas?.drawPath(drawPath, drawPaint)
                    drawPath.reset(); drawPath.moveTo(x, y)
                }
            }
            MotionEvent.ACTION_UP -> { drawCanvas?.drawPath(drawPath, drawPaint); drawPath.reset() }
        }
        invalidate()
    }

    private fun handleControlTouch(touchX: Float, touchY: Float, item: MovableItem): Boolean {
        // Lấy ma trận biến đổi của item (xoay và scale)
        val matrix = Matrix()
        val bounds = when (item) {
            is MovableItem.TextItem -> item.getBounds()
            is MovableItem.ImageItem -> RectF(item.x, item.y, item.x + item.bitmap.width, item.y + item.bitmap.height)
        }
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        matrix.postRotate(item.rotation, centerX, centerY)
        matrix.postScale(item.scale, item.scale, centerX, centerY)

        // Tính hitbox cho nút XÓA trên màn hình
        val deletePoint = floatArrayOf(bounds.right, bounds.top)
        matrix.mapPoints(deletePoint)
        val deleteHitRect = RectF(
            deletePoint[0] - ItemControls.BUTTON_SIZE, deletePoint[1] - ItemControls.BUTTON_SIZE,
            deletePoint[0] + ItemControls.BUTTON_SIZE, deletePoint[1] + ItemControls.BUTTON_SIZE
        )
        if (deleteHitRect.contains(touchX, touchY)) {
            movableItems.remove(item)
            itemControls.removeAll { it.item == item }
            invalidate()
            return true // Đã xử lý
        }

        // Tính hitbox cho nút XOAY trên màn hình
        val rotatePoint = floatArrayOf(bounds.left, bounds.bottom)
        matrix.mapPoints(rotatePoint)
        val rotateHitRect = RectF(
            rotatePoint[0] - ItemControls.BUTTON_SIZE, rotatePoint[1] - ItemControls.BUTTON_SIZE,
            rotatePoint[0] + ItemControls.BUTTON_SIZE, rotatePoint[1] + ItemControls.BUTTON_SIZE
        )
        if (rotateHitRect.contains(touchX, touchY)) {
            isRotating = true // Bật chế độ xoay bằng nút
            itemToRotate = item
            this.itemCenterX = centerX
            this.itemCenterY = centerY
            lastAngle = calculateAngle(centerX, centerY, touchX, touchY)
            return true // Đã xử lý
        }

        return false // Không chạm vào nút nào
    }

    private fun handleDoubleClick(item: MovableItem): Boolean {
        if (System.currentTimeMillis() - lastClickTime < DOUBLE_CLICK_TIME_DELTA && item is MovableItem.TextItem && item.isSelected) {
            onTextEditRequestListener?.invoke(item)
            lastClickTime = 0
            return true
        }
        return false
    }

    private fun selectItem(itemToSelect: MovableItem) {
        movableItems.forEach { it.isSelected = (it == itemToSelect) }
        itemControls.clear()
        if (itemToSelect.isSelected) itemControls.add(ItemControls(itemToSelect))
    }

    private fun deselectAllItems() {
        movableItems.forEach { it.isSelected = false }
        itemControls.clear()
        invalidate()
    }

    private fun findTouchedItem(x: Float, y: Float): MovableItem? {
        for (item in movableItems.asReversed()) {
            val bounds = when (item) {
                is MovableItem.TextItem -> item.getBounds()
                is MovableItem.ImageItem -> RectF(item.x, item.y, item.x + item.bitmap.width, item.y + item.bitmap.height)
            }
            val inverseMatrix = Matrix()
            Matrix().apply {
                postRotate(item.rotation, bounds.centerX(), bounds.centerY())
                postScale(item.scale, item.scale, bounds.centerX(), bounds.centerY())
                invert(inverseMatrix)
            }
            val touchPoint = floatArrayOf(x, y).also { inverseMatrix.mapPoints(it) }
            val touchBounds = RectF(bounds).apply { inset(-ItemControls.BUTTON_SIZE / 2, -ItemControls.BUTTON_SIZE / 2) }
            if (touchBounds.contains(touchPoint[0], touchPoint[1])) return item
        }
        return null
    }

    fun enableDrawing(enabled: Boolean) { setEraseMode(false); isDrawingEnabled = enabled }
    fun setDrawingSize(size: Float) { drawSize = size; drawPaint.strokeWidth = size }
    fun setPaintColor(color: Int) { setEraseMode(false); isDrawingEnabled = true; drawColor = color; drawPaint.color = drawColor; invalidate() }
    fun setEraseMode(erase: Boolean) {
        isErasing = erase
        drawPaint.xfermode = if (isErasing) PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
        drawPaint.strokeWidth = if (isErasing) 50f else drawSize
        if(!isErasing) drawPaint.color = drawColor
    }

    fun setBackgroundBitmap(bitmap: Bitmap?) {
        if (bitmap != null) {
            originalBackgroundBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
            val scaleFactor = min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
            val scaledWidth = (bitmap.width * scaleFactor).toInt()
            val scaledHeight = (bitmap.height * scaleFactor).toInt()
            val scaledBitmap = bitmap.scale(scaledWidth, scaledHeight, true)
            imageRectLeft = (width - scaledWidth) / 2f
            imageRectTop = (height - scaledHeight) / 2f
            imageRectRight = imageRectLeft + scaledWidth
            imageRectBottom = imageRectTop + scaledHeight
            backgroundBitmap = scaledBitmap
            imageDimensionsListener?.onImageDimensionsChanged(imageRectLeft, imageRectTop, imageRectRight, imageRectBottom, scaledWidth, scaledHeight)
        } else {
            originalBackgroundBitmap = null
            backgroundBitmap = createBitmap(width, height).apply { eraseColor(Color.WHITE) }
            imageRectLeft = 0f; imageRectTop = 0f; imageRectRight = width.toFloat(); imageRectBottom = height.toFloat()
            imageDimensionsListener?.onImageDimensionsChanged(0f, 0f, width.toFloat(), height.toFloat(), width, height)
        }
        canvasBitmap = createBitmap(width, height).apply { eraseColor(Color.TRANSPARENT) }
        drawCanvas = Canvas(canvasBitmap!!)
        isDrawingEnabled = false; isErasing = false; showBorder = false
        movableItems.clear()
        invalidate()
    }

    fun addTextItem(text: String, textStyle: TextStyle? = null) {
        isDrawingEnabled = false; isErasing = false; deselectAllItems()
        val paint = Paint().apply {
            color = textStyle?.textColor ?: Color.RED; textSize = textStyle?.textSize ?: 60f; isAntiAlias = true
        }
        val x = imageRectLeft + (imageRectRight - imageRectLeft) / 4
        val y = imageRectTop + (imageRectBottom - imageRectTop) / 2 + paint.textSize / 2
        val textItem = MovableItem.TextItem(text, x, y, paint, textStyle).apply { scale = 1.0f; isSelected = true }
        movableItems.add(textItem)
        selectItem(textItem)
        invalidate()
    }

    fun addStickerItem(bitmap: Bitmap) {
        isDrawingEnabled = false; isErasing = false; deselectAllItems()
        val sanitizedBitmap: Bitmap = if (bitmap.width > MAX_STICKER_RESOLUTION || bitmap.height > MAX_STICKER_RESOLUTION) {
            val scaleRatio = min(MAX_STICKER_RESOLUTION / bitmap.width, MAX_STICKER_RESOLUTION / bitmap.height)
            bitmap.scale((bitmap.width * scaleRatio).toInt(), (bitmap.height * scaleRatio).toInt(), true)
        } else {
            bitmap
        }
        val targetInitialWidth = 150f
        val initialScale = targetInitialWidth / sanitizedBitmap.width.toFloat()
        val imageCenterX = imageRectLeft + (imageRectRight - imageRectLeft) / 2
        val imageCenterY = imageRectTop + (imageRectBottom - imageRectTop) / 2
        val x = imageCenterX - sanitizedBitmap.width / 2
        val y = imageCenterY - sanitizedBitmap.height / 2
        val stickerItem = MovableItem.ImageItem(sanitizedBitmap, x, y).apply { scale = initialScale; isSelected = true }
        movableItems.add(stickerItem)
        selectItem(stickerItem)
        invalidate()
    }

    fun getBitmap(): Bitmap {
        val result = createBitmap(width, height)
        val canvas = Canvas(result)
        backgroundBitmap?.let { canvas.drawBitmap(it, imageRectLeft, imageRectTop, null) }
        val imageClipRect = RectF(imageRectLeft, imageRectTop, imageRectRight, imageRectBottom)
        val saveCount = canvas.save()
        canvas.clipRect(imageClipRect)
        canvasBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        for (item in movableItems) {
            when (item) {
                is MovableItem.TextItem -> {
                    if (item.textStyle != null) {
                        item.applyStyle(); item.drawWithStyle(canvas)
                    } else {
                        canvas.withSave {
                            val bounds = item.getBounds()
                            rotate(item.rotation, bounds.centerX(), bounds.centerY())
                            scale(item.scale, item.scale, bounds.centerX(), bounds.centerY())
                            val lines = item.text.split('\n')
                            val lineHeight = item.paint.fontSpacing
                            var currentY = item.y
                            for (line in lines) {
                                drawText(line, item.x, currentY, item.paint)
                                currentY += lineHeight
                            }
                        }
                    }
                }
                is MovableItem.ImageItem -> {
                    canvas.withSave {
                        val centerX = item.x + item.bitmap.width / 2f
                        val centerY = item.y + item.bitmap.height / 2f
                        rotate(item.rotation, centerX, centerY)
                        scale(item.scale, item.scale, centerX, centerY)
                        drawBitmap(item.bitmap, item.x, item.y, imagePaint)
                    }
                }
            }
        }
        canvas.restoreToCount(saveCount)
        return result
    }

    private fun spacing(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return sqrt(x * x + y * y)
    }

    private fun calculateAngle(centerX: Float, centerY: Float, touchX: Float, touchY: Float): Float {
        val deltaX = touchX - centerX
        val deltaY = touchY - centerY
        var angle = Math.toDegrees(atan2(deltaY.toDouble(), deltaX.toDouble())).toFloat()
        if (angle < 0) angle += 360f
        return angle
    }

    fun hasBackgroundBitmap(): Boolean = backgroundBitmap != null
    fun resetToOriginalBitmap() {
        originalBackgroundBitmap?.let {
            clearDrawings()
            setBackgroundBitmap(it.copy(it.config ?: Bitmap.Config.ARGB_8888, true))
        }
    }
    fun clearDrawings() { canvasBitmap?.eraseColor(Color.TRANSPARENT); invalidate() }
    fun setOnTextEditRequestListener(listener: (MovableItem.TextItem) -> Unit) { onTextEditRequestListener = listener }
    fun updateTextItem(textItem: MovableItem.TextItem, textStyle: TextStyle) {
        textItem.textStyle = textStyle; textItem.text = textStyle.text; textItem.applyStyle(); invalidate()
    }

    private fun moveItemWithConstraints(item: MovableItem, x: Float, y: Float) {
        item.x = x - touchOffsetX; item.y = y - touchOffsetY
        checkAndHighlightBoundaries(item)
    }

    private fun checkAndHighlightBoundaries(item: MovableItem) {
        if (backgroundBitmap == null) return
        val itemBounds = when (item) {
            is MovableItem.TextItem -> item.getBounds()
            is MovableItem.ImageItem -> RectF(item.x, item.y, item.x + item.bitmap.width * item.scale, item.y + item.bitmap.height * item.scale)
        }
        val expandedImageRect = RectF(imageRectLeft - 10, imageRectTop - 10, imageRectRight + 10, imageRectBottom + 10)
        if (!expandedImageRect.contains(itemBounds)) {
            showClipBoundary = true; clipHighlightAlpha = 180
            clipHighlightHandler.removeCallbacks(clipHighlightRunnable)
            invalidate()
            clipHighlightHandler.postDelayed(clipHighlightRunnable, clipHighlightFadeDuration)
        }
    }
}

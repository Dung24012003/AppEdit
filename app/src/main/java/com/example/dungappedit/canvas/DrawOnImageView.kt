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
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.withSave
import com.example.dungappedit.model.MovableItem
import com.example.dungappedit.model.TextStyle
import com.example.dungappedit.ui.edit.text.TextEditor
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class DrawOnImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val movableItems = mutableListOf<MovableItem>()
    private var currentDraggedItem: MovableItem? = null

    var isDrawingEnabled = false
    private var drawPath = Path() // Path that stores user's finger movement
    private var drawSize = 10f
    private var drawColor = Color.BLACK

    private val drawPaint = Paint().apply { // Paint setup
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

    private var drawCanvas: Canvas? = null // Canvas to draw on bitmap
    private var canvasBitmap: Bitmap? = null // Bitmap to store drawing
    private var backgroundBitmap: Bitmap? = null // Background image from gallery
    private var originalBackgroundBitmap: Bitmap? = null // Save original background for reset
    private var isErasing = false
    private var showBorder = false // Flag to control border visibility

    private var touchOffsetX = 0f
    private var touchOffsetY = 0f

    // Image positioning variables
    var imageRectLeft: Float = 0f
        private set
    var imageRectTop: Float = 0f
        private set
    var imageRectRight: Float = 0f
        private set
    var imageRectBottom: Float = 0f
        private set

    // Callback interface for image size changes
    interface OnImageDimensionsChangedListener {
        fun onImageDimensionsChanged(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            width: Int,
            height: Int
        )
    }

    private var imageDimensionsListener: OnImageDimensionsChangedListener? = null

    fun setOnImageDimensionsChangedListener(listener: OnImageDimensionsChangedListener) {
        imageDimensionsListener = listener
    }

    // Class to represent control buttons for movable items
    private class ItemControls(val item: MovableItem) {
        // Control button sizes and colors
        companion object {
            const val BUTTON_SIZE = 55f
            const val BUTTON_PADDING = 10f

            // Increase hit area for easier interaction
            const val BUTTON_HIT_AREA_MULTIPLIER = 1.5f
            val DELETE_PAINT = Paint().apply {
                color = Color.RED
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val DELETE_X_PAINT = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 6f
                isAntiAlias = true
            }
            val ROTATE_PAINT = Paint().apply {
                color = Color.GREEN
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val ROTATE_ICON_PAINT = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 6f
                isAntiAlias = true
            }
            val SELECTED_BORDER_PAINT = Paint().apply {
                color = Color.BLUE
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
        }

        // These positions are in unrotated coordinates for consistent hit detection
        // regardless of item rotation

        // Calculate positions for delete button (top right corner)
        fun getDeleteButtonRect(item: MovableItem): RectF {
            val bounds = when (item) {
                is MovableItem.TextItem -> item.getBounds()
                is MovableItem.ImageItem -> RectF(
                    item.x,
                    item.y,
                    item.x + item.bitmap.width,
                    item.y + item.bitmap.height
                )
            }
            // Use a larger hit area for the button
            val hitRect = RectF(
                bounds.right - BUTTON_SIZE * 0.75f,
                bounds.top - BUTTON_SIZE * 0.75f,
                bounds.right + BUTTON_SIZE * 0.75f,
                bounds.top + BUTTON_SIZE * 0.75f
            )
            return hitRect
        }

        // Calculate positions for rotate button (bottom left corner)
        fun getRotateButtonRect(item: MovableItem): RectF {
            val bounds = when (item) {
                is MovableItem.TextItem -> item.getBounds()
                is MovableItem.ImageItem -> RectF(
                    item.x,
                    item.y,
                    item.x + item.bitmap.width,
                    item.y + item.bitmap.height
                )
            }
            // Use a larger hit area for the button
            val hitRect = RectF(
                bounds.left - BUTTON_SIZE * 0.75f,
                bounds.bottom - BUTTON_SIZE * 0.75f,
                bounds.left + BUTTON_SIZE * 0.75f,
                bounds.bottom + BUTTON_SIZE * 0.75f
            )
            return hitRect
        }

        // Calculate positions for rotate button with larger hit area
        fun getRotateButtonHitRect(item: MovableItem): RectF {
            val rotateRect = getRotateButtonRect(item)
            val expandSize = BUTTON_SIZE * (BUTTON_HIT_AREA_MULTIPLIER - 1) / 2

            return RectF(
                rotateRect.left - expandSize,
                rotateRect.top - expandSize,
                rotateRect.right + expandSize,
                rotateRect.bottom + expandSize
            )
        }

        // Calculate positions for delete button with larger hit area
        fun getDeleteButtonHitRect(item: MovableItem): RectF {
            val deleteRect = getDeleteButtonRect(item)
            val expandSize = BUTTON_SIZE * (BUTTON_HIT_AREA_MULTIPLIER - 1) / 2

            return RectF(
                deleteRect.left - expandSize,
                deleteRect.top - expandSize,
                deleteRect.right + expandSize,
                deleteRect.bottom + expandSize
            )
        }

        // Calculate selection border
        fun getSelectionRect(item: MovableItem): RectF {
            val bounds = when (item) {
                is MovableItem.TextItem -> item.getBounds()
                is MovableItem.ImageItem -> RectF(
                    item.x,
                    item.y,
                    item.x + item.bitmap.width,
                    item.y + item.bitmap.height
                )
            }
            bounds.inset(-BUTTON_PADDING, -BUTTON_PADDING)
            return bounds
        }

        // Draw controls
        fun drawControls(canvas: Canvas) {
            if (!item.isSelected) return

            val bounds = when (item) {
                is MovableItem.TextItem -> item.getBounds()
                is MovableItem.ImageItem -> RectF(
                    item.x,
                    item.y,
                    item.x + item.bitmap.width,
                    item.y + item.bitmap.height
                )
            }
            val centerX = bounds.centerX()
            val centerY = bounds.centerY()

            canvas.save()
            // Apply transformations to the canvas for drawing controls
            canvas.rotate(item.rotation, centerX, centerY)
            canvas.scale(item.scale, item.scale, centerX, centerY)

            // Draw selection border
            canvas.drawRect(getSelectionRect(item), SELECTED_BORDER_PAINT)

            // Draw delete button
            val deleteRect = getDeleteButtonRect(item)
            canvas.drawCircle(
                deleteRect.centerX(),
                deleteRect.centerY(),
                BUTTON_SIZE / 2,
                DELETE_PAINT
            )
            val xSize = BUTTON_SIZE / 3
            canvas.drawLine(
                deleteRect.centerX() - xSize,
                deleteRect.centerY() - xSize,
                deleteRect.centerX() + xSize,
                deleteRect.centerY() + xSize,
                DELETE_X_PAINT
            )
            canvas.drawLine(
                deleteRect.centerX() + xSize,
                deleteRect.centerY() - xSize,
                deleteRect.centerX() - xSize,
                deleteRect.centerY() + xSize,
                DELETE_X_PAINT
            )

            // Draw rotate button
            val rotateRect = getRotateButtonRect(item)
            canvas.drawCircle(
                rotateRect.centerX(),
                rotateRect.centerY(),
                BUTTON_SIZE / 2,
                ROTATE_PAINT
            )

            val rSize = BUTTON_SIZE / 2.5f
            val oval = RectF(
                rotateRect.centerX() - rSize, rotateRect.centerY() - rSize,
                rotateRect.centerX() + rSize, rotateRect.centerY() + rSize
            )
            canvas.drawArc(oval, 45f, 270f, false, ROTATE_ICON_PAINT)
            val arrowEndX = rotateRect.centerX() + rSize * 0.8f
            val arrowEndY = rotateRect.centerY() - rSize * 0.8f
            val arrowHeadSize = rSize * 0.6f
            canvas.drawLine(
                arrowEndX,
                arrowEndY,
                arrowEndX - arrowHeadSize,
                arrowEndY,
                ROTATE_ICON_PAINT
            )
            canvas.drawLine(
                arrowEndX,
                arrowEndY,
                arrowEndX,
                arrowEndY + arrowHeadSize,
                ROTATE_ICON_PAINT
            )

            canvas.restore()
        }
    }

    // List to keep track of controls for each item
    private val itemControls = mutableListOf<ItemControls>()

    // Variables for pinch-to-zoom
    private var isMultiTouch = false
    private var midPoint = PointF()
    private var oldDist = 1f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var itemToZoom: MovableItem? = null

    // Variables for rotation
    private var isRotating = false
    private var itemToRotate: MovableItem? = null
    private var lastAngle = 0f
    private var itemCenterX = 0f
    private var itemCenterY = 0f

    // Add this property for text editing
    private var textEditor: TextEditor? = null
    private var onTextEditRequestListener: ((MovableItem.TextItem) -> Unit)? = null
    private var lastClickTime: Long = 0
    private val DOUBLE_CLICK_TIME_DELTA: Long = 300 // milliseconds

    // Add these properties to the class
    private var showClipBoundary = false
    private var clipHighlightAlpha = 0
    private val clipHighlightPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    private val clipHighlightFadeDuration = 500L // Fade duration in milliseconds
    private val clipHighlightHandler = Handler(Looper.getMainLooper())
    private val clipHighlightRunnable = object : Runnable {
        override fun run() {
            if (clipHighlightAlpha > 0) {
                clipHighlightAlpha -= 5
                clipHighlightPaint.alpha = clipHighlightAlpha
                invalidate()
                clipHighlightHandler.postDelayed(this, 16) // ~60fps
            } else {
                showClipBoundary = false
            }
        }
    }

    init {
        // Initialize text editor
        textEditor = TextEditor(context)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background image first
        backgroundBitmap?.let { canvas.drawBitmap(it, imageRectLeft, imageRectTop, null) }

        // Create a clipping rect for the image boundaries
        val imageClipRect = RectF(imageRectLeft, imageRectTop, imageRectRight, imageRectBottom)

        // Save the canvas state before clipping
        val saveCount = canvas.save()

        // Apply clipping to constrain drawings to the image boundaries
        canvas.clipRect(imageClipRect)

        // Draw the canvas bitmap with drawings
        canvasBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }

        // Draw the current drawing path
        canvas.drawPath(drawPath, drawPaint)

        // Draw movable items: text and icons with appropriate scaling and rotation
        for (item in movableItems) {
            when (item) {
                is MovableItem.TextItem -> {
                    // Use the drawWithStyle method if style is present, otherwise fallback to old method
                    if (item.textStyle != null) {
                        item.applyStyle()
                        item.drawWithStyle(canvas)
                    } else {
                        // Save canvas state for transformations
                        canvas.withSave() {
                            // Calculate the center of the text for rotation
                            val bounds = item.getBounds()
                            val centerX = bounds.centerX()
                            val centerY = bounds.centerY()

                            // Apply rotation around the center
                            rotate(item.rotation, centerX, centerY)

                            // Apply scaling
                            scale(item.scale, item.scale, centerX, centerY)

                            // Draw text (handle multi-line)
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
                        drawBitmap(item.bitmap, item.x, item.y, null)
                    }
                }
            }
        }

        // Restore canvas state to remove clipping for controls
        canvas.restoreToCount(saveCount)

        // Draw controls for selected items (outside of clipping)
        itemControls.forEach { it.drawControls(canvas) }

        // Draw clip boundary indicator if needed
        if (showClipBoundary) {
            clipHighlightPaint.alpha = clipHighlightAlpha
            canvas.drawRect(imageClipRect, clipHighlightPaint)
        }

        // Draw border around the image or the entire view only if showBorder is true
        if (showBorder) {
            if (backgroundBitmap != null) {
                canvas.drawRect(
                    imageRectLeft,
                    imageRectTop,
                    imageRectRight,
                    imageRectBottom,
                    borderPaint
                )
            } else {
                canvas.drawRect(
                    borderPaint.strokeWidth / 2,
                    borderPaint.strokeWidth / 2,
                    width - borderPaint.strokeWidth / 2,
                    height - borderPaint.strokeWidth / 2,
                    borderPaint
                )
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        // Check if touch is within the image boundaries when an image is set
        if (backgroundBitmap != null) {
            if (x < imageRectLeft || x > imageRectRight || y < imageRectTop || y > imageRectBottom) {
                return true // Ignore touches outside the image
            }
        }

        if (!isDrawingEnabled) {
            val action = event.actionMasked

            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    // Reset multi-touch state
                    isMultiTouch = false
                    isRotating = false
                    activePointerId = event.getPointerId(0)

                    // First check if the touch is on a control button of the selected item
                    val selectedItem = movableItems.find { it.isSelected }
                    if (selectedItem != null) {
                        val controls = ItemControls(selectedItem)

                        // Get item center for coordinate transformations
                        val bounds = when (selectedItem) {
                            is MovableItem.TextItem -> selectedItem.getBounds()
                            is MovableItem.ImageItem -> RectF(
                                selectedItem.x,
                                selectedItem.y,
                                selectedItem.x + selectedItem.bitmap.width,
                                selectedItem.y + selectedItem.bitmap.height
                            )
                        }
                        val itemCenterX = bounds.centerX()
                        val itemCenterY = bounds.centerY()

                        // Create an inverse matrix to transform touch point to local coordinates
                        val inverseMatrix = Matrix()
                        val tempMatrix = Matrix()
                        tempMatrix.setRotate(selectedItem.rotation, itemCenterX, itemCenterY)
                        tempMatrix.postScale(
                            selectedItem.scale,
                            selectedItem.scale,
                            itemCenterX,
                            itemCenterY
                        )
                        tempMatrix.invert(inverseMatrix)

                        val touchPoint = floatArrayOf(x, y)
                        inverseMatrix.mapPoints(touchPoint)

                        // Check if touched delete button (using larger hit area)
                        val deleteHitRect = controls.getDeleteButtonRect(selectedItem)
                        if (deleteHitRect.contains(touchPoint[0], touchPoint[1])) {
                            // Delete the item
                            movableItems.remove(selectedItem)
                            itemControls.removeAll { it.item == selectedItem }
                            invalidate()
                            return true
                        }

                        // Check if touched rotate button (using larger hit area)
                        val rotateHitRect = controls.getRotateButtonRect(selectedItem)
                        if (rotateHitRect.contains(touchPoint[0], touchPoint[1])) {
                            // Start rotation
                            itemToRotate = selectedItem
                            isRotating = true

                            // Store the item center for rotation calculations
                            this.itemCenterX = itemCenterX
                            this.itemCenterY = itemCenterY

                            // Calculate initial angle between center and touch point
                            lastAngle = calculateAngle(itemCenterX, itemCenterY, x, y)

                            return true
                        }
                    }

                    // If not touching a control, check if touching an item
                    // Find the item under the touch point
                    val touchedItem = findTouchedItem(x, y)

                    if (touchedItem != null) {
                        // Check for double click on text item
                        val clickTime = System.currentTimeMillis()
                        if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA &&
                            touchedItem is MovableItem.TextItem && touchedItem.isSelected
                        ) {

                            // Increment double click counter
                            touchedItem.doubleClickCount++

                            // Notify the listener that a text edit was requested
                            onTextEditRequestListener?.invoke(touchedItem)

                            lastClickTime = 0 // Reset timer to avoid triple click handling
                            return true
                        }

                        lastClickTime = clickTime

                        // If we touched an item, prepare for dragging
                        currentDraggedItem = touchedItem
                        touchOffsetX = x - touchedItem.x
                        touchOffsetY = y - touchedItem.y

                        // Select this item and deselect others
                        movableItems.forEach { it.isSelected = (it == touchedItem) }
                        itemControls.clear()
                        if (touchedItem.isSelected) {
                            itemControls.add(ItemControls(touchedItem))
                        }

                        invalidate()
                        return true
                    } else {
                        // If touch is not on any item, deselect all
                        movableItems.forEach { it.isSelected = false }
                        itemControls.clear()
                        invalidate()
                    }
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    // Second finger is down, start zooming
                    if (event.pointerCount == 2) {
                        // Find the selected item for zooming
                        itemToZoom = movableItems.find { it.isSelected }

                        if (itemToZoom != null) {
                            // Get the initial distance between fingers
                            oldDist = spacing(event)
                            if (oldDist > 10f) {
                                midPoint = getMidPoint(event)
                                isMultiTouch = true
                                currentDraggedItem = null // Cancel any dragging
                                isRotating = false // Cancel any rotation
                            }
                        }
                    }
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    // Handle rotation if active
                    if (isRotating && itemToRotate != null) {
                        // Calculate new angle between center and current touch point
                        val newAngle = calculateAngle(itemCenterX, itemCenterY, x, y)

                        // Calculate the difference (how much we rotated)
                        val angleDiff = newAngle - lastAngle

                        // Apply the rotation (add to the current rotation)
                        itemToRotate!!.rotation += angleDiff

                        // Normalize rotation to 0-360 range
                        itemToRotate!!.rotation %= 360f

                        // Update last angle for next move
                        lastAngle = newAngle

                        invalidate()
                        return true
                    }
                    // Handle multi-touch zoom
                    else if (isMultiTouch && event.pointerCount == 2 && itemToZoom != null) {
                        // Calculate new distance between fingers
                        val newDist = spacing(event)
                        if (newDist > 10f) {
                            // Calculate scale factor as the ratio of new to old distance
                            val scale = newDist / oldDist

                            // Calculate new scale
                            var newScale = itemToZoom!!.scale * scale

                            // Limit the scale to reasonable values
                            newScale = newScale.coerceIn(0.2f, 5.0f)

                            // Check if the new scale would make the item too large for the image
                            if (backgroundBitmap != null) {
                                when (itemToZoom) {
                                    is MovableItem.TextItem -> {
                                        val textItem = itemToZoom as MovableItem.TextItem
                                        val bounds = textItem.getBounds()
                                        val currentWidth = bounds.width()
                                        val currentHeight = bounds.height()

                                        val newWidth = currentWidth * newScale
                                        val newHeight = currentHeight * newScale

                                        if (newWidth > (imageRectRight - imageRectLeft)) {
                                            newScale = minOf(
                                                newScale,
                                                (imageRectRight - imageRectLeft) / currentWidth
                                            )
                                        }
                                        if (newHeight > (imageRectBottom - imageRectTop)) {
                                            newScale = minOf(
                                                newScale,
                                                (imageRectBottom - imageRectTop) / currentHeight
                                            )
                                        }
                                    }

                                    is MovableItem.ImageItem -> {
                                        val imageItem = itemToZoom as MovableItem.ImageItem
                                        val newWidth = imageItem.bitmap.width * newScale
                                        val newHeight = imageItem.bitmap.height * newScale

                                        // If new dimensions would exceed image bounds, limit the scale
                                        if (newWidth > (imageRectRight - imageRectLeft)) {
                                            newScale = (imageRectRight - imageRectLeft) /
                                                    imageItem.bitmap.width
                                        }
                                        if (newHeight > (imageRectBottom - imageRectTop)) {
                                            newScale = minOf(
                                                newScale, (imageRectBottom - imageRectTop) /
                                                        imageItem.bitmap.height
                                            )
                                        }

                                        // Adjust position if item would go outside boundaries after scaling
                                        if (imageItem.x + newWidth > imageRectRight) {
                                            imageItem.x = imageRectRight - newWidth
                                        }
                                        if (imageItem.y + newHeight > imageRectBottom) {
                                            imageItem.y = imageRectBottom - newHeight
                                        }
                                    }

                                    else -> {}
                                }
                            }

                            // Apply the final scale
                            itemToZoom!!.scale = newScale

                            // Update old distance for next move event
                            oldDist = newDist

                            invalidate()
                        }
                        return true
                    }
                    // Handle single-finger dragging
                    else if (currentDraggedItem != null) {
                        moveItemWithConstraints(currentDraggedItem!!, x, y)
                        invalidate()
                        return true
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    // Check which pointer was lifted
                    val pointerIndex = event.actionIndex
                    val pointerId = event.getPointerId(pointerIndex)

                    if (pointerId == activePointerId) {
                        // The active pointer was lifted, reset state
                        activePointerId = MotionEvent.INVALID_POINTER_ID
                        currentDraggedItem = null
                        isMultiTouch = false
                        itemToZoom = null
                        isRotating = false
                        itemToRotate = null
                        // No need to reset itemCenterX/Y as they'll be recalculated on next touch
                    }
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    currentDraggedItem = null
                    isMultiTouch = false
                    itemToZoom = null
                    isRotating = false
                    itemToRotate = null
                    // No need to reset itemCenterX/Y as they'll be recalculated on next touch
                    return true
                }
            }
        } else {
            // Temporary drawing mode on bitmap
            drawPaint.color = drawColor
            when (event.action) {
                MotionEvent.ACTION_DOWN -> drawPath.moveTo(x, y)
                MotionEvent.ACTION_MOVE -> {
                    drawPath.lineTo(x, y)
                    // When in erase mode, draw path immediately instead of waiting for ACTION_UP
                    if (isErasing) {
                        drawCanvas?.drawPath(drawPath, drawPaint)
                        drawPath.reset()
                        drawPath.moveTo(x, y)
                    }
                }

                MotionEvent.ACTION_UP -> {
                    drawCanvas?.drawPath(drawPath, drawPaint)
                    drawPath.reset()
                }
            }
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun findTouchedItem(x: Float, y: Float): MovableItem? {
        for (item in movableItems.asReversed()) { // asReversed() prioritizes items on top
            val bounds = when (item) {
                is MovableItem.TextItem -> item.getBounds()
                is MovableItem.ImageItem -> RectF(
                    item.x,
                    item.y,
                    item.x + item.bitmap.width,
                    item.y + item.bitmap.height
                )
            }
            val centerX = bounds.centerX()
            val centerY = bounds.centerY()

            // Create an inverse matrix to transform the touch point to the item's local coordinate system
            val inverseMatrix = Matrix()
            val matrix = Matrix()
            matrix.postRotate(item.rotation, centerX, centerY)
            matrix.postScale(item.scale, item.scale, centerX, centerY)
            matrix.invert(inverseMatrix)

            val touchPoint = floatArrayOf(x, y)
            inverseMatrix.mapPoints(touchPoint)

            // Check if the transformed point is inside the unscaled item bounds
            val touchBounds =
                RectF(bounds) // Use the 'bounds' variable calculated above, which is correct for both types
            touchBounds.inset(
                -ItemControls.BUTTON_SIZE / 2,
                -ItemControls.BUTTON_SIZE / 2
            ) // Add padding for easier selection
            if (touchBounds.contains(touchPoint[0], touchPoint[1])) {
                return item
            }
        }
        return null
    }

    fun enableDrawing(enabled: Boolean) {
        setEraseMode(false)
        isDrawingEnabled = enabled
    }

    fun setDrawingSize(size: Float) {
        drawSize = size
        drawPaint.strokeWidth = size
    }

    fun setPaintColor(color: Int) {
        setEraseMode(false)
        isDrawingEnabled = true
        drawColor = color
        drawPaint.color = drawColor
        invalidate()
    }

    fun setEraseMode(erase: Boolean) {
        isErasing = erase
        if (isErasing) {
            drawPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            drawPaint.strokeWidth = 50f
        } else {
            drawPaint.xfermode = null
            drawPaint.color = drawColor
            drawPaint.strokeWidth = drawSize
        }
    }

    fun setBackgroundBitmap(bitmap: Bitmap?) {
        if (bitmap != null) {
            // Store the original bitmap for reset functionality
            val config = bitmap.config ?: Bitmap.Config.ARGB_8888
            originalBackgroundBitmap = bitmap.copy(config, true)

            // Calculate scaling to maintain aspect ratio
            val scaleX = width.toFloat() / bitmap.width.toFloat()
            val scaleY = height.toFloat() / bitmap.height.toFloat()
            val scaleFactor = min(scaleX, scaleY)

            val scaledWidth = (bitmap.width * scaleFactor).toInt()
            val scaledHeight = (bitmap.height * scaleFactor).toInt()

            val scaledBitmap = bitmap.scale(scaledWidth, scaledHeight)

            // Center the image
            imageRectLeft = (width - scaledWidth) / 2f
            imageRectTop = (height - scaledHeight) / 2f
            imageRectRight = imageRectLeft + scaledWidth
            imageRectBottom = imageRectTop + scaledHeight

            backgroundBitmap = scaledBitmap

            // Notify the dimension listener
            imageDimensionsListener?.onImageDimensionsChanged(
                imageRectLeft,
                imageRectTop,
                imageRectRight,
                imageRectBottom,
                scaledWidth,
                scaledHeight
            )
        } else {
            // Clear the original bitmap reference when setting to null
            originalBackgroundBitmap = null

            // Create white background bitmap if bitmap is null
            backgroundBitmap = createBitmap(width, height).apply {
                eraseColor(Color.WHITE)
            }

            imageRectLeft = 0f
            imageRectTop = 0f
            imageRectRight = width.toFloat()
            imageRectBottom = height.toFloat()

            // Notify the dimension listener for full canvas
            imageDimensionsListener?.onImageDimensionsChanged(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                width,
                height
            )
        }

        canvasBitmap = createBitmap(width, height) // Create empty bitmap for drawing
        canvasBitmap!!.eraseColor(Color.TRANSPARENT)
        drawCanvas = Canvas(canvasBitmap!!) // Draw on canvasBitmap

        isDrawingEnabled = false
        isErasing = false
        showBorder = false // Reset border visibility when setting a new background
        movableItems.clear()
        invalidate()
    }

    fun addTextItem(text: String, textStyle: TextStyle? = null) {
        isDrawingEnabled = false
        isErasing = false

        // Deselect all existing items
        movableItems.forEach { it.isSelected = false }
        itemControls.clear()

        val paint = Paint().apply {
            color = textStyle?.textColor ?: Color.RED
            textSize = textStyle?.textSize ?: 60f
            isAntiAlias = true
        }

        // Position text within image bounds if an image is set
        val x = if (backgroundBitmap != null) {
            // Calculate a position that ensures text is completely within the image
            // Place it at 1/4 of the image width
            imageRectLeft + (imageRectRight - imageRectLeft) / 4
        } else {
            300f
        }

        val y = if (backgroundBitmap != null) {
            // Calculate a position that ensures text is completely within the image
            // For text, y is the baseline, so position it at center plus some offset for height
            imageRectTop + (imageRectBottom - imageRectTop) / 2 + paint.textSize / 2
        } else {
            900f
        }

        val textItem = MovableItem.TextItem(text, x, y, paint, textStyle).apply {
            scale = 1.0f
            isSelected = true  // Select the newly added item
        }

        movableItems.add(textItem)
        itemControls.add(ItemControls(textItem))
        invalidate()
    }

    fun addStickerItem(bitmap: Bitmap) {
        isDrawingEnabled = false
        isErasing = false

        // Deselect all existing items
        movableItems.forEach { it.isSelected = false }
        itemControls.clear()

        // --- BƯỚC QUAN TRỌNG: GIẢM KÍCH THƯỚC BITMAP NẾU QUÁ LỚN ---
        val sanitizedBitmap: Bitmap
        val originalWidth = bitmap.width.toFloat()
        val originalHeight = bitmap.height.toFloat()

        if (originalWidth > 1024f || originalHeight > 1024f) {
            // Bitmap quá lớn, cần thu nhỏ lại nhưng vẫn giữ tỷ lệ khung hình.
            val scaleRatio = if (originalWidth > originalHeight) {
                1024f / originalWidth
            } else {
                1024f / originalHeight
            }
            val newWidth = (originalWidth * scaleRatio).toInt()
            val newHeight = (originalHeight * scaleRatio).toInt()

            // Tạo một bitmap mới đã được thu nhỏ tới kích thước hợp lý.
            sanitizedBitmap = bitmap.scale(newWidth, newHeight, true)
        } else {
            // Kích thước bitmap chấp nhận được, sử dụng ảnh gốc.
            sanitizedBitmap = bitmap
        }
        // Từ giờ, chúng ta sẽ chỉ làm việc với 'sanitizedBitmap'.

        // -----------------------------------------------------------------

        // Xác định chiều rộng hiển thị ban đầu khi sticker mới được thêm vào.
        val targetInitialWidth = 150f

        // Tính toán tỷ lệ ban đầu dựa trên kích thước của 'sanitizedBitmap'.
        val initialScale = targetInitialWidth / sanitizedBitmap.width.toFloat()

        // Tính toán vị trí (x, y) để căn giữa sticker một cách chính xác.
        val x: Float
        val y: Float

        if (backgroundBitmap != null) {
            // Căn sticker vào giữa ảnh nền
            val imageCenterX = imageRectLeft + (imageRectRight - imageRectLeft) / 2
            val imageCenterY = imageRectTop + (imageRectBottom - imageRectTop) / 2
            x = imageCenterX - sanitizedBitmap.width / 2
            y = imageCenterY - sanitizedBitmap.height / 2
        } else {
            // Căn sticker vào giữa View (màn hình)
            val viewCenterX = width / 2f
            val viewCenterY = height / 2f
            x = viewCenterX - sanitizedBitmap.width / 2
            y = viewCenterY - sanitizedBitmap.height / 2
        }

        // Tạo sticker item, truyền vào bitmap đã được xử lý và tọa độ đã tính.
        val stickerItem = MovableItem.ImageItem(sanitizedBitmap, x, y).apply {
            scale = initialScale
            isSelected = true
        }

        movableItems.add(stickerItem)
        itemControls.add(ItemControls(stickerItem))
        invalidate()
    }

//    fun addStickerItem(bitmap: Bitmap) {
//        isDrawingEnabled = false
//        isErasing = false
//
//        // Deselect all existing items
//        movableItems.forEach { it.isSelected = false }
//        itemControls.clear()
//
//        val scaled = bitmap.scale(150, 150)
//
//        // Position sticker within image bounds if an image is set
//        val x = if (backgroundBitmap != null) {
//            // Calculate a position that ensures text is completely within the image
//            // Place it at 1/4 of the image width
//            imageRectLeft + (imageRectRight - imageRectLeft) / 4
//        } else {
//            300f
//        }
//
//        val y = if (backgroundBitmap != null) {
//            // Calculate a position that ensures text is completely within the image
//            // For text, y is the baseline, so position it at center plus some offset for height
//            imageRectTop + (imageRectBottom - imageRectTop) / 2
//        } else {
//            900f
//        }
//
//        val stickerItem = MovableItem.ImageItem(scaled, x, y).apply {
//            scale = 1.0f
//            isSelected = true  // Select the newly added item
//        }
//
//        movableItems.add(stickerItem)
//        itemControls.add(ItemControls(stickerItem))
//        invalidate()
//    }

    fun getBitmap(): Bitmap {
        val result = createBitmap(width, height)
        val canvas = Canvas(result)

        // Draw background with correct positioning
        backgroundBitmap?.let {
            canvas.drawBitmap(it, imageRectLeft, imageRectTop, null)
        }

        // Create a clipping rect for the image boundaries
        val imageClipRect = RectF(imageRectLeft, imageRectTop, imageRectRight, imageRectBottom)

        // Save the canvas state before clipping
        val saveCount = canvas.save()

        // Apply clipping to constrain drawings to the image boundaries
        canvas.clipRect(imageClipRect)

        // Draw canvas bitmap with drawings
        canvasBitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, null)
        }

        // Draw movable items (without selection controls)
        for (item in movableItems) {
            when (item) {
                is MovableItem.TextItem -> {
                    // Use the drawWithStyle method if style is present, otherwise fallback to old method
                    if (item.textStyle != null) {
                        item.applyStyle()
                        item.drawWithStyle(canvas)
                    } else {
                        // Save canvas state for transformations
                        canvas.save()

                        // Calculate the center of the text for rotation
                        val bounds = item.getBounds()
                        val centerX = bounds.centerX()
                        val centerY = bounds.centerY()

                        // Apply rotation around the center
                        canvas.rotate(item.rotation, centerX, centerY)

                        // Apply scaling
                        canvas.scale(item.scale, item.scale, centerX, centerY)

                        // Draw text
                        val lines = item.text.split('\n')
                        val lineHeight = item.paint.fontSpacing
                        var currentY = item.y
                        for (line in lines) {
                            canvas.drawText(line, item.x, currentY, item.paint)
                            currentY += lineHeight
                        }

                        // Restore canvas state
                        canvas.restore()
                    }
                }

                is MovableItem.ImageItem -> {
                    canvas.withSave {
                        val centerX = item.x + item.bitmap.width / 2f
                        val centerY = item.y + item.bitmap.height / 2f

                        rotate(item.rotation, centerX, centerY)
                        scale(item.scale, item.scale, centerX, centerY)
                        drawBitmap(item.bitmap, item.x, item.y, null)
                    }
                }
            }
        }

        // Restore canvas state
        canvas.restoreToCount(saveCount)

        return result
    }

    // Helper method to calculate distance between two points
    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
    }

    // Helper method to calculate distance between fingers in a multi-touch event
    private fun spacing(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return sqrt(x * x + y * y)
    }

    // Helper method to find the midpoint between two fingers
    private fun getMidPoint(event: MotionEvent): PointF {
        val x = (event.getX(0) + event.getX(1)) / 2
        val y = (event.getY(0) + event.getY(1)) / 2
        return PointF(x, y)
    }

    // Helper method to calculate angle between center point and touch point
    private fun calculateAngle(
        centerX: Float,
        centerY: Float,
        touchX: Float,
        touchY: Float
    ): Float {
        val deltaX = touchX - centerX
        val deltaY = touchY - centerY

        // Calculate angle in radians then convert to degrees
        var angle = Math.toDegrees(atan2(deltaY.toDouble(), deltaX.toDouble())).toFloat()

        // Convert to 0-360 range
        if (angle < 0) {
            angle += 360f
        }

        return angle
    }

    // Helper method to transform a touch point into unrotated item coordinates
    private fun transformPointToItemCoordinates(
        touchX: Float,
        touchY: Float,
        rotation: Float,
        itemCenterX: Float,
        itemCenterY: Float
    ): FloatArray {
        // If no rotation, just return the original point
        if (rotation == 0f) {
            return floatArrayOf(touchX, touchY)
        }

        // Calculate distance from item center to touch point
        val dx = touchX - itemCenterX
        val dy = touchY - itemCenterY

        // Calculate distance
        val distance = sqrt(dx * dx + dy * dy)

        // Calculate current angle of the touch point relative to center
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

        // Subtract the item's rotation to get the angle in the unrotated coordinate system
        angle -= rotation

        // Convert back to radians
        val radians = Math.toRadians(angle.toDouble())

        // Calculate new position in unrotated coordinates
        val newX = itemCenterX + distance * cos(radians).toFloat()
        val newY = itemCenterY + distance * sin(radians).toFloat()

        return floatArrayOf(newX, newY)
    }

    /**
     * Check if there is a background bitmap set
     * @return true if a background bitmap exists, false otherwise
     */
    fun hasBackgroundBitmap(): Boolean {
        return backgroundBitmap != null
    }

    /**
     * Reset to the original background bitmap
     * This clears all drawings and sets the background to the original image
     */
    fun resetToOriginalBitmap() {
        // Check if we have an original bitmap to reset to
        if (originalBackgroundBitmap != null) {
            // Clear any drawings
            clearDrawings()

            // Reset to original bitmap
            originalBackgroundBitmap?.let {
                val config = it.config ?: Bitmap.Config.ARGB_8888
                setBackgroundBitmap(it.copy(config, true))
            }
        }
    }

    /**
     * Clear all drawings on the canvas
     */
    fun clearDrawings() {
        canvasBitmap?.eraseColor(Color.TRANSPARENT)
        invalidate()
    }

    /**
     * Get the current background bitmap
     * @return The current background bitmap or null if not set
     */
    fun getBackgroundBitmap(): Bitmap? {
        return backgroundBitmap
    }

    /**
     * Clear the background bitmap but maintain the dimensions and drawing area
     * This is useful when we want to keep drawings but make the background transparent
     */
    fun clearBackground() {
        if (backgroundBitmap != null) {
            // Create a transparent bitmap with the same dimensions
            val safeConfig = backgroundBitmap!!.config ?: Bitmap.Config.ARGB_8888
            val transparentBitmap = createBitmap(
                backgroundBitmap!!.width,
                backgroundBitmap!!.height,
                safeConfig
            )

            // Set it as the background
            val originalBg = backgroundBitmap
            backgroundBitmap = transparentBitmap

            // No need to call setupDrawingBitmap() again since we're keeping the same dimensions
            // Just make sure to invalidate the view
            invalidate()
        }
    }

    /**
     * Set a listener for text edit requests (triggered by double-clicking a text item)
     */
    fun setOnTextEditRequestListener(listener: (MovableItem.TextItem) -> Unit) {
        onTextEditRequestListener = listener
    }

    /**
     * Update the properties of an existing text item
     */
    fun updateTextItem(textItem: MovableItem.TextItem, textStyle: TextStyle) {
        textItem.textStyle = textStyle
        textItem.text = textStyle.text
        textItem.applyStyle()
        invalidate()
    }

    // Add this method to check if an item is near or crossing the boundary
    private fun checkAndHighlightBoundaries(item: MovableItem) {
        if (backgroundBitmap == null) return

        val itemBounds = when (item) {
            is MovableItem.TextItem -> item.getBounds()
            is MovableItem.ImageItem -> {
                val width = item.bitmap.width * item.scale
                val height = item.bitmap.height * item.scale
                RectF(item.x, item.y, item.x + width, item.y + height)
            }
        }

        // Create a larger image rect for "near boundary" detection
        val expandedImageRect = RectF(
            imageRectLeft - 10,
            imageRectTop - 10,
            imageRectRight + 10,
            imageRectBottom + 10
        )

        // Check if item is crossing or near the boundary
        if (!expandedImageRect.contains(itemBounds)) {
            showClipBoundary = true
            clipHighlightAlpha = 180
            clipHighlightHandler.removeCallbacks(clipHighlightRunnable)
            invalidate()

            // Start the fade-out effect
            clipHighlightHandler.postDelayed(clipHighlightRunnable, clipHighlightFadeDuration)
        }
    }

    // In the onTouchEvent method, update the ACTION_MOVE case where items are dragged
    // Find the part where currentDraggedItem is moved and add this:

    private fun moveItemWithConstraints(item: MovableItem, x: Float, y: Float) {
        // Store original position
        val originalX = item.x
        val originalY = item.y

        // Move the item
        item.x = x - touchOffsetX
        item.y = y - touchOffsetY

        // Check and highlight boundaries if needed
        checkAndHighlightBoundaries(item)

        // Since clipping will handle displaying, we don't need to constrain the actual position anymore
        // Just let the item be positioned wherever the user wants
    }
}

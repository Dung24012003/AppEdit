package com.example.dungappedit.ui.camera.stikcer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

class FaceStickerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs) {

    private val faces = mutableListOf<Face>()
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0
    private var isFrontFacing: Boolean = false

    var selectedSticker: Sticker = Sticker.NONE
        set(value) {
            field = value
            postInvalidate()
        }

    private val stickerBitmaps = mutableMapOf<Sticker, Bitmap>()

    fun setSourceInfo(width: Int, height: Int, isFront: Boolean) {
        if (width > 0 && height > 0) {
            this.imageWidth = width
            this.imageHeight = height
        }
        this.isFrontFacing = isFront
        postInvalidate()
    }

    fun updateFaces(newFaces: List<Face>) {
        faces.clear()
        faces.addAll(newFaces)
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (selectedSticker == Sticker.NONE || faces.isEmpty() || imageWidth == 0 || imageHeight == 0) return
        for (face in faces) {
            drawStickerForFace(canvas, face, width, height)
        }
    }

    fun drawStickerOnCanvas(canvas: Canvas, face: Face) {
        if (selectedSticker == Sticker.NONE || imageWidth == 0 || imageHeight == 0) return
        drawStickerForFace(canvas, face, canvas.width, canvas.height)
    }

    private fun drawStickerForFace(
        canvas: Canvas,
        face: Face,
        targetWidth: Int,
        targetHeight: Int
    ) {
        when (selectedSticker) {
            Sticker.HAT -> drawHat(canvas, face, targetWidth, targetHeight)
            Sticker.HATBunny -> drawHatBunny(canvas, face, targetWidth, targetHeight)
            Sticker.FaceBatman -> drawFaceDisguise(canvas, face, targetWidth, targetHeight)
            Sticker.GLASSES -> drawGlasses(canvas, face, targetWidth, targetHeight)
            Sticker.MASK -> drawMask(canvas, face, targetWidth, targetHeight)
            Sticker.FaceCat -> drawFace(canvas, face, targetWidth, targetHeight)
            else -> { /* Do nothing */
            }
        }
    }

    // This is the key function, it calculates the scale based on the whole face ratio
    private fun getStandardScale(
        face: Face,
        bitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int
    ): Float {
        val scaledFaceWidth = scaleX(face.boundingBox.width().toFloat(), targetWidth)
        val scaledFaceHeight = scaleY(face.boundingBox.height().toFloat(), targetHeight)

        val scaleXFactor = scaledFaceWidth / bitmap.width.toFloat()
        val scaleYFactor = scaledFaceHeight / bitmap.height.toFloat()

        return max(scaleXFactor, scaleYFactor)
    }


    private fun drawHat(canvas: Canvas, face: Face, targetWidth: Int, targetHeight: Int) {
        val hatBitmap = getStickerBitmap(Sticker.HAT) ?: return

        val scale =
            getStandardScale(face, hatBitmap, targetWidth, targetHeight) * 0.8f // Example: make it 20% smaller

        val angle = if (isFrontFacing) face.headEulerAngleZ else -face.headEulerAngleZ
        val pivotX = translateX(face.boundingBox.exactCenterX(), targetWidth)
        val pivotY = translateY(face.boundingBox.exactCenterY(), targetHeight)

        val topOfFaceY = translateY(face.boundingBox.top.toFloat(), targetHeight)
        val scaledHatHeight = hatBitmap.height * scale

        val initialHatCenterY =
            topOfFaceY - (scaledHatHeight * 0.3f)

        // The hat's center X is always the face's center (pivotX).
        val (finalHatCenterX, finalHatCenterY) = getRotatedPoint(
            pivotX,
            initialHatCenterY,
            pivotX,
            pivotY,
            angle
        )

        // STEP 5: DRAW THE STICKER
        drawBitmapWithTransform(
            canvas,
            hatBitmap,
            finalHatCenterX,
            finalHatCenterY,
            scale,
            scale,
            angle
        )
    }

    private fun drawHatBunny(canvas: Canvas, face: Face, targetWidth: Int, targetHeight: Int) {
        val hatBitmap = getStickerBitmap(Sticker.HATBunny) ?: return

        val scale = getStandardScale(
            face,
            hatBitmap,
            targetWidth,
            targetHeight
        ) * 0.8f

        val angle = if (isFrontFacing) face.headEulerAngleZ else -face.headEulerAngleZ
        val pivotX = translateX(face.boundingBox.exactCenterX(), targetWidth)
        val pivotY = translateY(face.boundingBox.exactCenterY(), targetHeight)

        val topOfFaceY = translateY(face.boundingBox.top.toFloat(), targetHeight)
        val scaledHatHeight = hatBitmap.height * scale

        val initialHatCenterY = topOfFaceY - (scaledHatHeight * 0.3f)

        val (finalHatCenterX, finalHatCenterY) = getRotatedPoint(
            pivotX,
            initialHatCenterY,
            pivotX,
            pivotY,
            angle
        )

        // STEP 5: DRAW THE STICKER
        drawBitmapWithTransform(
            canvas,
            hatBitmap,
            finalHatCenterX,
            finalHatCenterY,
            scale,
            scale,
            angle
        )
    }

    private fun drawFaceDisguise(canvas: Canvas, face: Face, targetWidth: Int, targetHeight: Int) {
        val disguiseBitmap = getStickerBitmap(Sticker.FaceBatman) ?: return
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)

        if (leftEye != null && rightEye != null) {

            // STEP 1: CALCULATE SIZE (SCALE) TO STRETCH WITH RATIO
            // Use getStandardScale to ensure it scales correctly.
            // You can adjust the 1.2f coefficient to make the mask relatively larger/smaller.
            val scale = getStandardScale(face, disguiseBitmap, targetWidth, targetHeight) * 1.2f

            // STEP 2: CALCULATE ROTATION ANGLE BASED ON THE TWO EYES
            // This is the most accurate way to make the mask rotate to match the eyes.
            val dY =
                scaleY(rightEye.position.y, targetHeight) - scaleY(leftEye.position.y, targetHeight)
            val dX =
                scaleX(rightEye.position.x, targetWidth) - scaleX(leftEye.position.x, targetWidth)
            var angle = Math.toDegrees(atan2(dY.toDouble(), dX.toDouble())).toFloat()
            if (isFrontFacing) {
                angle = -angle
            }

            // STEP 3: CALCULATE POSITION TO FIT THE EYES
            // The final position of the mask is the center between the two eyes.
            val eyeCenterX =
                translateX((leftEye.position.x + rightEye.position.x) / 2f, targetWidth)
            val eyeCenterY =
                translateY((leftEye.position.y + rightEye.position.y) / 2f, targetHeight) - 150f

            // STEP 4: DRAW THE STICKER
            // We use the scale calculated in step 1, and the position/angle calculated in steps 2 & 3.
            drawBitmapWithTransform(
                canvas,
                disguiseBitmap,
                eyeCenterX,
                eyeCenterY,
                scale,
                scale,
                angle
            )
        }
    }

    private fun drawGlasses(canvas: Canvas, face: Face, targetWidth: Int, targetHeight: Int) {
        val glassesBitmap = getStickerBitmap(Sticker.GLASSES) ?: return
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)

        if (leftEye != null && rightEye != null) {
            // SIZE
            val scale = getStandardScale(face, glassesBitmap, targetWidth, targetHeight) * 0.5f

            // ROTATION ANGLE & POSITION
            // Similar to Batman, the angle and position must be based on the eyes.
            val dY =
                scaleY(rightEye.position.y, targetHeight) - scaleY(leftEye.position.y, targetHeight)
            val dX =
                scaleX(rightEye.position.x, targetWidth) - scaleX(leftEye.position.x, targetWidth)
            var angle = Math.toDegrees(atan2(dY.toDouble(), dX.toDouble())).toFloat()
            if (isFrontFacing) {
                angle = -angle
            }

            val finalEyeCenterX =
                translateX((leftEye.position.x + rightEye.position.x) / 2f, targetWidth)
            val finalEyeCenterY =
                translateY((leftEye.position.y + rightEye.position.y) / 2f, targetHeight)

            drawBitmapWithTransform(
                canvas,
                glassesBitmap,
                finalEyeCenterX,
                finalEyeCenterY,
                scale,
                scale,
                angle
            )
        }
    }

    private fun drawMask(canvas: Canvas, face: Face, targetWidth: Int, targetHeight: Int) {
        val maskBitmap = getStickerBitmap(Sticker.MASK) ?: return
        val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)

        if (noseBase != null) {
            // SIZE
            val scale = getStandardScale(face, maskBitmap, targetWidth, targetHeight) * 0.5f

            // ROTATION ANGLE AND PIVOT POINT
            // The mask's rotation angle should follow the head's tilt angle
            val angle = if (isFrontFacing) face.headEulerAngleZ else -face.headEulerAngleZ
            // The pivot point is still the center of the face for the most natural result
            val pivotX = translateX(face.boundingBox.exactCenterX(), targetWidth)
            val pivotY = translateY(face.boundingBox.exactCenterY(), targetHeight)

            // INITIAL POSITION (BEFORE ROTATION)
            // The anchor position for the mask is the base of the nose
            val noseBaseX = translateX(noseBase.position.x, targetWidth)
            val noseBaseY = translateY(noseBase.position.y, targetHeight)
            val scaledMaskHeight = maskBitmap.height * scale
            val initialMaskCenterY = noseBaseY + scaledMaskHeight * 0.3f // Shift down a bit

            // CALCULATE FINAL POSITION AFTER ROTATION
            val (finalMaskCenterX, finalMaskCenterY) = getRotatedPoint(
                noseBaseX,
                initialMaskCenterY,
                pivotX,
                pivotY,
                angle
            )

            drawBitmapWithTransform(
                canvas,
                maskBitmap,
                finalMaskCenterX,
                finalMaskCenterY,
                scale,
                scale,
                angle
            )
        }
    }

    // This is a standard function, keep it as is
    private fun drawFace(canvas: Canvas, face: Face, targetWidth: Int, targetHeight: Int) {
        val faceBitmap = getStickerBitmap(Sticker.FaceCat) ?: return

        // Use the standard function to calculate scale
        val scale = getStandardScale(face, faceBitmap, targetWidth, targetHeight) * 1.5f

        val angle = if (isFrontFacing) face.headEulerAngleZ else -face.headEulerAngleZ
        val pivotX = translateX(face.boundingBox.exactCenterX(), targetWidth)
        val pivotY = translateY(face.boundingBox.exactCenterY(), targetHeight)

        // Sticker position relative to the face center
        val scaledFaceHeight = scaleY(face.boundingBox.height().toFloat(), targetHeight)
        val verticalOffset = scaledFaceHeight * 0.1f
        val finalCenterY = pivotY - verticalOffset

        drawBitmapWithTransform(canvas, faceBitmap, pivotX, finalCenterY, scale, scale, angle)
    }

    // --- UTILITY FUNCTIONS (unchanged) ---
    private fun getRotatedPoint(
        initialX: Float,
        initialY: Float,
        pivotX: Float,
        pivotY: Float,
        angle: Float
    ): Pair<Float, Float> {
        val point = floatArrayOf(initialX, initialY)
        Matrix().apply { setRotate(angle, pivotX, pivotY) }.mapPoints(point)
        return Pair(point[0], point[1])
    }

    private fun drawBitmapWithTransform(
        canvas: Canvas,
        bitmap: Bitmap,
        centerX: Float,
        centerY: Float,
        scaleX: Float,
        scaleY: Float,
        angle: Float
    ) {
        val matrix = Matrix()
        matrix.postTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
        matrix.postScale(scaleX, scaleY)
        matrix.postRotate(angle)
        matrix.postTranslate(centerX, centerY)
        canvas.drawBitmap(bitmap, matrix, null)
    }

    private fun getStickerBitmap(sticker: Sticker): Bitmap? {
        if (sticker == Sticker.NONE) return null
        if (!stickerBitmaps.containsKey(sticker)) {
            stickerBitmaps[sticker] = BitmapFactory.decodeResource(resources, sticker.drawableId)
        }
        return stickerBitmaps[sticker]
    }

    private fun scaleX(x: Float, targetWidth: Int): Float =
        x * (targetWidth.toFloat() / imageWidth.toFloat())

    private fun scaleY(y: Float, targetHeight: Int): Float =
        y * (targetHeight.toFloat() / imageHeight.toFloat())

    private fun translateX(x: Float, targetWidth: Int): Float =
        if (isFrontFacing) targetWidth.toFloat() - scaleX(x, targetWidth) else scaleX(
            x,
            targetWidth
        )

    private fun translateY(y: Float, targetHeight: Int): Float = scaleY(y, targetHeight)
}

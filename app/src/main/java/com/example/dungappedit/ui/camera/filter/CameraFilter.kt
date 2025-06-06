package com.example.dungappedit.ui.camera.filter

import android.graphics.PointF
import jp.co.cyberagent.android.gpuimage.filter.GPUImageBrightnessFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageColorMatrixFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageContrastFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import jp.co.cyberagent.android.gpuimage.filter.GPUImageGrayscaleFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSaturationFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSketchFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageToonFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageVignetteFilter

enum class CameraFilter {
    ORIGINAL,
    BRIGHT,
    DARK,
    WARM,
    COOL,
    COMIC,
    PENCIL,
    BLING;

    fun createFilter(): GPUImageFilter {
        return when (this) {
            ORIGINAL -> GPUImageFilter()
            BRIGHT -> GPUImageBrightnessFilter(0.3f)
            DARK -> GPUImageBrightnessFilter(-0.3f)
            WARM -> GPUImageColorMatrixFilter(
                1.0f, floatArrayOf(
                    1.0f, 0.9f, 0.7f, 0f,
                    0.9f, 0.8f, 0.5f, 0f,
                    0.6f, 0.5f, 0.3f, 0f,
                    0f, 0f, 0f, 1.0f
                )
            )

            COOL -> GPUImageColorMatrixFilter(
                1.0f, floatArrayOf(
                    0.7f, 0.9f, 1.2f, 0f,
                    0.7f, 0.9f, 1.2f, 0f,
                    0.7f, 0.9f, 1.2f, 0f,
                    0f, 0f, 0f, 1.0f
                )
            )

            COMIC -> {
                GPUImageFilterGroup().apply {
                    // Tăng độ tương phản cho viền sắc nét
                    addFilter(GPUImageContrastFilter(1.7f))

                    // Tăng độ bão hòa màu
                    addFilter(GPUImageSaturationFilter(1.5f))

                    addFilter(GPUImageToonFilter().apply {
                        setThreshold(0.15f)        // Giảm ngưỡng để phát hiện viền tốt hơn
                        setQuantizationLevels(10.0f) // Tăng mức để màu mịn hơn
                    })

                    // Ma trận màu để làm sắc nét
                    addFilter(
                        GPUImageColorMatrixFilter(
                            1.2f, floatArrayOf(
                                1.3f, -0.1f, -0.1f, 0.0f,
                                -0.1f, 1.3f, -0.1f, 0.0f,
                                -0.1f, -0.1f, 1.3f, 0.0f,
                                0.0f, 0.0f, 0.0f, 1.0f
                            )
                        )
                    )
                }
            }

            PENCIL -> {
                GPUImageFilterGroup().apply {
                    addFilter(GPUImageContrastFilter(1.2f))

                    // Chuyển ảnh thành đen trắng
                    addFilter(GPUImageGrayscaleFilter())

                    // Hiệu ứng vẽ bút chì
                    addFilter(GPUImageSketchFilter())

                    // Tăng độ tương phản cho nét vẽ rõ hơn
                    addFilter(GPUImageContrastFilter(1.6f))

                    // Tăng độ sáng nhẹ để hiện chi tiết vùng tối
                    addFilter(GPUImageBrightnessFilter(0.05f))

                    // Ma trận màu làm sắc nét đường nét
                    addFilter(
                        GPUImageColorMatrixFilter(
                            1.0f, floatArrayOf(
                                1.5f, -0.1f, -0.1f, 0.0f,
                                -0.1f, 1.5f, -0.1f, 0.0f,
                                -0.1f, -0.1f, 1.5f, 0.0f,
                                0.0f, 0.0f, 0.0f, 1.0f
                            )
                        )
                    )
                }
            }

            BLING -> GPUImageFilterGroup().apply {
                addFilter(
                    GPUImageVignetteFilter(
                        PointF(0.5f, 0.5f),
                        floatArrayOf(0f, 0f, 0f),
                        0.1f,
                        0.7f
                    )
                ) // Viền tối nhẹ
                addFilter(GPUImageBrightnessFilter(0.05f))  // Tăng sáng nhẹ
                addFilter(GPUImageContrastFilter(1.1f))     // Tăng tương phản nhẹ
                addFilter(GPUImageSaturationFilter(0.85f))  // Giảm độ bão hòa một chút cho dịu màu
            }

        }
    }
}

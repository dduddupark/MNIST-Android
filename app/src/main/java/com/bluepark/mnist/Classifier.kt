package com.bluepark.mnist

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.scale
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.Executors

class Classifier(val context: Context) {

    var isInitialized = false

    private val executorService = Executors.newCachedThreadPool()
    private lateinit var interpreter: Interpreter

    private var modelInputChannel = 0
    private var modelInputWidth = 0
    private var modelInputHeight = 0

    private var modelOutputClasses = 99

    companion object {
        private const val MODEL_NAME = "mnist_google.tflite"
    }

    init {
        initialize()
    }

    fun classifyTask(bitmap: Bitmap): Task<String> {
        val task = TaskCompletionSource<String>()
        executorService.execute {
            val result = classify(bitmap)
            task.setResult(result)
        }
        return task.task
    }

    fun initialize(): Task<Void> {
        val task = TaskCompletionSource<Void>()
        executorService.execute {
            try {
                initializeInterpreter()
                task.setResult(null)
            } catch (e: Exception) {
                task.setException(e)
            }
        }
        return task.task
    }

    /**
     * 사용자 입력 > 이미지로 변경
     */

    //Classifier에 bitmap 전달
    //1080,932 > 28,28
    fun Bitmap.resizeBimap(): Bitmap {
        return this.scale(modelInputWidth, modelInputHeight, true)
    }

    fun loadModelFile(assetManager: AssetManager): ByteBuffer {
        val fileDescriptor = assetManager.openFd(MODEL_NAME)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannels = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannels.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun initializeInterpreter() {
        val assetManager = context.assets
        val model = loadModelFile(assetManager)
        val options = Interpreter.Options()
        options.setUseNNAPI(true)
        val interpreter = Interpreter(model, options)

        val inputShape = interpreter.getInputTensor(0).shape()
        modelInputChannel = inputShape[0]
        modelInputWidth = inputShape[1]
        modelInputHeight = inputShape[2]

        Log.d(TAG, "modelInputWidth = $modelInputWidth, modelInputHeight = $modelInputHeight")

        val outputShape = interpreter.getOutputTensor(0).shape()
        modelOutputClasses = outputShape[1]

        this.interpreter = interpreter
        isInitialized = true
    }

    private fun classify(bitmap: Bitmap): String {
        check(isInitialized) {
            return "Interpreter is not initialized"
        }

        val input = convertBitmapToByteBuffer(bitmap.resizeBimap())
        val output = Array(1) { FloatArray(modelOutputClasses) }

        Log.d(TAG, "input = $input, output = ${output.size}")

        interpreter.run(input, output)

        val result = output[0]
        val maxIndex = result.indices.maxBy { result[it] } ?: -1

        Log.d(TAG, "result = ${result.indices}")

        val resultString = "Prediction Result: %d, Confidence: %2f".format(maxIndex, result[maxIndex])

        return resultString
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(bitmap.byteCount)

        //byteBuffer에 데이터를 쓸 때, 현재 시스템의 바이트 순서에 맞춰 저장하라는 뜻입니다.
        //이는 특히 TensorFlow Lite나 JNI, 바이너리 파일 입출력 시에 매우 중요합니다.
        //바이트 순서가 잘못되면 숫자가 완전히 엉뚱하게 해석됩니다.
        byteBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(modelInputWidth * modelInputHeight)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF)
            val g = (pixel shr 8 and 0xFF)
            val b = (pixel and 0xFF)

            val normalizedPixel = (r+g+b) / 3.0f / 255.0f
            byteBuffer.putFloat(normalizedPixel)
        }
        return byteBuffer
    }

    fun close() {
        executorService.execute {
            interpreter.close()
        }
    }

    // 이미지로 변경된 입력 > TFLite 라이브러리에 전달
    // TFLite 라이브러리 결과 > 분류 결과, 확률 출력
}
package com.omzer.tflitescaffold.machinelearning


import android.app.Activity
import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks.call
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.collections.ArrayList


class ColorsClassifier(private val activity: Activity) {
    // Processing
    private lateinit var interpreter: Interpreter
    private lateinit var labels: ArrayList<String>
    private val executorService: ExecutorService = Executors.newCachedThreadPool()
    private var gpuDelegate: GpuDelegate? = null
    private var isInitialized = false

    // Image variables
    private var inputImageWidth: Int = 0
    private var inputImageHeight: Int = 0
    private var modelInputSize: Int = 0

    companion object {
        private const val FLOAT_TYPE_SIZE = 4
        private const val CHANNEL_SIZE = 3
        private const val IMAGE_MEAN = 127.5f
        private const val IMAGE_STD = 127.5f
    }

    fun initialize(): Task<Void>? {
        return try {
            call<Void>(
                executorService,
                Callable {
                    initializeInterpreter()
                    null
                }
            )
        } catch (ex: Exception) {
            return null
        }
    }

    @Throws(Exception::class)
    private fun initializeInterpreter() {
        val model = loadModelFile()
        labels = loadLines()
        val options = Interpreter.Options()
        gpuDelegate = GpuDelegate()
        options.addDelegate(gpuDelegate)
        val interpreter = Interpreter(model, options)

        val inputShape = interpreter.getInputTensor(0).shape()
        inputImageWidth = inputShape[1]
        inputImageHeight = inputShape[2]
        modelInputSize = FLOAT_TYPE_SIZE * inputImageWidth * inputImageHeight * CHANNEL_SIZE

        this.interpreter = interpreter

        isInitialized = true
    }


    private fun classify(bitmap: Bitmap): String {

        check(isInitialized) { "TF Lite Interpreter is not initialized yet." }
        val resizedImage = Bitmap.createScaledBitmap(
            bitmap,
            inputImageWidth,
            inputImageHeight,
            true
        )

        val byteBuffer = convertBitmapToByteBuffer(resizedImage)

        val output = Array(1) { FloatArray(labels.size) }
        interpreter.run(byteBuffer, output)
        val index = getMaxResult(output[0])

        return labels[index]
    }

    private fun getMaxResult(result: FloatArray): Int {
        var probability = result[0]
        var index = 0
        for (i in result.indices) {
            if (probability < result[i]) {
                probability = result[i]
                index = i
            }
        }
        return index
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(modelInputSize)
        byteBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputImageWidth * inputImageHeight)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var pixel = 0
        for (i in 0 until inputImageWidth) {
            for (j in 0 until inputImageHeight) {
                val pixelVal = pixels[pixel++]

                byteBuffer.putFloat(((pixelVal shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                byteBuffer.putFloat(((pixelVal shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                byteBuffer.putFloat(((pixelVal and 0xFF) - IMAGE_MEAN) / IMAGE_STD)

            }
        }
        bitmap.recycle()

        return byteBuffer
    }


    fun classifyAsync(bitmap: Bitmap): Task<String> {
        return call(executorService, { classify(bitmap) })
    }

    private fun loadModelFile(): ByteBuffer {
        val fileDescriptor = activity.assets.openFd(getModelPath())
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadLines(): ArrayList<String> {
        val s = Scanner(InputStreamReader(activity.assets.open(getLabelsPath())))
        val labels = ArrayList<String>()
        while (s.hasNextLine()) labels.add(s.nextLine())
        s.close()
        return labels
    }

    private fun getModelPath(): String = "model.tflite"
    private fun getLabelsPath(): String = "labels.txt"


}
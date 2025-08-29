package co.kobby.clinicalaide.services

import android.content.Context
import android.content.res.AssetFileDescriptor
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads and manages TensorFlow Lite models from assets.
 */
@Singleton
class TFLiteModelLoader @Inject constructor(
    private val context: Context
) {
    private var interpreter: Interpreter? = null
    private val interpreterOptions = Interpreter.Options().apply {
        setNumThreads(4) // Use 4 threads for better performance
        setUseNNAPI(false) // Disable NNAPI for compatibility
    }
    
    /**
     * Load a TFLite model from assets folder.
     */
    fun loadModelFromAssets(modelPath: String): Interpreter {
        if (interpreter == null) {
            val modelBuffer = loadModelFile(modelPath)
            interpreter = Interpreter(modelBuffer, interpreterOptions)
        }
        return interpreter!!
    }
    
    /**
     * Load model file from assets as MappedByteBuffer.
     */
    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val fileDescriptor: AssetFileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel: FileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    
    /**
     * Get input tensor shape.
     */
    fun getInputShape(): IntArray? {
        return interpreter?.getInputTensor(0)?.shape()
    }
    
    /**
     * Get output tensor shape.
     */
    fun getOutputShape(): IntArray? {
        return interpreter?.getOutputTensor(0)?.shape()
    }
    
    /**
     * Get input tensor data type.
     */
    fun getInputDataType(): String? {
        return interpreter?.getInputTensor(0)?.dataType()?.toString()
    }
    
    /**
     * Get output tensor data type.
     */
    fun getOutputDataType(): String? {
        return interpreter?.getOutputTensor(0)?.dataType()?.toString()
    }
    
    /**
     * Close the interpreter and free resources.
     */
    fun close() {
        interpreter?.close()
        interpreter = null
    }
    
    /**
     * Check if model is loaded.
     */
    fun isModelLoaded(): Boolean {
        return interpreter != null
    }
    
    /**
     * Get model details for debugging.
     */
    fun getModelDetails(): String {
        return if (interpreter != null) {
            """
            Model Details:
            - Input Shape: ${getInputShape()?.contentToString()}
            - Input Type: ${getInputDataType()}
            - Output Shape: ${getOutputShape()?.contentToString()}
            - Output Type: ${getOutputDataType()}
            - Input Count: ${interpreter?.inputTensorCount}
            - Output Count: ${interpreter?.outputTensorCount}
            """.trimIndent()
        } else {
            "Model not loaded"
        }
    }
}
package com.omzer.tflitescaffold


import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.lifecycle.ViewModelProvider
import com.omzer.tflitescaffold.machinelearning.ColorsClassifier
import com.omzer.tflitescaffold.viewmodel.CameraViewModel


class MainActivity : AppCompatActivity() {
    private lateinit var classifier: ColorsClassifier
    private val handler: Handler = Handler()
    private lateinit var runnable: Runnable
    private lateinit var cameraPreview: PreviewView
    private lateinit var rootLayout: View

    companion object {
        private val RED: Int = Color.parseColor("#F7072B")
        private val GREEN: Int = Color.parseColor("#3BEB6A")
        private val BLUE: Int = Color.parseColor("#076FF7")
        private val ORANGE: Int = Color.parseColor("#D99116")
        private val YELLOW: Int = Color.parseColor("#E7EB09")
        private val BLANK: Int = Color.parseColor("#FFFFFF")
    }

    private lateinit var cameraViewModel: CameraViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViewsAndModels()
        cameraViewModel.setupCamera()
        initializeClassifier()
    }

    private fun initializeClassifier() {
        val initializingTask = classifier.initialize()
        initializingTask?.addOnSuccessListener {
            runTheCamera()
            handler.postDelayed(runnable, 2000)
        }
        initializingTask?.addOnFailureListener { finish() }
    }

    private fun runTheCamera() {
        if (cameraViewModel.allPermissionsGranted()) {
            start()
        } else {
            cameraViewModel.askForPermissions()
        }
    }

    private fun initViewsAndModels() {
        // init views
        cameraPreview = findViewById(R.id.cameraPreview)
        rootLayout = findViewById(R.id.rootLayout)

        // init viewmodel
        cameraViewModel = ViewModelProvider(this).get(CameraViewModel::class.java)

        // init camera
        cameraViewModel.setActivity(this, cameraPreview)
        classifier = ColorsClassifier(this)
        runnable = Runnable {
            val classifierTask = classifier.classifyAsync(cameraPreview.bitmap!!)

            classifierTask.addOnSuccessListener {
                onClassificationResultReceived(it)
                handler.postDelayed(runnable, 200)
            }
            classifierTask.addOnFailureListener { it.printStackTrace() }

        }
    }

    private fun onClassificationResultReceived(result: String) {
        Log.i("Tag", result)
        // TODO: change to your liking
        when (result) {
            "Blank" -> rootLayout.setBackgroundColor(BLANK)
            "Red" -> rootLayout.setBackgroundColor(RED)
            "Green" -> rootLayout.setBackgroundColor(GREEN)
            "Blue" -> rootLayout.setBackgroundColor(BLUE)
            "Yellow" -> rootLayout.setBackgroundColor(YELLOW)
            "Orange" -> rootLayout.setBackgroundColor(ORANGE)
        }
    }

    private fun start() {
        cameraViewModel.startCamera()
    }

    override fun onRequestPermissionsResult(requestCode: Int, p: Array<String>, res: IntArray) {
        cameraViewModel.onRequestPermissionsResult(requestCode)
    }

    override fun onDestroy() {
        handler.removeCallbacks(runnable)
        super.onDestroy()
    }

}
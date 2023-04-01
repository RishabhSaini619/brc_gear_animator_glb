package com.kotlin.brc.gear_animator_glb

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceView
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.filament.Engine
import com.google.android.filament.View
import com.google.android.filament.utils.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import jgltf.FlamModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URL
import java.net.URLConnection
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {
    companion object{
        init {
            Utils.init()
        }
        val view = modelViewer.view
        private val fm = FlamModel()
        private lateinit var choreographer: Choreographer
        private lateinit var doubleTapDetector: GestureDetector
//        private val frameScheduler = FrameCallback()
//        private val doubleTapListener = DoubleTapListener()
        private val viewerContent = AutomationEngine.ViewerContent()
        private lateinit var bottomSheetBehavior: BottomSheetBehavior<*>
        private lateinit var bottomDrawerSheet: ConstraintLayout
        private lateinit var modelViewer: ModelViewer
        private lateinit var surfaceView: SurfaceView
        private lateinit var avatarUrlField: EditText
        private lateinit var animationUrlField: EditText
        private lateinit var glbAnimatorButton: MaterialButton
        private var isClicked=false
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.file_layout)
        choreographer = Choreographer.getInstance()
        surfaceView = findViewById(R.id.view_surface)

        bottomDrawerSheet = findViewById(R.id.bottom_drawer_sheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomDrawerSheet)

        setupEnvironment()
    }
    private fun setupEnvironment() {

        // on mobile, better use lower quality color buffer
        view.renderQuality = view.renderQuality.apply {
            hdrColorBuffer = View.QualityLevel.MEDIUM
        }

        // dynamic resolution often helps a lot
        view.dynamicResolutionOptions = view.dynamicResolutionOptions.apply {
            enabled = true
            quality = View.QualityLevel.MEDIUM
        }

        // MSAA is needed with dynamic resolution MEDIUM
        view.multiSampleAntiAliasingOptions = view.multiSampleAntiAliasingOptions.apply {
            enabled = true
        }

        // FXAA is pretty cheap and helps a lot
        view.antiAliasing = View.AntiAliasing.FXAA

        // ambient occlusion is the cheapest effect that adds a lot of quality
        view.ambientOcclusionOptions = view.ambientOcclusionOptions.apply {
            enabled = true
        }

        // bloom is pretty expensive but adds a fair amount of realism
        view.bloomOptions = view.bloomOptions.apply {
            enabled = true
        }

        doubleTapDetector = GestureDetector(applicationContext, doubleTapListener)
        modelViewer = ModelViewer(surfaceView, engine = Engine.create())
        viewerContent.view = modelViewer.view
        viewerContent.sunlight = modelViewer.light
        viewerContent.lightManager = modelViewer.engine.lightManager
        viewerContent.scene = modelViewer.scene
        viewerContent.renderer = modelViewer.renderer

        surfaceView.setOnTouchListener { _, event ->
            modelViewer.onTouchEvent(event)
            doubleTapDetector.onTouchEvent(event)
            true
        }

        setUpEventListener()
        createIndirectLight()
        createDefaultRenderables()
    }

}
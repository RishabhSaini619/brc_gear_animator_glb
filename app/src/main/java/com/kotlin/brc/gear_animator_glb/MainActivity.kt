package com.kotlin.brc.gear_animator_glb

import android.annotation.SuppressLint
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
import com.google.android.filament.Scene
import com.google.android.filament.View
import com.google.android.filament.utils.AutomationEngine
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
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
    }
    private val fm = FlamModel()
    private val frameScheduler = FrameCallback()
    private val doubleTapListener = DoubleTapListener()
    private val viewerContent = AutomationEngine.ViewerContent()
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<*>
    private lateinit var bottomDrawerSheet: ConstraintLayout
    private lateinit var doubleTapDetector: GestureDetector
    private lateinit var glbAnimatorButton: MaterialButton
    private lateinit var animationUrlField: EditText
    private lateinit var avatarUrlField: EditText
    private lateinit var choreographer: Choreographer
    private lateinit var modelViewer: ModelViewer
    private lateinit var surfaceView: SurfaceView
    private lateinit var engine : Engine
    private lateinit var scene : Scene
    private lateinit var ibl : String
    private lateinit var view : View
    private var isClicked=false

    inner class FrameCallback : Choreographer.FrameCallback {
        private val startTime = System.nanoTime()
        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)
            modelViewer.animator?.apply {
                if (animationCount > 0) {
                    val elapsedTimeSeconds = (frameTimeNanos - startTime).toDouble() / 1_000_000_000
                    applyAnimation(0, elapsedTimeSeconds.toFloat())
                }
                updateBoneMatrices()
            }
            modelViewer.render(frameTimeNanos)

        }
    }
    inner class DoubleTapListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            modelViewer.destroyModel()
            return super.onDoubleTap(e)
        }
    }

    private fun loadModel(){
        isClicked=true
        CoroutineScope(Dispatchers.IO).launch {
            val mergeBuffer = fm.getAnimatedModelBuffer(
                avatarUrl = avatarUrlField.text.toString(),
                animationUrl = animationUrlField.text.toString()
            )
            CoroutineScope(Dispatchers.Main).launch {
                if (mergeBuffer != null) {
                    val byteBufferWithRewind = mergeBuffer.rewind()
                    modelViewer.loadModelGlb(byteBufferWithRewind)
                }
                updateRootTransform()
                isClicked=false
            }
        }
    }
    private fun setupEnvironment() {
        modelViewer = ModelViewer(surfaceView)

        viewerContent.view = modelViewer.view
        viewerContent.scene = modelViewer.scene
        viewerContent.sunlight = modelViewer.light
        viewerContent.renderer = modelViewer.renderer
        viewerContent.lightManager = modelViewer.engine.lightManager
        doubleTapDetector = GestureDetector(applicationContext, doubleTapListener)

        view = modelViewer.view

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
    }
    @SuppressLint("ClickableViewAccessibility")
    private fun setUpEventListener() {
        bottomSheetBehavior.apply {
            peekHeight = 100
        }
        glbAnimatorButton.setOnTouchListener { _, _ ->
            Log.i("called","called")
            if(!isClicked) loadModel()
            true
        }
        surfaceView.setOnTouchListener { _, event ->
            modelViewer.onTouchEvent(event)
            doubleTapDetector.onTouchEvent(event)
            true
        }

    }
    private fun createIndirectLight() {

        ibl = "default_env"
        scene = modelViewer.scene
        engine = modelViewer.engine
        readCompressedAsset("${ibl}_ibl.ktx").let {
            scene.indirectLight = KTX1Loader.createIndirectLight(engine, it)
            scene.indirectLight!!.intensity = 30_000.0f
            viewerContent.indirectLight = modelViewer.scene.indirectLight
        }
        readCompressedAsset("${ibl}_skybox.ktx").let {
            scene.skybox = KTX1Loader.createSkybox(engine, it)
        }
    }
    private fun updateRootTransform() {
        modelViewer.transformToUnitCube()
    }
    private fun readCompressedAsset(assetName: String): ByteBuffer {
        val input = assets.open(assetName)
        val bytes = ByteArray(input.available())
        input.read(bytes)
        return ByteBuffer.wrap(bytes)
    }
    private fun createDefaultRenderables() {

        CoroutineScope(Dispatchers.IO).launch {

                val url = URL("https://models.readyplayer.me/64242ab4c9e8aa39b5d1b4dc.glb")
                val output = ByteArrayOutputStream()
                val conn: URLConnection = url.openConnection()
                conn.setRequestProperty("User-Agent", "Firefox")
                conn.getInputStream().use { inputStream ->
                    var n: Int
                    val buffer = ByteArray(1024)
                    while (-1 != inputStream.read(buffer).also { n = it }) {
                        output.write(buffer, 0, n)
                    }
                }
                val img: ByteArray = output.toByteArray()
                val imageBytes = ByteBuffer.wrap(img)
                Log.i("ready","ioThread $imageBytes")
            CoroutineScope(Dispatchers.Main).launch{
                    Log.i("ready","mainThread")
                    modelViewer.loadModelGlb(imageBytes)
                    updateRootTransform()
                }
            }

    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.file_layout)
        choreographer = Choreographer.getInstance()
        surfaceView = findViewById(R.id.view_surface)
        avatarUrlField = findViewById(R.id.avatar_url_field)
        animationUrlField = findViewById(R.id.animation_url_field)
        glbAnimatorButton = findViewById(R.id.animate_button)
        bottomDrawerSheet = findViewById(R.id.bottom_drawer_sheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomDrawerSheet)

        setupEnvironment()
        setUpEventListener()
        createIndirectLight()
        createDefaultRenderables()
    }
    override fun onResume() {
        super.onResume()
        choreographer.postFrameCallback(frameScheduler)
    }
    override fun onPause() {
        super.onPause()
        choreographer.removeFrameCallback(frameScheduler)
    }
    override fun onDestroy() {
        super.onDestroy()
        choreographer.removeFrameCallback(frameScheduler)
    }

}

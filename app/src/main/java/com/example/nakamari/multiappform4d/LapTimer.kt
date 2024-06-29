package com.example.nakamari.multiappform4d

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Button
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import com.example.nakamari.multiappform4d.databinding.ActivityLapTimerBinding
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.imgproc.Imgproc.GaussianBlur
import org.opencv.imgproc.Imgproc.cvtColor
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.schedule
import kotlin.math.abs


//typealias LumaListener = (luma: Double) -> Unit

class LapTimer : AppCompatActivity() {
    // 定数
    val DELAY_TIME_SEC : Int = 100

    // 計測可能状態かフラグ
    var recFlag = false
    // 計測中かフラグ
    var isRecNow = false
    // ラップタイム(Intでよさそう)
    var lapTimeSec : Long = 0
    // 検出用の感度(でかいほど鈍感)
    var accurDetect : Double = 0.0

    var startTime : Long = 0
    var finishTime : Long = 0
    val sdf : SimpleDateFormat = SimpleDateFormat("ss.SS")
    private lateinit var viewBinding: ActivityLapTimerBinding

    private lateinit var cameraExecutor: ExecutorService

    private val imgAnalyzer by lazy {
        ImageAnalysis.Builder()
            .setTargetResolution((Size(9, 21)))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
    }

    var recordTimeVal = Thread()
    var recordTimeDisp = Thread()

    override fun onStop() {
        super.onStop()
        if(this.recordTimeVal.isAlive) this.recordTimeVal.interrupt()
        if(this.recordTimeDisp.isAlive) this.recordTimeDisp.interrupt()

    }


    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityLapTimerBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        OpenCVLoader.initDebug()

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            Toast.makeText(this, "権限周りでやらかし", Toast.LENGTH_LONG).show()
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()


        var accuracyDetection : SeekBar = findViewById(R.id.detectAccur)
        var lapTime : TextView = findViewById(R.id.totalTime)
        var lblIsCalc : TextView = findViewById(R.id.lblIsCalc)
        val btnSet : Button = findViewById(R.id.btnSet)

        accuracyDetection.min = 0
        accuracyDetection.max = 100
        accuracyDetection.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                    accurDetect = 0.5 * p1 / 100
                    Log.d("", accurDetect.toString())
                }

                override fun onStartTrackingTouch(p0: SeekBar?) {
                }

                override fun onStopTrackingTouch(p0: SeekBar?) {
                }
            }
        )


        btnSet.setOnClickListener{
            // 一旦初期化
            this.recFlag = true
            this.isRecNow = false
            this.lapTimeSec = 0

            this.startTime = 0
            this.finishTime = 0
            createCalcTimeThread()
            createEditDispThread()
            lapTime.text = "00.00"
            lblIsCalc.text = "計測中"
            var lapModeChkBox : CheckBox = findViewById(R.id.lapMode)
            var lapMode : Boolean = lapModeChkBox.isChecked()
            //Log.d("", this.recordTimeVal.isAlive.toString())
            //calcLapTime(lapMode)
        }

        val btnReset : Button = findViewById(R.id.btnReset)
        btnReset.setOnClickListener{
            this.recFlag = false
            this.isRecNow = false
            this.lapTimeSec = 0
            this.startTime = 0
            this.finishTime = 0

            lapTime.text = "00.00"
            lblIsCalc.text = ""
            createCalcTimeThread()
            createEditDispThread()
        }


    }

    private fun createCalcTimeThread(){
        this.recordTimeVal = Thread {
            while(isRecNow){

                try {
                    Thread.sleep(10)
                    lapTimeSec = System.currentTimeMillis() - startTime
                    //Log.d("time", String.format("%1\$tM:%1\$tS.%1\$tL", lapTimeSec))
                } catch (e: InterruptedException){
                    break
                }

            }

        }
    }

    private fun createEditDispThread(){
        this.recordTimeDisp = Thread {
            var lapTime : TextView = findViewById(R.id.totalTime)
            while(isRecNow){

                try {
                    Thread.sleep(10)
                    //lapTime.text = String.format("%1\$tS.%1\$tL", lapTimeSec)
                    //Log.d("time", String.format("%1\$tM:%1\$tS.%1\$tL", lapTimeSec))
                    // 10ミリ秒単位にしたから一旦独自フォーマット(ミリ秒部分が3桁で末尾0になってしまう)
                    //lapTime.text = String.format("%1$02d.%2$02d", lapTimeSec / 1000 % 60, (lapTimeSec % 1000 / 10))
                    lapTime.text = sdf.format(lapTimeSec)
                } catch (e: InterruptedException){
                    break
                }

            }

        }
    }

    private inner class MyImageAnalyzer : ImageAnalysis.Analyzer {
        private var matPrevious: Mat? = null
        private var valPrev: Int? = null
        private var valCurrnt: Int? = 0
        override fun analyze(image: ImageProxy) {
            /* Create cv::mat(RGB888) from image(NV21) */
            val mat: Mat = getMatFromImage(image)
            val matGray: Mat = Mat()

            cvtColor(mat, matGray, Imgproc.COLOR_RGB2GRAY)
            GaussianBlur(matGray, matGray, org.opencv.core.Size(3.0, 3.0), 0.0)
            Imgproc.adaptiveThreshold(matGray, matGray, 255.0,
                Imgproc.ADAPTIVE_THRESH_MEAN_C,
                Imgproc.THRESH_BINARY_INV, 25, 2.0
            );


            if (matPrevious == null) matPrevious = matGray
            val matOutput: Mat = Mat()
            Core.absdiff(matGray, matPrevious, matOutput)
            //Log.d("loglog", Core.countNonZero(matOutput).toString())
            valCurrnt = Core.countNonZero(matOutput)
            if (valPrev == null) valPrev = Core.countNonZero(matOutput)
            //Log.d("", valCurrnt.toString() + " : " + this@LapTimer.recFlag + " : " + this@LapTimer.isRecNow + " : " + (Math.abs(valCurrnt!! - valPrev!!) > valCurrnt!! * 0.35) + " : " + this@LapTimer.lapTimeSec)
            Log.d("", valCurrnt.toString())
            // 前後値の差の絶対値と前のフレームの特定%を比較して大きかったら動体検知
            // matPrevious:前のフレームの画像処理後の値
            //
            //

            if((Math.abs(valCurrnt!! - valPrev!!) > valCurrnt!! * (0.5 + this@LapTimer.accurDetect )) && this@LapTimer.recFlag){
                if(!this@LapTimer.isRecNow){
                    // 計測開始
                    this@LapTimer.isRecNow = true
                    startTime = System.currentTimeMillis()
                    if(!this@LapTimer.recordTimeVal.isAlive) this@LapTimer.recordTimeVal.start()
                    if(!this@LapTimer.recordTimeDisp.isAlive) this@LapTimer.recordTimeDisp.start()
                    Log.d("", "計測開始")
                    Log.d("", "valCurrnt: " + valCurrnt.toString() + "\n" +
                            "valPrev: " + valPrev.toString() + "\n" +
                            "accurDetect: " + accurDetect.toString() + "\n" +
                            "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
                    )
                } else {
                    // 計測開始直後の指定時間は無視
                    if(this@LapTimer.lapTimeSec > 500){
                        if(this@LapTimer.recordTimeVal.isAlive) this@LapTimer.isRecNow = false
                        this@LapTimer.recFlag = false
                        this@LapTimer.recordTimeVal.interrupt()
                        this@LapTimer.recordTimeDisp.interrupt()
                        Log.d("", "計測終了")
                        Log.d("", "valCurrnt: " + valCurrnt.toString() + "\n" +
                                "valPrev: " + valPrev.toString() + "\n" +
                                "accurDetect: " + accurDetect.toString() + "\n" +
                                "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
                        )
                    }
                }
            }

            valPrev = valCurrnt
            matPrevious = matGray
            /* Close the image otherwise, this function is not called next time */
            image.close()
        }

        private fun getMatFromImage(image: ImageProxy): Mat {
            /* https://stackoverflow.com/questions/30510928/convert-android-camera2-api-yuv-420-888-to-rgb */
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer[nv21, 0, ySize]
            vBuffer[nv21, ySize, vSize]
            uBuffer[nv21, ySize + vSize, uSize]
            val yuv: Mat = Mat(image.height + image.height / 2, image.width, CvType.CV_8UC1)
            yuv.put(0, 0, nv21)
            val mat: Mat = Mat()
            Imgproc.cvtColor(yuv, mat, Imgproc.COLOR_YUV2RGB_NV21, 3)
            return mat
        }

    }





























    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ここからカメラ周り~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LapTimer.REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                //startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startCamera() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        //Toast.makeText(this, "カメラ起動", Toast.LENGTH_LONG).show()
        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }
            imgAnalyzer.setAnalyzer(cameraExecutor, MyImageAnalyzer())
            //val imageAnalysis = ImageAnalysis.Builder().build()
            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imgAnalyzer)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        if(this.recordTimeVal.isAlive) this.recordTimeVal.interrupt()
        if(this.recordTimeDisp.isAlive) this.recordTimeDisp.interrupt()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA//,
//                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
}

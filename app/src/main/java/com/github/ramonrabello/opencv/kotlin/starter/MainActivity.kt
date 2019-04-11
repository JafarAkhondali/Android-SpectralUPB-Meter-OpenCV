package com.github.ramonrabello.opencv.kotlin.starter

import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.Toast
import com.github.florent37.runtimepermission.kotlin.askPermission
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.Core.REDUCE_AVG
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.PrintWriter
import java.util.*


class MainActivity() : AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2, View.OnTouchListener {

    protected var cameraPreview: CameraBridgeViewBase? = null
    protected lateinit var mRgba: Mat
    protected lateinit var mRgbaT: Mat
    protected lateinit var mRgbaF: Mat
    protected var wSize: Int = 0
    protected var hSize: Int = 0
    protected lateinit var graph: GraphView
    //    protected lateinit var linechart: LineChart
    protected var series:LineGraphSeries<DataPoint>? = null
    protected lateinit var values: Array<DataPoint?>
    protected lateinit var lineChartValues: ArrayList<Entry>
//    protected lateinit var dataset: LineDataSet
    //    dataset.setColor(Color.CYAN);
//    protected lateinit var lineData:LineData

    protected lateinit var csvHeader: String
    protected var graphIsRendering = false
    protected var isRecording = false
    protected var permissionsAreGranted = false




    var rightNow = Calendar.getInstance()
    // offset to add since we're not UTC
    val offsetForTimeZone = rightNow.get(Calendar.ZONE_OFFSET) + rightNow.get(Calendar.DST_OFFSET)

    protected var exportCsvBuilder = StringBuilder("")


    fun Context.toast(message: CharSequence, longShow: Boolean=false) =
            Toast.makeText(this, message, if(longShow) Toast.LENGTH_LONG else Toast.LENGTH_SHORT ).show()


    fun saveCsvData(filename: String, csvPreData:StringBuilder): Pair<Boolean,String>{

        val sd_main = File(Environment.getExternalStorageDirectory().path+"/intensity_csv_data/")
        var success = true
        if (!sd_main.exists()) {
            success = sd_main.mkdir()
        }
        if (success) {
            val file_name_compelte = "$filename.csv"
            val dest = File(sd_main, file_name_compelte)
            try {
                PrintWriter(dest).use { out -> out.print(csvPreData.toString())}
                return Pair(true, "Files saved in ${dest.absolutePath}")
            } catch (e: Exception) {
                return Pair(false, "Error in writing files ${e.message}")
            }
        } else {
            return Pair(false, "Failed to create directory")
        }
    }






    protected var mLoaderCallback: BaseLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    cameraPreview!!.enableView()
                }
                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraPreview = findViewById<CameraBridgeViewBase>(R.id.sample_test_camera_view)
        cameraPreview!!.disableView()

        val record_btn = findViewById<Button>(R.id.btn_record)
        record_btn.setOnClickListener {
            if(isRecording) {
                //Save data from recording
                val saveResult = saveCsvData( Calendar.getInstance().time.toString() , exportCsvBuilder )

                if(saveResult.first){
                    toast(saveResult.second, true)
                }else{
                    toast("Saving csv failed! ${saveResult.second}")
                }

                record_btn.text = "* Start Recording *"
                exportCsvBuilder.clear()
            }else{
                //Start recording
                exportCsvBuilder = java.lang.StringBuilder(csvHeader)
                record_btn.text = "* Stop Recording *"
                toast("Recording started")
            }

            isRecording=!isRecording //Toggle recording flag

        }

        graph = findViewById<GraphView>(R.id.graph)
//        linechart = findViewById<LineChart>(R.id.chart)
//        linechart.legend.isEnabled = false
        askPermission(){
            if(it.isAccepted){
//                cameraPreview!!.enableView()
                permissionsAreGranted=true

                cameraPreview!!.setMaxFrameSize(10000,10000)
                cameraPreview!!.minimumHeight = 2000
                cameraPreview!!.setCvCameraViewListener(this)
                cameraPreview!!.visibility = SurfaceView.VISIBLE
                try{
                    OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback)
                }catch (e:java.lang.Exception){
                    toast(e.message.toString())
                }

            }
//            cameraPreview!!.rotation= 180F
        }.onDeclined { e ->
            //at least one permission have been declined by the user
        }


    }

    override fun onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy()
    }


    override fun onPause() {
        // TODO Auto-generated method stub
        super.onPause()
        if (cameraPreview != null) {
            cameraPreview!!.disableView()
        }
    }

    override fun onResume() {
        super.onResume()
        if(permissionsAreGranted)
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback)
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
//        mRgba  = Mat(height, width, CvType.CV_8UC4)
//        cameraPreview!!.rotation= 180F
        hSize = height
        wSize = width
        mRgba = Mat(height, width, CvType.CV_8UC4)
        mRgbaF = Mat(height, width, CvType.CV_8UC4)
        mRgbaT = Mat(width, width, CvType.CV_8UC4)
//        series = LineGraphSeries<DataPoint>(arrayOf<DataPoint>(DataPoint(0.0, 1.0), DataPoint(1.0, 5.0), DataPoint(2.0, 3.0), DataPoint(3.0, 2.0), DataPoint(4.0, 6.0)))
        values = arrayOfNulls<DataPoint>(wSize)

        graphIsRendering = true
        lineChartValues = ArrayList<Entry>(wSize)

        csvHeader="timestamp_milliseconds"

        for(i in 0 until wSize) {
//            lineChartValues.add(Entry(i.toFloat(), 255.0F))
            values[i] = DataPoint(i.toDouble(), 0.0)
            csvHeader.plus(',')
            csvHeader.plus(i)
        }
        exportCsvBuilder.append("\r\n")
        series = LineGraphSeries<DataPoint>(values)
        graph.addSeries(series)
//        linechart.setVisibleXRangeMaximum(wSize.toFloat())
//        dataset= LineDataSet(lineChartValues,"Intensity")
//        dataset.color = Color.CYAN
//        lineData = LineData(dataset)
//        linechart.data = lineData
//        linechart.invalidate()
        graphIsRendering = false
//        linechart.setVisibleYRangeMaximum(256.0f,YAxis.AxisDependency.LEFT);
        graph.viewport.isXAxisBoundsManual = true
        graph.viewport.isYAxisBoundsManual = true
        graph.viewport.setMinX(0.0);
        graph.viewport.setMaxX(wSize.toDouble());
        graph.viewport.setMinY(0.0 );
        graph.viewport.setMaxY(255.0 );

    }

    override fun onCameraViewStopped() {
        super.onStop()
        if(::mRgba.isInitialized && !mRgba.empty())
            mRgba.release()
    }

    private val MAINACTIVITY_TAG: String = "MAINACTIVITY_TAG"

    override fun onCameraFrame(inputFrame: CvCameraViewFrame): Mat? {
        // TODO Auto-generated method stub
//        mRgba = inputFrame.rgba()//.t() //.gray()

        // TODO Auto-generated method stub
        mRgba = inputFrame.rgba()

//        mRgbaT = inputFrame.gray()

        //         Rotate mRgba 90 degrees
        Core.transpose(mRgba, mRgbaT);
        Imgproc.resize(mRgbaT, mRgbaF, mRgbaF.size(), 0.0, 0.0, 0);
        Core.flip(mRgbaF, mRgba, 1 );



        if(mRgba != null){
            try {
//            Thread(Runnable {
//            val mRGB = Mat(wSize, hSize, CvType.CV_8UC3)
                val mGrayScale = Mat(wSize, hSize, CvType.CV_8UC1)
//            Imgproc.cvtColor(mRgba, mRGB, Imgproc.COLOR_RGBA2RGB, 3) //3 is HSV Channel
//            Imgproc.cvtColor(mRGB, mGrayScale, Imgproc.COLOR_RGB2GRAY, 1) //3 is HSV Channel
                Imgproc.cvtColor(mRgba,mGrayScale, Imgproc.COLOR_RGBA2GRAY, 1) //3 is HSV Channel
                var avg = Mat(hSize,0, CvType.CV_8UC1)
                Core.reduce(mGrayScale, avg,0, REDUCE_AVG)
//            val mGrayScale = Mat(wSize, hSize, CvType.CV_8UC3)
//            Imgproc.cvtColor(mRgba, mGrayScale, Imgproc.COLOR_RGBA2RGB, 3) //3 is HSV Channel
//            Imgproc.cvtColor(mGrayScale, mGrayScale, Imgproc.COLOR_RGB2HLS, 3) //3 is HSV Channel
//            var avg = Mat(1, wSize, CvType.CV_8UC1)
//            Core.reduce(mGrayScale, avg,1, REDUCE_AVG)

//            var newSeries:LineGraphSeries<DataPoint> = LineGraphSeries<DataPoint>()
//            var newSeries:Array

//                Log.e(MAINACTIVITY_TAG ,"Checking")
                if(!graphIsRendering) {
                    exportCsvBuilder.append(System.currentTimeMillis()+offsetForTimeZone)
                    Thread(Runnable {
                        //                        Log.e(MAINACTIVITY_TAG ,"Locking")
//                        try {


                        graphIsRendering=true
                        //todo: add anotehr chart https://github.com/PhilJay/MPAndroidChart/wiki/Dynamic-&-Realtime-Data
//                            lineChartValues.clear();

                        for(i in 0 until wSize) {

//                        for(i in 0 until wSize) {

//                                lineChartValues.add(Entry(i.toFloat(), avg.get(0,i).first().toFloat()))
//                            values[i] = DataPoint(i.toDouble(), 0.0)
//                        }
                            val intensityInColumn = avg.get(0, i).first()
                            values[i] = DataPoint(i.toDouble(), intensityInColumn )
                            if(isRecording) {
                                exportCsvBuilder.append(',')
                                exportCsvBuilder.append(intensityInColumn)
                            }
                        }
//                            Log.e(MAINACTIVITY_TAG , lineChartValues.size.toString())
//                            dataset.notifyDataSetChanged()
//                            linechart.notifyDataSetChanged()
//                            linechart.invalidate()

                        runOnUiThread {
                            series?.resetData(values)
                        }
                        exportCsvBuilder.append("\r\n")

//                        }catch (ee:java.lang.Exception){
//                            Log.e(MAINACTIVITY_TAG , "E1: " + ee.message)
//                        }finally {
                        graphIsRendering = false
//                            Log.e(MAINACTIVITY_TAG ,"UNLOCKING")
//                        }
//            graph.removeAllSeries()
//            graph.addSeries(series)

//                series?.appendData(series, true, wSize)
                    }).start()
                }
            }catch (e:Exception){
                Log.e(MAINACTIVITY_TAG , "E2: " + e.message)
            }




//                Log.e("------avg------", avg[0,0].toString());
//                for(i in 0 until wSize-1){
//                    var avg:Double = 0.0
//                    for(j in 0 until hSize-1){
//
//                        avg += mRgba.get(j,i)[0]
//                    }
//                }
//            }).start()

        }

//        Core.rotate(mRgba, mRgba, ROTATE_90_CLOCKWISE)
//        // Rotate mRgba 90 degrees
//        val dst = Mat(mRgba.cols(), mRgba.rows(), CvType.CV_8UC4)
////        Core.transpose(mRgba, mRgbaT);
////        Imgproc.resize(mRgbaT, mRgbaF, mRgbaF.size(), 0.0, 0.0, 0);
//        Core.flip(mRgba, dst , 1);
//        mRgbaT = mRgbaT.t()


        return mRgba
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        // TODO Auto-generated method stub
        return false
    }
//    private val imageBitmap by lazy { (ContextCompat.getDrawable(this, R.drawable.lena) as BitmapDrawable).bitmap }
//
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//        setSupportActionBar(toolbar)
//    }
//
//    private fun applyGrayScale() {
//        val mat = Mat()
//        mat.toGray(imageBitmap)
//        image.setImageBitmap(mat.toBitmap())
//        mat.release()
//    }
//
//    private fun applyGaussianBlur(){
//        val mat = Mat()
//        mat.gaussianBlur(imageBitmap) { image.setImageBitmap(it) }
//        mat.release()
//    }
//
//    /**
//     * Apply the Canny Edge Algorithm to detect edges of an image.
//     */
//    private fun applyCannyEdge() {
//        val mat = Mat()
//        mat.canny(imageBitmap) { image.setImageBitmap(it) }
//    }
//
//    /**
//     * Apply the Threshold Algorithm.
//     */
//    private fun applyThreshold() {
//        val mat = Mat()
//        mat.threshold(imageBitmap) { image.setImageBitmap(it) }
//    }
//
//    private fun applyAdaptiveThreshold() {
//        val mat = Mat()
//        mat.adaptiveThreshold(imageBitmap) { image.setImageBitmap(it) }
//    }
//
//    private fun resetImage() {
//        image.setImageBitmap(imageBitmap)
//    }
//
//    override fun onCreateOptionsMenu(menu: Menu): Boolean {
//        // Inflate the menu; this adds items to the action bar if it is present.
//        menuInflater.inflate(R.menu.menu_main, menu)
//        return true
//    }
//
//    override fun onOptionsItemSelected(item: MenuItem): Boolean =// Handle action bar item clicks here. The action bar will
//    // automatically handle clicks on the Home/Up button, so long
//    // as you specify a parent activity in AndroidManifest.xml.
//            when (item.itemId) {
//                R.id.action_gray_scale -> {
//                    applyGrayScale()
//                    true
//                }
//                R.id.action_gaussian_blur -> {
//                    applyGaussianBlur()
//                    true
//                }
//                R.id.action_canny -> {
//                    applyCannyEdge()
//                    true
//                }
//                R.id.action_threshold -> {
//                    applyThreshold()
//                    true
//                }
//                R.id.action_adaptive_threshold -> {
//                    applyAdaptiveThreshold()
//                    true
//                }
//                R.id.action_reset -> {
//                    resetImage()
//                    true
//                }
//                else -> super.onOptionsItemSelected(item)
//            }
//
//    override fun writeToParcel(parcel: Parcel, flags: Int) {
//
//    }
//
//    override fun describeContents(): Int {
//        return 0
//    }
//
//    companion object CREATOR : Parcelable.Creator<MainActivity> {
//        override fun createFromParcel(parcel: Parcel): MainActivity {
//            return MainActivity(parcel)
//        }
//
//        override fun newArray(size: Int): Array<MainActivity?> {
//            return arrayOfNulls(size)
//        }
//    }

}

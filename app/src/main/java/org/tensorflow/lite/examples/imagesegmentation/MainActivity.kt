/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.imagesegmentation

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.Intent
import android.graphics.*
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.provider.MediaStore
import android.renderscript.ScriptGroup
import android.util.Log
import android.util.TypedValue
import android.view.animation.AnimationUtils
import android.view.animation.BounceInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.android.synthetic.main.tfe_is_activity_main.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import org.tensorflow.lite.examples.imagesegmentation.camera.CameraFragment
import org.tensorflow.lite.examples.imagesegmentation.tflite.ImageSegmentationModelExecutor
import org.tensorflow.lite.examples.imagesegmentation.tflite.ModelExecutionResult
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.HashMap

// This is an arbitrary number we are using to keep tab of the permission
// request. Where an app has multiple context for requesting permission,
// this can help differentiate the different contexts
private const val REQUEST_CODE_PERMISSIONS = 10

// This is an array of all the permission specified in the manifest
private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {

    //private val INPUT = "input_image.jpg"
    private lateinit var backgroundBitmap: Bitmap
    private lateinit var cameraFragment: CameraFragment
    private lateinit var viewModel: MLExecutionViewModel
    private lateinit var viewFinder: FrameLayout
    private lateinit var resultImageView: ImageView
    private lateinit var originalImageView: ImageView
    private lateinit var maskImageView: ImageView
    private lateinit var chipsGroup: ChipGroup
    private lateinit var rerunButton: Button
    private lateinit var captureButton1: ImageButton
    private lateinit var captureButton2: ImageButton
    private lateinit var galleryButton1: FloatingActionButton
    private lateinit var galleryButton2: FloatingActionButton
    private lateinit var swapButton: Button
    lateinit var  currentPhotoPath: String

    val REQEST_TAKE_PHOTO = 1

    private var lastSavedFile = ""
    private var useGPU = false
    private var imageSegmentationModel: ImageSegmentationModelExecutor? = null
    private val inferenceThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val mainScope = MainScope()
    private val pickImage = 100

    private var lensFacing = CameraCharacteristics.LENS_FACING_FRONT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tfe_is_activity_main)

        originalImageView = findViewById(R.id.original_imageview)
        maskImageView = findViewById(R.id.mask_imageview)
        chipsGroup = findViewById(R.id.chips_group)
        captureButton1 = findViewById(R.id.capture_button1)
        captureButton2 = findViewById(R.id.capture_button2)
        galleryButton1 = findViewById(R.id.gallery_button1)
        galleryButton2 = findViewById(R.id.gallery_button2)
        swapButton = findViewById(R.id.swap_button)

        galleryButton1.setOnClickListener{
            val gallery = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
            startActivityForResult(gallery, pickImage)
        }

        galleryButton2.setOnClickListener{
            val gallery = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
            startActivityForResult(gallery, pickImage+1)
        }

        captureButton1.setOnClickListener{
            takePicture(1)
            galleryAddPic()
        }

        captureButton2.setOnClickListener{
            takePicture(2)
        }

        swapButton.setOnClickListener{
            applyModel()
        }


        val useGpuSwitch: Switch = findViewById(R.id.switch_use_gpu)

        // Request camera permission

        if (allPermissionsGranted()) {
            //addCameraFragment()
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    REQUIRED_PERMISSIONS,
                    REQUEST_CODE_PERMISSIONS
            )
        }

        viewModel = AndroidViewModelFactory(application).create(MLExecutionViewModel::class.java)
        viewModel.resultingBitmap.observe(
                this,
                Observer { resultImage ->
                    if (resultImage != null) {
                        updateUIWithResults(resultImage)
                    }
                    enableControls(true)
                }
        )

        createModelExecutor(useGPU)

        useGpuSwitch.setOnCheckedChangeListener { _, isChecked ->
            useGPU = isChecked
            mainScope.async(inferenceThread) {
                createModelExecutor(useGPU)
            }
        }

        rerunButton = findViewById(R.id.rerun_button)
        rerunButton.setOnClickListener {
            if (lastSavedFile.isNotEmpty()) {
                enableControls(false)
                viewModel.onApplyModel(lastSavedFile, imageSegmentationModel, inferenceThread)
            }
        }

        setChipsToLogView(HashMap<String, Int>() )
        enableControls(true)

        //backgroundBitmap = loadImage(INPUT)!!

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == pickImage) {
            if (data != null) {
                lastSavedFile = data.data.toString()
            }
            originalImageView.setImageURI(data?.data)
        }
        if(requestCode==101 && resultCode == RESULT_OK) {

            val contentUri: Uri? =  data?.data
            val cr:ContentResolver = contentResolver
            val input: InputStream? = cr.openInputStream(contentUri!!)
            backgroundBitmap = BitmapFactory.decodeStream(input)

            maskImageView.setImageURI(data.data)

        }
        if (requestCode == REQEST_TAKE_PHOTO && resultCode == RESULT_OK) {
            //picture.rotation = 90f
            //val imageBitmap = data?.extras?.get("data") as Bitmap
            originalImageView.setImageURI(Uri.parse(currentPhotoPath))
        }
        if (requestCode == REQEST_TAKE_PHOTO+1 && resultCode == RESULT_OK) {

            val imageUri: Uri? = Uri.parse("file://$currentPhotoPath")
            backgroundBitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, imageUri)
            maskImageView.setImageBitmap(backgroundBitmap)

        }
    }

    private fun takePicture(code : Int) {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            // Ensure that there's a camera activity to handle the intent
            takePictureIntent.resolveActivity(packageManager)?.also {
                // Create the File where the photo should go
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    // Error occurred while creating the File
                    null
                }
                // Continue only if the File was successfully created
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "com.example.android.fileprovider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    if(code == 1) {
                        lastSavedFile = photoFile.absolutePath
                        startActivityForResult(takePictureIntent, REQEST_TAKE_PHOTO)
                    }
                    if(code == 2) {
                        startActivityForResult(takePictureIntent, REQEST_TAKE_PHOTO+1)
                    }

                }
            }
        }
        /*
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        if(intent.resolveActivity(packageManager) != null) {
            var photoFile: File? = null
            try{
                photoFile = createImageFile()
            }catch (e: IOException){}
            if(photoFile != null) {
                val photoUri = FileProvider.getUriForFile(
                    this,
                    "com.example.android.fileprovider",
                    photoFile
                )
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)

                if(code == 1) {
                    //lastSavedFile = photoFile.absolutePath
                    startActivityForResult(intent, REQEST_TAKE_PHOTO)
                }
                if(code == 2) {
                    startActivityForResult(intent, REQEST_TAKE_PHOTO+1)
                }

            }
        }

         */
    }

    private fun galleryAddPic() {
        Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).also { mediaScanIntent ->
            val f = File(currentPhotoPath)
            mediaScanIntent.data = Uri.fromFile(f)
            sendBroadcast(mediaScanIntent)
        }
    }

    @SuppressLint("SimpleDateFormat")
    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }

    private fun createModelExecutor(useGPU: Boolean) {
        if (imageSegmentationModel != null) {
            imageSegmentationModel!!.close()
            imageSegmentationModel = null
        }
        try {
            imageSegmentationModel = ImageSegmentationModelExecutor(this, useGPU)
        } catch (e: Exception) {
            Log.e(TAG, "Fail to create ImageSegmentationModelExecutor: ${e.message}")
            val logText: TextView = findViewById(R.id.log_view)
            logText.text = e.message
        }
    }

    private fun setChipsToLogView(itemsFound: Map<String, Int>) {
        chipsGroup.removeAllViews()

        val paddingDp = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 10F,
                resources.displayMetrics
        ).toInt()

        for ((label, color) in itemsFound) {
            val chip = Chip(this)
            chip.text = label
            chip.chipBackgroundColor = getColorStateListForChip(color)
            chip.isClickable = false
            chip.setPadding(0, paddingDp, 0, paddingDp)
            chipsGroup.addView(chip)
        }
        val labelsFoundTextView: TextView = findViewById(R.id.tfe_is_labels_found)
        if (chipsGroup.childCount == 0) {
            labelsFoundTextView.text = getString(R.string.tfe_is_no_labels_found)
        } else {
            labelsFoundTextView.text = getString(R.string.tfe_is_labels_found)
        }
        chipsGroup.parent.requestLayout()
    }

    private fun getColorStateListForChip(color: Int): ColorStateList {
        val states = arrayOf(
                intArrayOf(android.R.attr.state_enabled), // enabled
                intArrayOf(android.R.attr.state_pressed) // pressed
        )

        val colors = intArrayOf(color, color)
        return ColorStateList(states, colors)
    }

    private fun setImageView(imageView: ImageView, image: Bitmap) {
        Glide.with(baseContext)
                .load(image)
                .override(512, 512)
                .fitCenter()
                .into(imageView)
    }

    private fun updateUIWithResults(modelExecutionResult: ModelExecutionResult) {
        val resultBitmap = maskBitmap(modelExecutionResult.bitmapOriginal,
                modelExecutionResult.bitmapMaskOnly,
                PorterDuff.Mode.DST_IN)
        val resultOnBackground = maskBitmap(backgroundBitmap,
                resultBitmap,
                PorterDuff.Mode.SRC_OVER)

        originalImageView.setImageBitmap(resultOnBackground)
        val intent = Intent(this, ViewResult::class.java)

        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }

        File(storageDir, timeStamp).writeBitmap(resultOnBackground, Bitmap.CompressFormat.PNG, 85)

        intent.putExtra("PATH", currentPhotoPath)
        if(resultOnBackground.width != 0) {
           // startActivity(intent)
        }
        //setImageView(resultImageView, resultOnBackground)
        //setImageView(originalImageView, modelExecutionResult.bitmapOriginal)
        //setImageView(maskImageView, resultOnBackground)
        val logText: TextView = findViewById(R.id.log_view)
        logText.text = modelExecutionResult.executionLog

        setChipsToLogView(modelExecutionResult.itemsFound)
        enableControls(true)
    }

    private fun File.writeBitmap(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int) {
        outputStream().use { out ->
            bitmap.compress(format, quality, out)
            out.flush()
        }
    }

    private fun enableControls(enable: Boolean) {
        rerunButton.isEnabled = enable && lastSavedFile.isNotEmpty()
        captureButton1.isEnabled = enable
    }

    /**
     * Process result from permission request dialog box, has the request
     * been granted? If yes, start Camera. Otherwise display a toast
     */
    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                Toast.makeText(
                        this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }
/*
    private fun addCameraFragment() {
        cameraFragment = CameraFragment.newInstance()
        cameraFragment.setFacingCamera(lensFacing)
        supportFragmentManager.popBackStack()
        supportFragmentManager.beginTransaction()
                .replace(R.id.mask_imageview, cameraFragment)
                .commit()
    }
*/
    /**
     * Check if all permission specified in the manifest have been granted
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        checkPermission(
                it, Process.myPid(), Process.myUid()
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun applyModel() {
        //val msg = "Photo capture succeeded: ${file.absolutePath}"

        enableControls(false)
        viewModel.onApplyModel(lastSavedFile, imageSegmentationModel, inferenceThread)
    }

    fun maskBitmap(bitmap: Bitmap, mask: Bitmap, mode: PorterDuff.Mode): Bitmap {
        val resultBitmap = Bitmap.createBitmap(
                mask.width, mask.height, Bitmap.Config.ARGB_8888
        )

        // paint to mask
        val paint = Paint().apply {
            isAntiAlias = true
            xfermode = PorterDuffXfermode(mode)
        }

        Canvas(resultBitmap).apply {
            // draw source bitmap on canvas
            drawBitmap(bitmap, 0f, 0f, null)
            // mask bitmap
            drawBitmap(mask, 0f, 0f, paint)
        }

        return resultBitmap
    }

    private fun convertToAlphaMask(mask: Bitmap): Bitmap {
        val alphaMask = Bitmap.createBitmap(mask.width, mask.height, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(alphaMask)
        canvas.drawBitmap(mask, 0.0f, 0.0f, null)
        return alphaMask
    }

    private fun loadImage(fileName: String): Bitmap? {
        val inputStream: InputStream? = assets.open(fileName)
        return BitmapFactory.decodeStream(inputStream)
    }


}

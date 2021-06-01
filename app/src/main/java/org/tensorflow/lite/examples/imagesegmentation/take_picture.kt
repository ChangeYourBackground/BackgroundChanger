package org.tensorflow.lite.examples.imagesegmentation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.android.synthetic.main.activity_take_picture.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class take_picture : AppCompatActivity() {

    lateinit var  photoPath: String

    val REQEST_TAKE_PHOTO = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_take_picture)

        take_picture_button.setOnClickListener{

            takePicture()
        }
    }

    private fun takePicture() {
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
                startActivityForResult(intent, REQEST_TAKE_PHOTO)
            }
        }
    }

    private fun createImageFile(): File? {
        val fileName = "MyPicture"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val image = File.createTempFile(
                fileName,
                ".jpg",
                storageDir
        )

        photoPath = image.absolutePath

        return  image
    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            startActivityForResult(takePictureIntent, 1)
        } catch (e: ActivityNotFoundException) {
            // display error state to the user
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQEST_TAKE_PHOTO && resultCode == RESULT_OK) {
            picture.rotation = 90f
            picture.setImageURI(Uri.parse(photoPath))
        }
    }
}
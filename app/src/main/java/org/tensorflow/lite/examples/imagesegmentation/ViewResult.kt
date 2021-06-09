package org.tensorflow.lite.examples.imagesegmentation

import android.annotation.SuppressLint
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import com.bumptech.glide.Glide
import org.tensorflow.lite.examples.imagesegmentation.R.id.resultView
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class ViewResult : AppCompatActivity() {
    @SuppressLint("WrongViewCast", "CutPasteId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_result2)

        intent.getByteArrayExtra("bitmap")
        val path:String = intent.getStringExtra("PATH")
        val view: ImageView = findViewById(R.id.resultView)

        val imageUri: Uri? = Uri.parse("file://$path")
        val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, imageUri)
        /*
        Glide.with(baseContext)
            .load(bitmap)
            .override(512, 512)
            .fitCenter()
            .into(view)
         */

        val button: Button = findViewById(R.id.saveButton)

        button.setOnClickListener{
            val filename = "${System.currentTimeMillis()}.jpg"
            var fos: OutputStream? = null

            val imagesDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val image = File(imagesDir, filename)
            fos = FileOutputStream(image)


            fos.use {
                //Finally writing the bitmap to the output stream that we opened
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)

            }
        }

    }

    private fun setImageView(imageView: ImageView, image: Bitmap) {
        Glide.with(baseContext)
            .load(image)
            .override(512, 512)
            .fitCenter()
            .into(imageView)
    }
}
package com.bluepark.mnist

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bluepark.mnist.ui.theme.MNISTTheme
import io.ak1.drawbox.DrawBox
import io.ak1.drawbox.rememberDrawController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val TAG = "Log"

class MainActivity : ComponentActivity() {

    private val classifier = Classifier(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        classifier.initialize().addOnFailureListener { e ->
            Log.d(TAG, "initialize failure = ${e.message}")
        }

        enableEdgeToEdge()
        setContent {
            MNISTTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(
                        modifier = Modifier.padding(innerPadding),
                        classifier = classifier,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        classifier.close()
        super.onDestroy()
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier, classifier: Classifier) {

    var result by rememberSaveable { mutableStateOf("RESULT") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 30.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        val controller = rememberDrawController()

        controller.changeColor(Color.White)
        controller.changeStrokeWidth(40f)

        DrawBox(
            drawController = controller,
            backgroundColor = Color.Black,
            modifier = Modifier
                .width(300.dp)
                .height(300.dp),
            bitmapCallback = { imageBitmap, error ->
                Log.d(TAG, "imageBitmap = $imageBitmap")
                Log.d(TAG, "error = $error")

                imageBitmap?.let {
                    if (classifier.isInitialized) {
                        val bitmap = it.asAndroidBitmap()
                        classifier.classifyTask(bitmap).addOnSuccessListener { data ->
                            result = data
                        }.addOnFailureListener { e ->
                            result = e.message.toString()
                        }
                        Log.d(TAG, "result = $result")
                    }
                }
            })

        Spacer(modifier = Modifier.height(10.dp))

        Text(modifier = modifier, text = result)

        Spacer(modifier = Modifier.height(30.dp))

        Row(
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = {
                    Log.d(TAG, "onClick")
                    controller.saveBitmap()
                },
                modifier = modifier
                    .width(100.dp)
                    .height(60.dp)
            ) {
                Text("예측")
            }
            Spacer(modifier = modifier.width(10.dp))
            Button(
                onClick = {
                    controller.reset()
                },
                modifier = modifier
                    .width(100.dp)
                    .height(60.dp)
            ) {
                Text("지우기")
            }
        }
    }


}

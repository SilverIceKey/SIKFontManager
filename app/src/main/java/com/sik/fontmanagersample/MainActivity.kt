package com.sik.fontmanagersample

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sik.fontmanager.ProvideFontManager
import com.sik.fontmanagersample.ui.theme.SIKFontManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SIKFontManagerTheme {
                ProvideFontManager {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        Greeting(
                            name = "派博自助机",
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Column(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Hello $name!",
            color = Color.Black,
            modifier = modifier
        )
        Spacer(modifier.height(10.dp))
        AndroidView(
            modifier = Modifier
                .weight(1f)
                .padding(start = 25.dp)     // layout_marginLeft="15dp"
                .fillMaxHeight(), factory = { context ->
                TextClock(context).apply {
                    // 对齐原 XML 属性
                    format12Hour = "yyyy-MM-dd HH:mm"
                    format24Hour = "yyyy-MM-dd HH:mm"
                    timeZone = "GMT+0800"
                    setTextColor(Color.Black.toArgb())
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, 22f)
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                }
            })
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SIKFontManagerTheme {
        Greeting("Android")
    }
}
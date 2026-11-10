package com.notaristo.docimagesaver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val activityMessages = mutableStateListOf<String>()
    private var savedCount by mutableIntStateOf(0)
    private var failedCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF090B16)) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color(0xFF17152E), Color(0xFF0B0D19), Color(0xFF090B16))))
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Spacer(Modifier.height(12.dp))
                        Text("DOC IMAGE SAVER", color = Color(0xFFB8B8D8), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp)
                        Text("Every document.\nOne safe place.", color = Color.White, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Share an image from WhatsApp or any other app to save it directly into your gallery.",
                            color = Color(0xFFB9BCD0), fontSize = 15.sp, lineHeight = 23.sp
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(26.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF24233D))
                        ) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("YOUR GALLERY FOLDER", color = Color(0xFFB8B8D8), fontSize = 11.sp, letterSpacing = 1.5.sp)
                                Text("Pictures / Document Images", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                                Text("Files are copied without resizing, recompression or format conversion.", color = Color(0xFFB9BCD0), fontSize = 13.sp, lineHeight = 19.sp)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            CounterCard("SAVED", savedCount.toString(), Modifier.weight(1f))
                            CounterCard("FAILED", failedCount.toString(), Modifier.weight(1f))
                        }
                        Text("RECENT ACTIVITY", color = Color(0xFFB8B8D8), fontSize = 11.sp, letterSpacing = 1.5.sp)
                        if (activityMessages.isEmpty()) {
                            Text("Nothing saved yet. Open a chat, select an image, and tap Share → DocImageSaver.", color = Color(0xFF9296B0), fontSize = 14.sp, lineHeight = 21.sp)
                        } else {
                            activityMessages.take(5).forEach { message ->
                                Text(message, color = Color.White, fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text("ORIGINAL FILE BYTES PRESERVED", color = Color(0xFF8D8BAF), fontSize = 10.sp, letterSpacing = 1.7.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
        }
        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val incoming = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                }
            )
            Intent.ACTION_SEND_MULTIPLE -> {
                val streams = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                }
                streams.orEmpty()
            }
            else -> emptyList()
        }
        if (incoming.isEmpty()) return
        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) {
                val saver = ImageSaver(applicationContext)
                incoming.distinct().map { saver.save(it) }
            }
            results.forEach { result ->
                if (result.success) savedCount++ else failedCount++
                activityMessages.add(0, (if (result.success) "✓ " else "✕ ") + result.fileName +
                    if (result.success) "" else ": " + result.message)
            }
            while (activityMessages.size > 12) activityMessages.removeAt(activityMessages.lastIndex)
        }
    }
}

@androidx.compose.runtime.Composable
private fun CounterCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF191A2C))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(value, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
            Text(label, color = Color(0xFF9C9FB9), fontSize = 10.sp, letterSpacing = 1.5.sp)
        }
    }
}

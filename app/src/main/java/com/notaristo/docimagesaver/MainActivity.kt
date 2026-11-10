package com.notaristo.docimagesaver

import android.content.ClipData
import android.content.Intent
import android.graphics.RuntimeShader
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF080A17)
private val Muted = Color(0xFFAAAFC9)
private val Cyan = Color(0xFF91E9FF)
private val Violet = Color(0xFFB7A0FF)

class MainActivity : ComponentActivity() {
    private val activityMessages = mutableStateListOf<String>()
    private var savedCount by mutableIntStateOf(0)
    private var failedCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedCount = getSharedPreferences("doc_image_saver", MODE_PRIVATE).getInt("saved_count", 0)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
                    DocImageHome(savedCount, failedCount, activityMessages)
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
        val streams = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                }
            )
            Intent.ACTION_SEND_MULTIPLE -> {
                if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
                }
            }
            else -> emptyList()
        }
        val clipUris = buildList {
            val clip = intent.clipData
            if (clip != null) for (index in 0 until clip.itemCount) {
                clip.getItemAt(index).uri?.let(::add)
            }
        }
        val incoming = (streams + clipUris).distinct()
        if (incoming.isEmpty()) return
        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) {
                val saver = ImageSaver(applicationContext)
                incoming.map { saver.save(it) }
            }
            results.forEach { result ->
                if (result.success) {
                    savedCount++
                    getSharedPreferences("doc_image_saver", MODE_PRIVATE)
                        .edit().putInt("saved_count", savedCount).apply()
                } else {
                    failedCount++
                }
                activityMessages.add(0, (if (result.success) "Saved  ·  " else "Could not save  ·  ") +
                    result.fileName + if (result.success) "" else " — " + result.message)
            }
            while (activityMessages.size > 12) activityMessages.removeAt(activityMessages.lastIndex)
            results.firstOrNull { it.success && it.uri != null }?.uri?.let(::openSavedImage)
        }
    }

    private fun openSavedImage(uri: Uri) {
        val galleryIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(galleryIntent)
        } catch (_: android.content.ActivityNotFoundException) {
            // The image remains saved even if no viewer app is installed.
        }
    }
}

@Composable
private fun DocImageHome(savedCount: Int, failedCount: Int, activityMessages: List<String>) {
    val transition = rememberInfiniteTransition(label = "ambient")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowDrift"
    )
    val backdropShader = remember {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            RuntimeShader(
                """
                uniform shader content;
                uniform float2 resolution;
                uniform float uScale;
                uniform float uTime;
                float hash21(float2 p) {
                    p = fract(p * float2(123.34, 456.21));
                    p += dot(p, p + 45.32);
                    return fract(p.x * p.y);
                }
                float noise2(float2 p) {
                    float2 i = floor(p);
                    float2 f = fract(p);
                    f = f * f * (3.0 - 2.0 * f);
                    return mix(mix(hash21(i), hash21(i + float2(1.0, 0.0)), f.x),
                               mix(hash21(i + float2(0.0, 1.0)), hash21(i + float2(1.0, 1.0)), f.x), f.y);
                }
                half4 main(float2 coord) {
                    float2 uv = coord / resolution;
                    float2 p = uv * 5.0;
                    float n1 = noise2(p + float2(uTime * 0.12, 0.0));
                    float n2 = noise2(p * 1.8 + float2(4.7, -uTime * 0.10));
                    float2 warp = float2(n1 - 0.5, n2 - 0.5);
                    float2 offset = warp * (uScale - 1.0) * 4.0;
                    half4 base = content.eval(coord + offset);
                    half4 soft1 = content.eval(coord + offset + float2(1.2, 0.0));
                    half4 soft2 = content.eval(coord + offset + float2(-1.2, 0.0));
                    return base * 0.84 + (soft1 + soft2) * 0.08;
                }
                """.trimIndent()
            )
        } else null
    }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF17152F), Color(0xFF0D1021), Ink)))
            .graphicsLayer {
                if (android.os.Build.VERSION.SDK_INT >= 33 && backdropShader != null) {
                    backdropShader.setFloatUniform("resolution", size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
                    backdropShader.setFloatUniform("uScale", 1.4f)
                    backdropShader.setFloatUniform("uTime", drift / 24f * 6.28318f)
                    renderEffect = android.graphics.RenderEffect
                        .createRuntimeShaderEffect(backdropShader, "content")
                        .asComposeRenderEffect()
                }
            }
    ) {
        Box(
            Modifier.offset(x = 190.dp + drift.dp, y = (-95).dp)
                .size(270.dp)
                .background(Brush.radialGradient(listOf(Color(0x665B4BC2), Color.Transparent)), CircleShape)
        )
        Box(
            Modifier.offset(x = (-135).dp, y = 360.dp - drift.dp)
                .size(280.dp)
                .background(Brush.radialGradient(listOf(Color(0x3341B7D6), Color.Transparent)), CircleShape)
        )

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(21.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Box(
                    Modifier.size(42.dp).shadow(18.dp, CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFFB8A6FF), Color(0xFF65DDF3))), CircleShape)
                        .padding(1.dp).clip(CircleShape).background(Color(0xFF17172D)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("D", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("DOC IMAGE SAVER", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("YOUR PRIVATE IMAGE LIBRARY", color = Muted, fontSize = 9.sp, letterSpacing = 1.45.sp)
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(CircleShape).background(Color(0x222DE0C1))
                        .border(1.dp, Color(0x5549E5C7), CircleShape).padding(horizontal = 11.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(6.dp).background(Color(0xFF6CF0C8), CircleShape))
                        Text("READY", color = Color(0xFF9AF0D8), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("A calmer home\nfor every image.", color = Color.White, fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.1).sp)
                Text(
                    "Keep the images that matter. Share from WhatsApp or any app and let them settle into one beautifully organised place.",
                    color = Muted, fontSize = 14.sp, lineHeight = 22.sp
                )
            }

            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                accent = Color(0xFF9D8BFF)
            ) {
                Column(Modifier.padding(21.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("YOUR COLLECTION", color = Color(0xFFC5B9FF), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                            Text("Document Images", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp)
                            Text("Pictures  /  Document Images", color = Color(0xFFB9B8D1), fontSize = 11.sp)
                        }
                        Box(
                            Modifier.size(66.dp).background(
                                Brush.linearGradient(listOf(Color(0x557E6BDF), Color(0x222FD0E7))),
                                RoundedCornerShape(22.dp)
                            ).border(1.dp, Color(0x557F91E9), RoundedCornerShape(22.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("▧", color = Color(0xFFD8D0FF), fontSize = 34.sp)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0x55FFFFFF), Color.Transparent))))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(Modifier.size(25.dp).background(Color(0x332EDBC5), CircleShape), contentAlignment = Alignment.Center) {
                            Text("✓", color = Color(0xFF8CEBD7), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("Original file bytes. No resizing. No recompression.", color = Color(0xFFD3D5E7), fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatPanel("SAVED", savedCount.toString(), "In your gallery", Modifier.weight(1f), Color(0xFF7DE7D0))
                StatPanel("NEEDS ATTENTION", failedCount.toString(), "Unsuccessful", Modifier.weight(1f), Color(0xFFFFA8B8))
            }

            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("RECENT ACTIVITY", color = Color(0xFFC7C5E0), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                    Spacer(Modifier.weight(1f))
                    Text("LATEST FIRST", color = Color(0xFF777D9D), fontSize = 8.sp, letterSpacing = 1.2.sp)
                }
                GlassPanel(Modifier.fillMaxWidth(), RoundedCornerShape(23.dp), Color(0xFF75D8E8)) {
                    if (activityMessages.isEmpty()) {
                        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                            Box(Modifier.size(40.dp).background(Color(0x223FBDD5), CircleShape), contentAlignment = Alignment.Center) {
                                Text("✧", color = Cyan, fontSize = 22.sp)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Your story starts here", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text("Open a chat, choose an image, then Share → DocImageSaver.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                            }
                        }
                    } else {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            activityMessages.take(5).forEachIndexed { index, message ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                    Box(Modifier.size(7.dp).background(if (message.startsWith("Saved")) Color(0xFF6DE4C6) else Color(0xFFFF9AAE), CircleShape))
                                    Text(message, color = Color(0xFFE3E4F1), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                }
                                if (index != activityMessages.take(5).lastIndex) {
                                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x18FFFFFF)))
                                }
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("✦", color = Color(0xFF9D8BFF), fontSize = 11.sp)
                Spacer(Modifier.width(8.dp))
                Text("MADE TO KEEP EVERY DETAIL", color = Color(0xFF8589A8), fontSize = 9.sp, letterSpacing = 1.7.sp)
                Spacer(Modifier.width(8.dp))
                Text("✦", color = Color(0xFF77D9E9), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape,
    accent: Color,
    content: @Composable () -> Unit
) {
    val pressed = remember { mutableStateOf(false) }
    val warp by animateFloatAsState(
        targetValue = if (pressed.value) 1.4f else 1f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "glassDisplacement"
    )
    val shader = remember {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            RuntimeShader(
                """
                uniform shader content;
                uniform float2 resolution;
                uniform float uScale;
                float hash21(float2 p) {
                    p = fract(p * float2(123.34, 456.21));
                    p += dot(p, p + 45.32);
                    return fract(p.x * p.y);
                }
                float noise2(float2 p) {
                    float2 i = floor(p);
                    float2 f = fract(p);
                    f = f * f * (3.0 - 2.0 * f);
                    return mix(mix(hash21(i), hash21(i + float2(1.0, 0.0)), f.x),
                               mix(hash21(i + float2(0.0, 1.0)), hash21(i + float2(1.0, 1.0)), f.x), f.y);
                }
                half4 main(float2 coord) {
                    float2 uv = coord / resolution;
                    float n1 = noise2(uv * 18.0);
                    float n2 = noise2(uv * 37.0 + 8.4);
                    float2 field = float2(n1 - 0.5, n2 - 0.5);
                    float2 offset = field * (uScale - 1.0) * 3.2;
                    half4 center = content.eval(coord + offset);
                    half4 blurA = content.eval(coord + offset + float2(1.0, 0.0));
                    half4 blurB = content.eval(coord + offset + float2(-1.0, 0.0));
                    half4 blurC = content.eval(coord + offset + float2(0.0, 1.0));
                    half4 blurD = content.eval(coord + offset + float2(0.0, -1.0));
                    return center * 0.72 + (blurA + blurB + blurC + blurD) * 0.07;
                }
                """.trimIndent()
            )
        } else null
    }

    Box(
        modifier.shadow(22.dp, shape)
            .clip(shape)
            .border(BorderStroke(1.dp, Brush.linearGradient(listOf(Color(0x88FFFFFF), accent.copy(alpha = 0.38f), Color(0x20FFFFFF)))), shape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed.value = true
                        tryAwaitRelease()
                        pressed.value = false
                    }
                )
            }
    ) {
        Box(
            Modifier.matchParentSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0x35FFFFFF), Color(0x17A5A4FF), Color(0x0DFFFFFF), Color(0x222D426A))
                    )
                )
                .background(Brush.verticalGradient(listOf(Color(0x16FFFFFF), Color(0x080A1428), Color(0x1AFFFFFF))))
                .graphicsLayer {
                    if (android.os.Build.VERSION.SDK_INT >= 33 && shader != null) {
                        shader.setFloatUniform("resolution", size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
                        shader.setFloatUniform("uScale", warp)
                        renderEffect = android.graphics.RenderEffect
                            .createRuntimeShaderEffect(shader, "content")
                            .asComposeRenderEffect()
                    }
                }
        )
        Box {
            Box(
                Modifier.fillMaxWidth().height(1.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0x99FFFFFF), Color.Transparent)))
                    .align(Alignment.TopCenter)
            )
            content()
        }
    }
}

@Composable
private fun StatPanel(label: String, value: String, caption: String, modifier: Modifier, tint: Color) {
    GlassPanel(modifier, RoundedCornerShape(23.dp), tint) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 17.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, color = Color(0xFFB3B6D0), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, maxLines = 1)
            Text(value, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
            Text(caption, color = Color(0xFF8F95B3), fontSize = 10.sp)
        }
    }
}

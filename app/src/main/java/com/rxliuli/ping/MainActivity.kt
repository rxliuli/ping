package com.rxliuli.ping

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.ui.res.vectorResource

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rxliuli.ping.ui.theme.PingTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {
    companion object {
        const val PREFS_NAME = "PingToolPrefs"
        const val KEY_LAST_ADDRESS = "last_address"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PingTheme(
                darkTheme = true
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF1E1E1E)
                ) { innerPadding ->
                    PingTool(
                        modifier = Modifier.padding(innerPadding),
                        context = this
                    )
                }
            }
        }
    }
}

@Composable
fun PingTool(modifier: Modifier = Modifier, context: Context) {
    val focusManager = LocalFocusManager.current
    val prefs =
        remember { context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE) }
    var address by remember {
        mutableStateOf(
            prefs.getString(MainActivity.KEY_LAST_ADDRESS, "") ?: ""
        )
    }
    var pingResults by remember { mutableStateOf(listOf<String>()) }
    var isPinging by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var pingJob by remember { mutableStateOf<Job?>(null) }
    var pingCount by remember { mutableStateOf(0) }
    var hasShownInitialInfo by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }

    // Remember the stop icon to avoid recomposition
    val stopIcon = ImageVector.vectorResource(id = R.drawable.baseline_stop_circle_24)

    // Save address when it changes
    DisposableEffect(address) {
        onDispose {
            prefs.edit().putString(MainActivity.KEY_LAST_ADDRESS, address).apply()
        }
    }

    // Function to handle ping action
    val startPing = {
        if (address.isNotEmpty() && !isPinging) {
            focusManager.clearFocus()
            isPinging = true
            pingCount = 0
            hasShownInitialInfo = false
            lastError = null
            pingResults = emptyList()
            isFirstLine = true

            pingJob = coroutineScope.launch {
                while (isActive) {
                    val results = performSinglePing(address)
                    if (results.isNotEmpty()) {
                        if (!hasShownInitialInfo) {
                            // 第一次显示完整信息
                            pingResults = results.filter { !it.startsWith("Error:") }
                            hasShownInitialInfo = true

                            // 检查是否有错误
                            val error = results.find { it.startsWith("Error:") }
                            if (error != null && error != lastError) {
                                lastError = error
                                pingResults = pingResults + error
                            }
                        } else {
                            // 检查新的错误
                            val error = results.find { it.startsWith("Error:") }
                            if (error != null && error != lastError) {
                                lastError = error
                                pingResults = pingResults + error
                            }
                            // 添加非错误信息
                            val newResults = results.filter { !it.startsWith("Error:") }
                            if (newResults.isNotEmpty()) {
                                pingResults = (pingResults + newResults).takeLast(100)
                            }
                        }
                    }
                    delay(1000)
                }
            }
        }
    }

    // Function to stop ping
    val stopPing = {
        pingJob?.cancel()
        pingJob = null
        isPinging = false
        lastError = null
        pingResults = pingResults + listOf("Ping operation stopped")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Input Field
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Enter address to ping") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { startPing() }
            ),
            trailingIcon = {
                if (isPinging) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(48.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color.Green,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        IconButton(
                            onClick = { stopPing() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = stopIcon,
                                contentDescription = "Stop",
                                tint = Color.Green,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    IconButton(
                        onClick = { startPing() },
                        enabled = !isPinging && address.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (!isPinging && address.isNotEmpty()) Color.Green else Color.Green.copy(
                                alpha = 0.7f
                            )
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.Green,
                unfocusedTextColor = Color.Green,
                disabledTextColor = Color.Green.copy(alpha = 0.7f),
                errorTextColor = Color.Green.copy(alpha = 0.7f),
                focusedBorderColor = Color.Green,
                unfocusedBorderColor = Color.Green.copy(alpha = 0.7f),
                disabledBorderColor = Color.Green.copy(alpha = 0.7f),
                errorBorderColor = Color.Green.copy(alpha = 0.7f),
                focusedLabelColor = Color.Green,
                unfocusedLabelColor = Color.Green.copy(alpha = 0.7f),
                disabledLabelColor = Color.Green.copy(alpha = 0.7f),
                errorLabelColor = Color.Green.copy(alpha = 0.7f),
                cursorColor = Color.Green,
                errorCursorColor = Color.Green.copy(alpha = 0.7f),
                focusedPlaceholderColor = Color.Green.copy(alpha = 0.7f),
                unfocusedPlaceholderColor = Color.Green.copy(alpha = 0.7f),
                disabledPlaceholderColor = Color.Green.copy(alpha = 0.7f),
                errorPlaceholderColor = Color.Green.copy(alpha = 0.7f)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Results Display Area
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp)),
            color = Color(0xFF0A0A0A)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                if (pingResults.isEmpty()) {
                    Text(
                        text = "Enter an address and press enter to start continuous ping",
                        color = Color.Green.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        reverseLayout = true // Show newest results at the top
                    ) {
                        items(pingResults.asReversed()) { line ->
                            Text(
                                text = line,
                                color = Color.Green,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

var isFirstLine = true

/*
convert ping output to a more readable format
64 bytes from sea30s13-in-f14.1e100.net (142.251.211.238): icmp_seq=1 ttl=255 time=0.881 ms
64 bytes from 142.251.211.238: icmp_seq=1 ttl=255 time=0.881 ms
 */
fun parseIpv4Output(it: String, isFirstLine: Boolean): String {
    if (it.contains("bytes from")) {
        return it
    }
    if (isFirstLine && it.contains("PING") && it.contains("bytes of data")) {
        return it
    }
    return ""
}

fun parseIpv6Output(it: String, isFirstLine: Boolean): String {
    if (it.contains("From") && it.contains("icmp_seq=")) {
        return it
    }
    if (isFirstLine && it.contains("PING") && it.contains("data bytes")) {
        return it
    }
    return ""
}

suspend fun performSinglePing(address: String): List<String> = withContext(Dispatchers.IO) {
    val results = mutableListOf<String>()
    try {
        val isIpv6 = address.contains(":")
        val pingCommand = if (System.getProperty("os.name").lowercase().contains("windows")) {
            if (isIpv6) {
                "ping -n 1 -6 $address" // Windows IPv6
            } else {
                "ping -n 1 -4 $address" // Windows IPv4
            }
        } else {
            if (isIpv6) {
                "ping6 -c 1 $address" // Linux/macOS IPv6
            } else {
                "ping -c 1 $address"  // Linux/macOS IPv4
            }
        }

        val process = Runtime.getRuntime().exec(pingCommand)
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?

        // 调试：打印完整的命令
        Log.d("PingTool", "Executing command: $pingCommand")

        while (reader.readLine().also { line = it } != null) {
            line?.let {
                // 调试：打印每一行原始输出
                Log.d("PingTool", "Raw output: $it")
                if (!isIpv6) {
                    val parsed = parseIpv4Output(it, isFirstLine)
                    if (parsed.isNotEmpty()) {
                        Log.d("PingTool", "Parsed output: $parsed")
                        results.add(parsed)
                    } else {
                    }
                } else {
                    val parsed = parseIpv6Output(it, isFirstLine)
                    if (parsed.isNotEmpty()) {
                        Log.d("PingTool", "Parsed output: $parsed")
                        results.add(parsed)
                    } else {
                    }
                }
            }
            isFirstLine = false
        }

        val errorReader = BufferedReader(InputStreamReader(process.errorStream))
        while (errorReader.readLine().also { line = it } != null) {
            line?.let {
                // 调试：打印错误流输出
                Log.d("PingTool", "Error stream: $it")
                results.add(it)
            }
        }

        // 调试：打印进程退出码
        val exitCode = process.waitFor()
        Log.d("PingTool", "Process exit code: $exitCode")

    } catch (e: Exception) {
        Log.e("PingTool", "Error performing ping: ${e.message}")
        results.add("Error: ${e.message}")
    }

    // 调试：打印最终结果
    Log.d("PingTool", "Final results: $results")
    return@withContext results
}

@Preview(showBackground = true)
@Composable
fun PingToolPreview() {
    PingTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF1E1E1E)
        ) {
            PingTool(
                context = LocalContext.current
            )
        }
    }
}
package com.igino.legohat

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.igino.legohat.ui.theme.LegohatTheme
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.Vector

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LegohatTheme {
                LegohatApp()
            }
        }
    }
}

data class SshConfig(
    val host: String,
    val user: String,
    val pass: String,
    val path: String
)

object SecurityHelper {
    fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            "ssh_config_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveConfig(context: Context, config: SshConfig) {
        val prefs = getEncryptedPrefs(context)
        prefs.edit().apply {
            putString("host", config.host)
            putString("user", config.user)
            putString("pass", config.pass)
            putString("path", config.path)
            apply()
        }
        
        val history = getHistory(context).toMutableList()
        if (!history.any { it.host == config.host && it.user == config.user }) {
            history.add(config)
            saveHistory(context, history)
        }
    }

    fun loadConfig(context: Context): SshConfig {
        val prefs = getEncryptedPrefs(context)
        return SshConfig(
            host = prefs.getString("host", "") ?: "",
            user = prefs.getString("user", "") ?: "",
            pass = prefs.getString("pass", "") ?: "",
            path = prefs.getString("path", ".") ?: "."
        )
    }

    private fun saveHistory(context: Context, history: List<SshConfig>) {
        val jsonArray = JSONArray()
        history.forEach {
            val jsonObject = JSONObject()
            jsonObject.put("host", it.host)
            jsonObject.put("user", it.user)
            jsonObject.put("pass", it.pass)
            jsonObject.put("path", it.path)
            jsonArray.put(jsonObject)
        }
        getEncryptedPrefs(context).edit().putString("history", jsonArray.toString()).apply()
    }

    fun getHistory(context: Context): List<SshConfig> {
        val historyJson = getEncryptedPrefs(context).getString("history", null) ?: return emptyList()
        val history = mutableListOf<SshConfig>()
        try {
            val jsonArray = JSONArray(historyJson)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                history.add(SshConfig(
                    host = jsonObject.getString("host"),
                    user = jsonObject.getString("user"),
                    pass = jsonObject.getString("pass"),
                    path = jsonObject.getString("path")
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return history
    }
    
    fun deleteFromHistory(context: Context, config: SshConfig) {
        val history = getHistory(context).filterNot { it.host == config.host && it.user == config.user }
        saveHistory(context, history)
    }
}

@PreviewScreenSizes
@Composable
fun LegohatApp() {
    val context = LocalContext.current
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    var ipAddress by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var remotePath by rememberSaveable { mutableStateOf(".") }

    LaunchedEffect(Unit) {
        val config = SecurityHelper.loadConfig(context)
        ipAddress = config.host
        username = config.user
        password = config.pass
        remotePath = config.path
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            painterResource(it.icon),
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            val modifier = Modifier.padding(innerPadding)
            when (currentDestination) {
                AppDestinations.HOME -> Greeting(name = "Home", modifier = modifier)
                AppDestinations.FAVORITES -> FavoritesScreen(
                    onSelectServer = { config ->
                        ipAddress = config.host
                        username = config.user
                        password = config.pass
                        remotePath = config.path
                        currentDestination = AppDestinations.PROFILE
                    },
                    modifier = modifier
                )
                AppDestinations.PROFILE -> ProfileScreen(
                    ipAddress = ipAddress,
                    onIpAddressChange = { ipAddress = it },
                    username = username,
                    onUsernameChange = { username = it },
                    password = password,
                    onPasswordChange = { password = it },
                    remotePath = remotePath,
                    onRemotePathChange = { remotePath = it },
                    modifier = modifier
                )
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: Int,
) {
    HOME("Home", R.drawable.ic_home),
    FAVORITES("Favorites", R.drawable.ic_favorite),
    PROFILE("Profile", R.drawable.ic_account_box),
}

data class SshResult(
    val success: Boolean,
    val message: String,
    val files: List<String> = emptyList()
)

@Composable
fun FavoritesScreen(
    onSelectServer: (SshConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var history by remember { mutableStateOf(SecurityHelper.getHistory(context)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Saved Servers",
            style = MaterialTheme.typography.headlineMedium
        )
        
        if (history.isEmpty()) {
            Text("No saved servers yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history) { config ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onSelectServer(config) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "${config.user}@${config.host}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = config.path,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = { 
                            SecurityHelper.deleteFromHistory(context, config)
                            history = SecurityHelper.getHistory(context)
                        }) {
                            Text("Remove", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(
    ipAddress: String,
    onIpAddressChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    remotePath: String,
    onRemotePathChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isConnecting by rememberSaveable { mutableStateOf(false) }
    var isExecuting by rememberSaveable { mutableStateOf(false) }
    var isConnected by rememberSaveable { mutableStateOf(false) }
    var fileList by rememberSaveable { mutableStateOf(listOf<String>()) }
    var selectedFile by rememberSaveable { mutableStateOf<String?>(null) }
    var consoleOutput by rememberSaveable { mutableStateOf("") }
    var currentJob by remember { mutableStateOf<Job?>(null) }
    var shellOutputStream by remember { mutableStateOf<OutputStream?>(null) }
    val consoleScrollState = rememberScrollState()

    var isRemoteControlActive by rememberSaveable { mutableStateOf(false) }

    var isEditing by rememberSaveable { mutableStateOf(false) }
    var editorContent by rememberSaveable { mutableStateOf("") }
    var isSaving by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(consoleOutput) {
        consoleScrollState.animateScrollTo(consoleScrollState.maxValue)
    }

    if (isEditing) {
        BackHandler { isEditing = false }
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Editing: $selectedFile",
                style = MaterialTheme.typography.titleMedium
            )
            TextField(
                value = editorContent,
                onValueChange = { editorContent = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                ),
                placeholder = { Text("Loading content...") }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { isEditing = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    enabled = !isSaving
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        selectedFile?.let { fileName ->
                            isSaving = true
                            scope.launch {
                                val success = saveRemoteFile(ipAddress, username, password, "$remotePath/$fileName", editorContent)
                                isSaving = false
                                if (success) {
                                    Toast.makeText(context, "File saved successfully", Toast.LENGTH_SHORT).show()
                                    isEditing = false
                                } else {
                                    Toast.makeText(context, "Error saving file", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Save")
                }
            }
        }
        return
    }

    if (isRemoteControlActive) {
        BackHandler {
            scope.launch {
                try {
                    shellOutputStream?.write("x".toByteArray())
                    shellOutputStream?.flush()
                    delay(200)
                } catch (e: Exception) { }
                currentJob?.cancel()
                isRemoteControlActive = false
                isExecuting = false
            }
        }
        
        RemoteControlScreen(
            onSendKey = { key ->
                scope.launch {
                    try {
                        shellOutputStream?.write(key.toByteArray())
                        shellOutputStream?.flush()
                    } catch (e: Exception) { 
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Error sending key", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onExit = {
                scope.launch {
                    try {
                        shellOutputStream?.write("x".toByteArray())
                        shellOutputStream?.flush()
                        delay(200)
                    } catch (e: Exception) { }
                    currentJob?.cancel()
                    isRemoteControlActive = false
                    isExecuting = false
                }
            },
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!isConnected) {
            Text(
                text = "SSH Configuration",
                style = MaterialTheme.typography.headlineMedium
            )
            OutlinedTextField(
                value = ipAddress,
                onValueChange = onIpAddressChange,
                label = { Text("PC IP Address") },
                placeholder = { Text("e.g. 192.168.1.100") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isConnecting
            )
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isConnecting
            )
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isConnecting
            )
            OutlinedTextField(
                value = remotePath,
                onValueChange = onRemotePathChange,
                label = { Text("Remote Path") },
                placeholder = { Text("e.g. /home/user/docs") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isConnecting
            )
            
            Button(
                onClick = {
                    isConnecting = true
                    scope.launch {
                        val result = connectAndListSsh(ipAddress, username, password, remotePath)
                        if (result.success) {
                            SecurityHelper.saveConfig(context, SshConfig(ipAddress, username, password, remotePath))
                            isConnected = true
                            fileList = result.files
                            selectedFile = null
                            consoleOutput = ""
                        }
                        isConnecting = false
                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.align(Alignment.End),
                enabled = !isConnecting && ipAddress.isNotBlank() && username.isNotBlank()
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                }
                Text("Connect and Save")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Remote Files",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = remotePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(onClick = { 
                    isConnected = false
                    selectedFile = null
                    currentJob?.cancel()
                }) {
                    Text("Edit Config")
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp)
            ) {
                items(fileList) { fileName ->
                    val isSelected = fileName == selectedFile
                    Text(
                        text = fileName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { selectedFile = if (isSelected) null else fileName }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.Unspecified
                        )
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            
            if (consoleOutput.isNotEmpty() || isExecuting) {
                Text(
                    text = "Shell Response:",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.6f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(Color.Black)
                        .padding(8.dp)
                        .verticalScroll(consoleScrollState)
                ) {
                    if (isExecuting && consoleOutput.isEmpty()) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.Green,
                            strokeWidth = 2.dp
                        )
                    }
                    Text(
                        text = consoleOutput,
                        color = Color.Green,
                        modifier = Modifier.fillMaxWidth(),
                        softWrap = true,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { 
                        isConnected = false
                        selectedFile = null
                        consoleOutput = ""
                        currentJob?.cancel()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Disconnect")
                }
                
                Button(
                    onClick = {
                        selectedFile?.let { fileName ->
                            scope.launch {
                                val content = readRemoteFile(ipAddress, username, password, "$remotePath/$fileName")
                                if (content != null) {
                                    editorContent = content
                                    isEditing = true
                                } else {
                                    Toast.makeText(context, "Error reading file", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    enabled = selectedFile != null && !isExecuting,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Edit")
                }
                
                if (isExecuting) {
                    Button(
                        onClick = { 
                            currentJob?.cancel()
                            isExecuting = false
                            consoleOutput += "\n--- Interrupted by user ---\n"
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Stop")
                    }
                } else {
                    Button(
                        onClick = {
                            selectedFile?.let { fileName ->
                                isExecuting = true
                                consoleOutput = "Executing $fileName...\n"
                                
                                if (fileName == "remote.py") {
                                    isRemoteControlActive = true
                                }

                                currentJob = scope.launch {
                                    val command = if (fileName.endsWith(".py")) {
                                        "cd \"$remotePath\" && python -u \"$fileName\""
                                    } else {
                                        "cd \"$remotePath\" && ./\"$fileName\""
                                    }
                                    
                                    runSshInteractive(ipAddress, username, password, command, 
                                        onOutput = { text -> consoleOutput += text },
                                        onStreamReady = { stream -> shellOutputStream = stream }
                                    )
                                    
                                    isExecuting = false
                                    isRemoteControlActive = false
                                }
                            }
                        },
                        enabled = selectedFile != null,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Run")
                    }
                }
            }
        }
    }
}

@Composable
fun RemoteControlScreen(
    onSendKey: (String) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Text(
            text = "Remote Control: remote.py",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MotorControl("Motor A", "q", "a", onSendKey)
            MotorControl("Motor B", "w", "s", onSendKey)
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MotorControl("Motor C", "e", "d", onSendKey)
            MotorControl("Motor D", "r", "f", onSendKey)
        }
        
        Button(
            onClick = onExit,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(0.5f).height(56.dp)
        ) {
            Text("EXIT (x)", fontSize = 18.sp)
        }
    }
}

@Composable
fun MotorControl(
    label: String,
    upKey: String,
    downKey: String,
    onSendKey: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = label, fontWeight = FontWeight.Medium)
        Button(
            onClick = { onSendKey(upKey) },
            modifier = Modifier.size(80.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("UP")
        }
        Button(
            onClick = { onSendKey(downKey) },
            modifier = Modifier.size(80.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("DOWN")
        }
    }
}

suspend fun runSshInteractive(
    host: String, 
    user: String, 
    pass: String, 
    command: String,
    onOutput: (String) -> Unit,
    onStreamReady: (OutputStream) -> Unit
): String {
    return withContext(Dispatchers.IO) {
        var session: Session? = null
        var channel: ChannelExec? = null
        try {
            val jsch = JSch()
            session = jsch.getSession(user, host, 22)
            session.setPassword(pass)
            session.setConfig("StrictHostKeyChecking", "no")
            session.connect(10000)
            
            channel = session.openChannel("exec") as ChannelExec
            channel.setPty(true)
            channel.setPtyType("xterm")
            channel.setCommand(command)
            
            val inputStream = channel.inputStream
            val errorStream = channel.errStream
            
            // Usiamo un PipedOutputStream per inviare i dati affidabilmente
            val pipedOut = PipedOutputStream()
            val pipedIn = PipedInputStream(pipedOut)
            channel.setInputStream(pipedIn)
            
            channel.connect()
            
            // Comunichiamo alla UI lo stream su cui scrivere
            withContext(Dispatchers.Main) { onStreamReady(pipedOut) }
            
            val buffer = ByteArray(1024)
            while (true) {
                if (!isActive) break
                
                var hasRead = false
                while (inputStream.available() > 0) {
                    val i = inputStream.read(buffer, 0, 1024)
                    if (i < 0) break
                    val text = String(buffer, 0, i)
                    withContext(Dispatchers.Main) { onOutput(text) }
                    hasRead = true
                }
                while (errorStream.available() > 0) {
                    val i = errorStream.read(buffer, 0, 1024)
                    if (i < 0) break
                    val text = "ERR: " + String(buffer, 0, i)
                    withContext(Dispatchers.Main) { onOutput(text) }
                    hasRead = true
                }
                
                if (channel.isClosed) {
                    if (inputStream.available() > 0 || errorStream.available() > 0) continue
                    break
                }
                if (!hasRead) delay(50)
            }
            "\n--- End ---"
        } catch (e: Exception) {
            "\nError: ${e.localizedMessage}"
        } finally {
            channel?.disconnect()
            session?.disconnect()
        }
    }
}

suspend fun readRemoteFile(host: String, user: String, pass: String, filePath: String): String? {
    return withContext(Dispatchers.IO) {
        var session: Session? = null
        var channel: ChannelSftp? = null
        try {
            val jsch = JSch()
            session = jsch.getSession(user, host, 22)
            session.setPassword(pass)
            session.setConfig("StrictHostKeyChecking", "no")
            session.connect(10000)
            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect()
            val outputStream = ByteArrayOutputStream()
            channel.get(filePath, outputStream)
            outputStream.toString()
        } catch (e: Exception) {
            null
        } finally {
            channel?.disconnect()
            session?.disconnect()
        }
    }
}

suspend fun saveRemoteFile(host: String, user: String, pass: String, filePath: String, content: String): Boolean {
    return withContext(Dispatchers.IO) {
        var session: Session? = null
        var channel: ChannelSftp? = null
        try {
            val jsch = JSch()
            session = jsch.getSession(user, host, 22)
            session.setPassword(pass)
            session.setConfig("StrictHostKeyChecking", "no")
            session.connect(10000)
            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect()
            val inputStream = ByteArrayInputStream(content.toByteArray())
            channel.put(inputStream, filePath)
            true
        } catch (e: Exception) {
            false
        } finally {
            channel?.disconnect()
            session?.disconnect()
        }
    }
}

suspend fun connectAndListSsh(host: String, user: String, pass: String, path: String): SshResult {
    return withContext(Dispatchers.IO) {
        var session: Session? = null
        var channel: ChannelSftp? = null
        try {
            val jsch = JSch()
            session = jsch.getSession(user, host, 22)
            session.setPassword(pass)
            session.setConfig("StrictHostKeyChecking", "no")
            session.connect(10000)
            
            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect()
            
            val files = mutableListOf<String>()
            val directoryListing: Vector<*> = channel.ls(path.ifBlank { "." })
            for (obj in directoryListing) {
                if (obj is ChannelSftp.LsEntry) {
                    val name = obj.filename
                    if (name != "." && name != "..") {
                        files.add(name)
                    }
                }
            }
            
            SshResult(true, "Connection successful!", files.sorted())
        } catch (e: Exception) {
            SshResult(false, "Error: ${e.localizedMessage}")
        } finally {
            channel?.disconnect()
            session?.disconnect()
        }
    }
}

suspend fun runSshCommand(
    host: String, 
    user: String, 
    pass: String, 
    command: String,
    onOutput: (String) -> Unit
): String {
    return withContext(Dispatchers.IO) {
        var session: Session? = null
        var channel: ChannelExec? = null
        try {
            val jsch = JSch()
            session = jsch.getSession(user, host, 22)
            session.setPassword(pass)
            session.setConfig("StrictHostKeyChecking", "no")
            session.connect(10000)
            
            channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            
            val inputStream = channel.inputStream
            val errorStream = channel.errStream
            
            channel.connect()
            
            val buffer = ByteArray(1024)
            while (true) {
                if (!isActive) break
                var hasRead = false
                while (inputStream.available() > 0) {
                    val i = inputStream.read(buffer, 0, 1024)
                    if (i < 0) break
                    val text = String(buffer, 0, i)
                    withContext(Dispatchers.Main) { onOutput(text) }
                    hasRead = true
                }
                while (errorStream.available() > 0) {
                    val i = errorStream.read(buffer, 0, 1024)
                    if (i < 0) break
                    val text = "ERR: " + String(buffer, 0, i)
                    withContext(Dispatchers.Main) { onOutput(text) }
                    hasRead = true
                }
                if (channel.isClosed) {
                    if (inputStream.available() > 0 || errorStream.available() > 0) continue
                    break
                }
                if (!hasRead) delay(100)
            }
            "\n--- Fine esecuzione ---"
        } catch (e: Exception) {
            "\nErrore esecuzione: ${e.localizedMessage}"
        } finally {
            channel?.disconnect()
            session?.disconnect()
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Page $name",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    LegohatTheme {
        Greeting("Android")
    }
}

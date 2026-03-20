package com.igino.legohat

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.igino.legohat.ui.theme.LegohatTheme
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    fun saveConfig(context: Context, host: String, user: String, pass: String, path: String) {
        getEncryptedPrefs(context).edit().apply {
            putString("host", host)
            putString("user", user)
            putString("pass", pass)
            putString("path", path)
            apply()
        }
    }

    fun loadConfig(context: Context): Map<String, String> {
        val prefs = getEncryptedPrefs(context)
        return mapOf(
            "host" to (prefs.getString("host", "") ?: ""),
            "user" to (prefs.getString("user", "") ?: ""),
            "pass" to (prefs.getString("pass", "") ?: ""),
            "path" to (prefs.getString("path", ".") ?: ".")
        )
    }
}

@PreviewScreenSizes
@Composable
fun LegohatApp() {
    val context = LocalContext.current
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    // SSH state
    var ipAddress by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var remotePath by rememberSaveable { mutableStateOf(".") }

    // Load saved configuration on first launch
    LaunchedEffect(Unit) {
        val config = SecurityHelper.loadConfig(context)
        ipAddress = config["host"] ?: ""
        username = config["user"] ?: ""
        password = config["pass"] ?: ""
        remotePath = config["path"] ?: "."
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
                AppDestinations.FAVORITES -> Greeting(name = "Favorites", modifier = modifier)
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
    var isConnected by rememberSaveable { mutableStateOf(false) }
    var fileList by rememberSaveable { mutableStateOf(listOf<String>()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!isConnected) {
            Text(
                text = "Configurazione SSH",
                style = MaterialTheme.typography.headlineMedium
            )
            OutlinedTextField(
                value = ipAddress,
                onValueChange = onIpAddressChange,
                label = { Text("Indirizzo IP PC") },
                placeholder = { Text("es. 192.168.1.100") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isConnecting
            )
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Nome Utente") },
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
                label = { Text("Percorso Remoto") },
                placeholder = { Text("es. /home/utente/documenti") },
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
                            SecurityHelper.saveConfig(context, ipAddress, username, password, remotePath)
                            isConnected = true
                            fileList = result.files
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
                Text("Connetti e Salva")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "File in remoto",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = remotePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(onClick = { isConnected = false }) {
                    Text("Modifica Config")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                items(fileList) { fileName ->
                    Text(
                        text = fileName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            
            Button(
                onClick = { isConnected = false },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Disconnetti")
            }
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
            
            val config = java.util.Properties()
            config["StrictHostKeyChecking"] = "no"
            session.setConfig(config)
            
            session.connect(10000)
            
            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect()
            
            val files = mutableListOf<String>()
            val directoryListing: Vector<*> = channel.ls(path.ifBlank { "." })
            for (obj in directoryListing) {
                if (obj is ChannelSftp.LsEntry) {
                    files.add(obj.filename)
                }
            }
            
            SshResult(true, "Connessione riuscita!", files)
        } catch (e: Exception) {
            SshResult(false, "Errore: ${e.localizedMessage}")
        } finally {
            channel?.disconnect()
            session?.disconnect()
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Pagina $name",
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

package io.github.ishaaaak18.iptv.ui.add

import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.ishaaaak18.iptv.IptvApp
import io.github.ishaaaak18.iptv.R
import io.github.ishaaaak18.iptv.core.PlaylistLoader
import io.github.ishaaaak18.iptv.core.model.PlaylistSource
import io.github.ishaaaak18.iptv.core.xtream.XtreamCredentials
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlaylistScreen(onDone: (String) -> Unit, onCancel: () -> Unit) {
    val app = IptvApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun currentSource(): PlaylistSource? = if (tab == 0) {
        PlaylistLoader.sourceFromInput(link)
    } else {
        val h = host.trim().trimEnd('/')
        if (h.startsWith("http") && user.isNotBlank() && pass.isNotBlank()) {
            PlaylistSource.Xtream(h, user.trim(), pass.trim())
        } else {
            null
        }
    }

    fun currentCredentials(): XtreamCredentials? = when (val s = currentSource()) {
        is PlaylistSource.Xtream -> XtreamCredentials(s.host, s.username, s.password)
        is PlaylistSource.M3uUrl -> XtreamCredentials.parse(s.url)
        else -> null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_playlist)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cancel)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.m3u_link)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.xtream)) })
            }
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text(stringResource(R.string.name)) }, modifier = Modifier.fillMaxWidth().testTag("field_name"),
            )
            if (tab == 0) {
                OutlinedTextField(
                    value = link, onValueChange = { link = it },
                    label = { Text(stringResource(R.string.link_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth().testTag("field_link"),
                )
            } else {
                OutlinedTextField(
                    value = host, onValueChange = { host = it }, singleLine = true,
                    label = { Text(stringResource(R.string.server)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = user, onValueChange = { user = it }, singleLine = true,
                    label = { Text(stringResource(R.string.username)) }, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it }, singleLine = true,
                    label = { Text(stringResource(R.string.password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    modifier = Modifier.testTag("save"),
                    enabled = !busy,
                    onClick = {
                        val source = currentSource()
                        if (source == null) {
                            message = context.getString(R.string.invalid_input)
                            return@Button
                        }
                        busy = true
                        message = context.getString(R.string.loading)
                        scope.launch {
                            runCatching { app.repository.addPlaylist(name.trim(), source) }
                                .onSuccess { onDone(it.id) }
                                .onFailure { message = context.getString(R.string.error_loading, it.message ?: "") }
                            busy = false
                        }
                    },
                ) { Text(stringResource(R.string.save)) }

                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        val creds = currentCredentials()
                        if (creds == null) {
                            message = context.getString(R.string.invalid_input)
                            return@OutlinedButton
                        }
                        busy = true
                        message = context.getString(R.string.loading)
                        scope.launch {
                            message = runCatching { app.xtreamApi.account(creds).userInfo }
                                .fold(
                                    onSuccess = { info ->
                                        if (info == null) {
                                            context.getString(R.string.invalid_input)
                                        } else {
                                            val unlimited = context.getString(R.string.account_unlimited)
                                            val expiry = info.expiresAt?.let {
                                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it * 1000))
                                            } ?: unlimited
                                            listOf(
                                                context.getString(R.string.account_status, info.status ?: "—"),
                                                context.getString(R.string.account_expiry, expiry),
                                                context.getString(R.string.account_connections, info.active, info.max?.toString() ?: unlimited),
                                            ).joinToString("\n")
                                        }
                                    },
                                    onFailure = { context.getString(R.string.error_loading, it.message ?: "") },
                                )
                            busy = false
                        }
                    },
                ) { Text(stringResource(R.string.check_account)) }

                if (busy) CircularProgressIndicator(Modifier.size(24.dp))
            }

            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("message")) }
            Text(
                stringResource(R.string.disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

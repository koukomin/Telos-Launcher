package de.mm20.launcher2.network.nextdns.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.theme.LauncherTheme
import de.mm20.launcher2.network.nextdns.NextDnsRepository
import de.mm20.launcher2.network.nextdns.NextDnsViewModel

class NextDnsEditorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = NextDnsRepository(applicationContext)
        val factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return NextDnsViewModel(repo) as T
            }
        }
        setContent {
            LauncherTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val vm: NextDnsViewModel = viewModel(factory = factory)
                    NextDnsEditorScreen(vm, applicationContext)
                }
            }
        }
    }

    companion object {
        fun open(context: Context) {
            context.startActivity(Intent(context, NextDnsEditorActivity::class.java))
        }
    }
}

@Composable
fun NextDnsEditorScreen(vm: NextDnsViewModel, ctx: Context) {
    val logs by vm.logs.collectAsStateWithLifecycle(initial = emptyList())
    val loading by vm.loading.collectAsStateWithLifecycle(initial = false)
    val error by vm.error.collectAsStateWithLifecycle(initial = null)

    var token by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("NextDNS Editor", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            label = { Text("API Token") }
        )
        Button(onClick = { vm.setToken(token) }, modifier = Modifier.fillMaxWidth()) {
            Text("Save & Fetch Logs")
        }

        if (loading) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(logs) { entry ->
                LogRow(entry.domain, entry.action, onAllow = { vm.allowDomain(entry.domain) }, onDeny = { vm.denyDomain(entry.domain) })
            }
        }
    }
}

@Composable
fun LogRow(domain: String, action: String, onAllow: () -> Unit, onDeny: () -> Unit) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp)) {
        Text(domain, style = MaterialTheme.typography.bodyLarge)
        Text(action, style = MaterialTheme.typography.bodySmall)
        RowButtons(onAllow, onDeny)
    }
}

@Composable
fun RowButtons(onAllow: () -> Unit, onDeny: () -> Unit) {
    androidx.compose.material3.Button(onClick = onAllow, modifier = Modifier.padding(end = 8.dp)) { Text("Allow") }
    androidx.compose.material3.Button(onClick = onDeny) { Text("Deny") }
}

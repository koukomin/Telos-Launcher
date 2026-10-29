package com.telos.desktop.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.telos.desktop.linux.Sys
import com.telos.desktop.ui.TelosColors
import java.io.File

object TelosPolkitAgent {
    var visible by mutableStateOf(false)
    var message by mutableStateOf("Authentication required")
    var password by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    private var pending: ((String) -> Unit)? = null

    fun installAskPass(): File {
        val dir = File(System.getProperty("user.home"), ".local/share/telos")
        dir.mkdirs()
        val script = File(dir, "telos-askpass.sh")
        if (!script.isFile) {
            script.writeText("#!/bin/sh\nzenity --password --title=\"Telos\" 2>/dev/null || kdialog --password \"Telos\" 2>/dev/null\n")
            script.setExecutable(true)
        }
        return script
    }

    fun prompt(msg: String, onSecret: (String) -> Unit) {
        message = msg
        password = ""
        error = null
        pending = onSecret
        visible = true
    }

    fun submit() {
        val p = password
        pending?.invoke(p)
        pending = null
        visible = false
        password = ""
    }

    fun pkexec(vararg args: String): Boolean {
        val envAsk = installAskPass()
        return Sys.run(*(arrayOf("pkexec") + args), timeoutSec = 30).ok ||
            Sys.run(*(arrayOf("sudo", "-A") + args), timeoutSec = 30).ok.also {
                System.setProperty("SUDO_ASKPASS", envAsk.absolutePath)
            }
    }
}

@Composable
fun PolkitDialog() {
    if (!TelosPolkitAgent.visible) return
    Column(Modifier.width(360.dp).background(TelosColors.surfaceContainer).padding(16.dp)) {
        Text(TelosPolkitAgent.message, color = TelosColors.onSurface)
        OutlinedTextField(
            TelosPolkitAgent.password,
            { TelosPolkitAgent.password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )
        TelosPolkitAgent.error?.let { Text(it, color = TelosColors.primary) }
        Button(onClick = { TelosPolkitAgent.submit() }) { Text("Authenticate") }
        Button(onClick = { TelosPolkitAgent.visible = false }) { Text("Cancel") }
    }
}

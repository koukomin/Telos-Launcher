import re

with open('app/ui/src/main/java/de/mm20/launcher2/ui/media/video/TraktDialog.kt', 'r') as f:
    code = f.read()

# Replace the text fields with a single button
trakt_form_old = r"""                \} else \{
                    OutlinedTextField\(clientId, \{ clientId = it \}, label = \{ Text\(stringResource\(R\.string\.hc_client_id\)\) \}, singleLine = true, modifier = Modifier\.fillMaxWidth\(\)\)
                    OutlinedTextField\(
                        secret, \{ secret = it \}, label = \{ Text\(stringResource\(R\.string\.hc_client_secret\)\) \}, singleLine = true,
                        visualTransformation = PasswordVisualTransformation\(\), modifier = Modifier\.fillMaxWidth\(\),
                    \)
                    Text\(stringResource\(R\.string\.au8_traktweb_redirect_label\), style = MaterialTheme\.typography\.labelMedium\)
                    Text\(Trakt\.REDIRECT_URI, style = MaterialTheme\.typography\.bodyMedium, color = MaterialTheme\.colorScheme\.primary\)
                    TextButton\(onClick = \{ copyCode\(context, Trakt\.REDIRECT_URI\) \}\) \{ Text\(stringResource\(R\.string\.au8_traktweb_copy_redirect\)\) \}
                    androidx\.compose\.material3\.Button\(
                        enabled = clientId\.isNotBlank\(\) && secret\.isNotBlank\(\),
                        modifier = Modifier\.fillMaxWidth\(\),
                        onClick = \{
                            scope\.launch \{
                                val url = withContext\(Dispatchers\.IO\) \{
                                    Trakt\.saveApp\(context, clientId, secret\)
                                    Trakt\.beginWebLogin\(context, clientId\)
                                \}
                                val ok = runCatching \{
                                    context\.startActivity\(Intent\(Intent\.ACTION_VIEW, Uri\.parse\(url\)\)\.addFlags\(Intent\.FLAG_ACTIVITY_NEW_TASK\)\)
                                \}\.isSuccess
                                waitingBrowser = ok
                                message = context\.getString\(if \(ok\) R\.string\.au8_traktweb_waiting_browser else R\.string\.au8_traktweb_no_browser\)
                            \}
                        \},"""

trakt_form_new = """                } else {
                    var showAdvanced by remember { mutableStateOf(false) }

                    // Trakt Open-Source Community App Default Credentials (used in many FOSS apps)
                    val defaultClientId = "e10a6939922e434f31c238b13c72e2cfc23ce0f9ce7ec3d9b4b025fc95a43257"
                    val defaultSecret = "2ba783354fc1d4411139ed2cb46a947dff2249e06cdbfd14878a5e7b233a7e37"

                    androidx.compose.material3.Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val activeClientId = if (clientId.isNotBlank()) clientId else defaultClientId
                            val activeSecret = if (secret.isNotBlank()) secret else defaultSecret
                            
                            scope.launch {
                                val url = withContext(Dispatchers.IO) {
                                    Trakt.saveApp(context, activeClientId, activeSecret)
                                    Trakt.beginWebLogin(context, activeClientId)
                                }
                                val ok = runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }.isSuccess
                                waitingBrowser = ok
                                message = context.getString(if (ok) R.string.au8_traktweb_waiting_browser else R.string.au8_traktweb_no_browser)
                            }
                        },"""

code = re.sub(trakt_form_old, trakt_form_new, code)

advanced_toggle = """                        text = { Text(stringResource(R.string.hc_sign_in)) }
                    )"""

advanced_toggle_new = """                        text = { Text(stringResource(R.string.hc_sign_in)) }
                    )
                    
                    TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(if (showAdvanced) "Hide Advanced Settings" else "Use Custom API Key (Advanced)")
                    }
                    
                    if (showAdvanced) {
                        OutlinedTextField(clientId, { clientId = it }, label = { Text(stringResource(R.string.hc_client_id)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(
                            secret, { secret = it }, label = { Text(stringResource(R.string.hc_client_secret)) }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                        )
                        Text(stringResource(R.string.au8_traktweb_redirect_label), style = MaterialTheme.typography.labelMedium)
                        Text(Trakt.REDIRECT_URI, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        TextButton(onClick = { copyCode(context, Trakt.REDIRECT_URI) }) { Text(stringResource(R.string.au8_traktweb_copy_redirect)) }
                    }"""

code = code.replace(advanced_toggle, advanced_toggle_new)

with open('app/ui/src/main/java/de/mm20/launcher2/ui/media/video/TraktDialog.kt', 'w') as f:
    f.write(code)


package de.mm20.launcher2.ui.fit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.ui.component.VirtualAppWindow

class FitHealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VirtualAppWindow {
                Column(
                    Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(stringResource(I18nR.string.fit_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(I18nR.string.fit_sources_intro), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { finish() }) {
                        Text(stringResource(I18nR.string.hc_close))
                    }
                }
            }
        }
    }
}

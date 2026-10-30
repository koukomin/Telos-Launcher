package de.mm20.launcher2.uikmp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SharedCalculatorScreen(
    title: String,
    display: String,
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    Column(modifier.background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
        Text(title, color = MaterialTheme.colorScheme.secondary, fontSize = 20.sp)
        Text(display, color = MaterialTheme.colorScheme.onSurface, fontSize = 28.sp, modifier = Modifier.padding(vertical = 8.dp))
        listOf(
            listOf("7", "8", "9", "/"),
            listOf("4", "5", "6", "*"),
            listOf("1", "2", "3", "-"),
            listOf("0", "C", "=", "+"),
        ).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    Button(onClick = { onKey(key) }, modifier = Modifier.weight(1f)) { Text(key) }
                }
            }
        }
    }
}

@Composable
fun SharedClockScreen(
    title: String,
    time: String,
    date: String,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    Column(modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        Text(title, color = MaterialTheme.colorScheme.secondary, fontSize = 20.sp)
        Text(time, color = MaterialTheme.colorScheme.onSurface, fontSize = 42.sp)
        Text(date, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun SharedNotesScreen(
    title: String,
    placeholder: String,
    text: String,
    onText: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    Column(modifier.background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
        Text(title, color = MaterialTheme.colorScheme.secondary, fontSize = 20.sp)
        OutlinedTextField(
            text,
            onText,
            modifier = Modifier.fillMaxSize().padding(top = 8.dp),
            label = { Text(placeholder) },
        )
    }
}

@Composable
fun SharedCalendarScreen(
    title: String,
    daysInMonth: Int,
    today: Int,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    Column(modifier.background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
        Text(title, color = MaterialTheme.colorScheme.secondary, fontSize = 20.sp)
        LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.padding(top = 8.dp)) {
            items((1..daysInMonth).toList()) { d ->
                Text(
                    "$d",
                    color = if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

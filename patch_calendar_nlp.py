import re

with open('app/ui/src/main/java/de/mm20/launcher2/ui/calendar/CalendarScreen.kt', 'r') as f:
    code = f.read()

nlp_parser = """
internal fun parseNaturalLanguageEvent(input: String, defaultCalendarId: Long, selectedDate: LocalDate): Draft {
    var title = input.trim()
    var eventDate = selectedDate
    var eventTime = LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)

    val lower = input.lowercase()

    if (lower.contains("αύριο") || lower.contains("αυριο") || lower.contains("tomorrow")) {
        eventDate = LocalDate.now().plusDays(1)
        title = title.replace(Regex("(?i)\\\\b(αύριο|αυριο|tomorrow)\\\\b"), "").trim()
    } else if (lower.contains("μεθαύριο") || lower.contains("μεθαυριο")) {
        eventDate = LocalDate.now().plusDays(2)
        title = title.replace(Regex("(?i)\\\\b(μεθαύριο|μεθαυριο)\\\\b"), "").trim()
    } else if (lower.contains("σήμερα") || lower.contains("σημερα") || lower.contains("today")) {
        eventDate = LocalDate.now()
        title = title.replace(Regex("(?i)\\\\b(σήμερα|σημερα|today)\\\\b"), "").trim()
    }

    val timeRegex = Regex("(?i)(?:στις\\\\s*|at\\\\s*)?(\\\\d{1,2})(?::(\\\\d{2}))?\\\\s*(pm|am|μμ|πμ)?")
    val timeMatch = timeRegex.find(lower)
    if (timeMatch != null && timeMatch.value.isNotBlank()) {
        val hourStr = timeMatch.groupValues[1]
        val minStr = timeMatch.groupValues[2]
        val ampm = timeMatch.groupValues[3]

        var hour = hourStr.toIntOrNull() ?: 12
        val min = minStr.toIntOrNull() ?: 0

        if (ampm == "pm" || ampm == "μμ") {
            if (hour < 12) hour += 12
        } else if (ampm == "am" || ampm == "πμ") {
            if (hour == 12) hour = 0
        } else if (hour < 8 && !lower.contains("πμ")) {
            hour += 12
        }

        eventTime = LocalTime.of(hour.coerceIn(0, 23), min.coerceIn(0, 59))
        title = title.replace(timeMatch.value, "").replace(Regex("(?i)\\\\bστις\\\\b"), "").trim()
    }

    val start = LocalDateTime.of(eventDate, eventTime)
    return Draft(
        calendarId = defaultCalendarId,
        title = title.ifBlank { "New Event" },
        start = start,
        end = start.plusHours(1)
    )
}
"""

if "fun parseNaturalLanguageEvent" not in code:
    code = code.replace("private data class Draft(", nlp_parser + "\nprivate data class Draft(")

# Add Quick Add FAB / Sheet trigger in CalendarContent
fab_old = r"""        floatingActionButton = \{
            if \(writable\.isNotEmpty\(\)\) FloatingActionButton\(onClick = \{
                val start = LocalDateTime\.of\(selected, LocalTime\.now\(\)\.plusHours\(1\)\.withMinute\(0\)\.withSecond\(0\)\.withNano\(0\)\)
                draft = Draft\(calendarId = \(writable\.firstOrNull \{ it\.visible \} \?: writable\.first\(\)\)\.id, start = start, end = start\.plusHours\(1\)\)
            \}\) \{ Icon\(painterResource\(R\.drawable\.add_24px\), stringResource\(R\.string\.cal_new_event\)\) \}
        \},"""

fab_new = """        floatingActionButton = {
            if (writable.isNotEmpty()) {
                var showQuickAdd by remember { mutableStateOf(false) }
                var quickText by remember { mutableStateOf("") }
                
                if (showQuickAdd) {
                    AlertDialog(
                        onDismissRequest = { showQuickAdd = false },
                        title = { Text("Quick Event (Natural Language)") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Type in plain text (e.g. 'Συνάντηση αύριο στις 5' or 'Lunch with Alex at 2pm')", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedTextField(
                                    value = quickText,
                                    onValueChange = { quickText = it },
                                    placeholder = { Text("e.g. Καφές αύριο στις 6") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                enabled = quickText.isNotBlank(),
                                onClick = {
                                    val defaultId = (writable.firstOrNull { it.visible } ?: writable.first()).id
                                    draft = parseNaturalLanguageEvent(quickText, defaultId, selected)
                                    showQuickAdd = false
                                }
                            ) { Text("Create Event") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showQuickAdd = false }) { Text(stringResource(R.string.cal_cancel)) }
                        }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallFloatingActionButton(
                        onClick = { showQuickAdd = true },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ) {
                        Icon(painterResource(R.drawable.auto_awesome_20dp), contentDescription = "Quick AI Add")
                    }
                    FloatingActionButton(onClick = {
                        val start = LocalDateTime.of(selected, LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0))
                        draft = Draft(calendarId = (writable.firstOrNull { it.visible } ?: writable.first()).id, start = start, end = start.plusHours(1))
                    }) { Icon(painterResource(R.drawable.add_24px), stringResource(R.string.cal_new_event)) }
                }
            }
        },"""

code = re.sub(fab_old, fab_new, code)

with open('app/ui/src/main/java/de/mm20/launcher2/ui/calendar/CalendarScreen.kt', 'w') as f:
    f.write(code)


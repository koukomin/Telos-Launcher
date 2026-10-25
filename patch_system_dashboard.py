import re

with open('app/ui/src/main/java/de/mm20/launcher2/ui/system/SystemDashboardScreen.kt', 'r') as f:
    code = f.read()

# Insert state
code = code.replace(
    'val pulse = rememberInfiniteTransition(label = "sysPulse")',
    'var currentTab by remember { mutableStateOf(0) }\n    val pulse = rememberInfiniteTransition(label = "sysPulse")'
)

# Replace LazyColumn opening
code = code.replace(
"""            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {""",
"""            androidx.compose.material3.TabRow(selectedTabIndex = currentTab, containerColor = Color.Transparent) {
                androidx.compose.material3.Tab(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    text = { Text(stringResource(R.string.telos_system_label)) }
                )
                androidx.compose.material3.Tab(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    text = { Text(stringResource(R.string.hc_apps)) }
                )
            }
            if (currentTab == 1) {
                SystemAppsTab(
                    onOpenDetails = { pkg -> backStack.add(de.mm20.launcher2.ui.store.AppDetailsRoute(pkg)) }
                )
            } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {"""
)

# Add closing brace for 'else' block right after the LazyColumn ends.
# The LazyColumn ends around line 316.
# It ends with:
#                 }
#             }
#         }
#     }
#     }
# }

code = code.replace(
"""                }
            }
        }
    }
    }
}""",
"""                }
            }
            }
        }
    }
    }
}"""
)

with open('app/ui/src/main/java/de/mm20/launcher2/ui/system/SystemDashboardScreen.kt', 'w') as f:
    f.write(code)


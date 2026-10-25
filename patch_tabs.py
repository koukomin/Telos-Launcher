import re

with open('app/ui/src/main/java/de/mm20/launcher2/ui/store/StoreDashboardScreen.kt', 'r') as f:
    code = f.read()

# Replace the tabs logic
new_tabs = """                val playTabIndex = if (playStoreInstalled) 3 else -1

                PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 16.dp) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.hc_apps), maxLines = 1) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.hc_telos_apps), maxLines = 1) })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(stringResource(R.string.recommendation_tab_label), maxLines = 1) })
                    if (playStoreInstalled) {
                        Tab(selected = tab == playTabIndex, onClick = { 
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://collection/updates")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }.onFailure {
                                runCatching { context.packageManager.getLaunchIntentForPackage("com.android.vending")?.let { context.startActivity(it) } }
                            }
                        }, text = { Text(stringResource(R.string.store_play_tab), maxLines = 1) })
                    }
                }
                if (tab == 0) {
                    AppsTab(
                        rows = rows,
                        totalCount = allRows.size,
                        updateCount = allRows.count { it.updateAvailable },
                        installStates = installStates,
                        query = query,
                        filter = filter,
                        category = category,
                        categories = categories,
                        onQuery = { viewModel.query.value = it },
                        onFilter = { viewModel.filter.value = it },
                        onCategory = { viewModel.category.value = it },
                        onOpen = { backStack.add(AppDetailsRoute(itemId = it.item.id)) },
                        onAction = { viewModel.onAppActionClicked(it.item) },
                        onUpdateAll = { viewModel.updateAll() },
                        checking = checking,
                        onRefresh = { viewModel.checkAll() },
                    )
                } else if (tab == 1) {
                    TelosAppsTab(
                        disabled = disabledTelos,
                        onToggle = { app, enabled -> viewModel.setTelosAppEnabled(app.key, enabled) },
                        onOpen = { app ->
                            context.startActivity(
                                Intent().setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                                    .putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, app.route)
                                    .apply { app.commsTab?.let { putExtra(SettingsDeepLinkContract.EXTRA_COMMS_TAB, it) } }
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                    )
                } else if (tab == 2) {
                    RecommendedTab(
                        tracked = trackedUrls,
                        onAdd = { app, done ->
                            viewModel.addRecommended(app) { error ->
                                done()
                                if (error != null) Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                        },
                    )
                }
            }
            if (tab == 0) {"""

# Replace from `val playTabIndex` to `if (tab == 0) { \n FloatingActionButton`
old_pattern = r"val playTabIndex = if \(playStoreInstalled\) 4 else -1\s*PrimaryScrollableTabRow.*?} else if \(playStoreInstalled && tab == playTabIndex\) {.*?PlayStoreTab\(.*?\)\s*}\s*}\s*if \(tab == 0\) \{"

code = re.sub(old_pattern, new_tabs, code, flags=re.DOTALL)

with open('app/ui/src/main/java/de/mm20/launcher2/ui/store/StoreDashboardScreen.kt', 'w') as f:
    f.write(code)


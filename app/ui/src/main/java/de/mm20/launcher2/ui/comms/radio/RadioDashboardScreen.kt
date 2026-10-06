// === TELOS_PENDING_REVIEW_START: radio_browser_ktor ===
package de.mm20.launcher2.ui.comms.radio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import de.mm20.launcher2.comms.model.RadioStation
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.comms.RadioViewModel
import de.mm20.launcher2.ui.component.LauncherCard
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object RadioDashboardRoute : NavKey

@Composable
fun RadioDashboardScreen() {
    val viewModel: RadioDashboardScreenVM = viewModel()
    val playerViewModel: RadioViewModel = viewModel()

    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Favorites", "Search")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }

        if (selectedTabIndex == 0) {
            // Favorites Tab
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (favorites.isEmpty()) {
                    item {
                        Text(
                            text = "No favorite stations yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                items(favorites, key = { it.id }) { station ->
                    StationRow(
                        station = station,
                        isFavorite = true,
                        onFavoriteClick = { viewModel.toggleFavorite(station) },
                        onClick = { playerViewModel.playStation(station) }
                    )
                }
            }
        } else {
            // Search Tab
            Column(modifier = Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    placeholder = { Text("Search by station name...") },
                    singleLine = true,
                    leadingIcon = { Icon(painterResource(R.drawable.search_24px), contentDescription = null) },
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(painterResource(R.drawable.close_24px), contentDescription = "Clear")
                            }
                        }
                    }
                )

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(searchResults, key = { it.id }) { station ->
                        val isFavorite = favorites.any { it.id == station.id }
                        StationRow(
                            station = station,
                            isFavorite = isFavorite,
                            onFavoriteClick = { viewModel.toggleFavorite(station) },
                            onClick = { playerViewModel.playStation(station) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StationRow(
    station: RadioStation,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit
) {
    LauncherCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Favicon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (station.faviconUrl.isNotBlank()) {
                    AsyncImage(
                        model = station.faviconUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.music_note_24px),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = station.streamUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Favorite Button
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    painter = painterResource(R.drawable.star_24px),
                    contentDescription = "Favorite",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
// === TELOS_PENDING_REVIEW_END: radio_browser_ktor ===

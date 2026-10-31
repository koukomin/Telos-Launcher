package de.mm20.launcher2.ui.component
import de.mm20.launcher2.base.R as BaseR

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.mm20.launcher2.searchactions.actions.AppSearchAction
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon
import de.mm20.launcher2.searchactions.builders.AppSearchActionBuilder
import de.mm20.launcher2.searchactions.builders.CustomizableSearchActionBuilder
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SearchActionIcon(
    icon: SearchActionIcon,
    color: Int = 0,
    customIcon: String? = null,
    componentName: ComponentName? = null,
    size: Dp = 20.dp
) {
    val tint = when (color) {
        0 -> LocalContentColor.current
        1 -> Color.Unspecified
        else -> Color(color)
    }
    if (icon != SearchActionIcon.Custom || customIcon == null && componentName == null) {
        Icon(
            modifier = Modifier.size(size),
            painter = painterResource(getSearchActionIconVector(icon)),
            contentDescription = null,
            tint = tint,
        )
    } else if (customIcon == null && componentName != null) {
        val context = LocalContext.current
        var drawable by remember(componentName) { mutableStateOf<Drawable?>(null) }

        LaunchedEffect(componentName) {
            drawable = withContext(Dispatchers.IO) {
                try {
                    context.packageManager.getActivityIcon(componentName)
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }
            }
        }

        AsyncImage(
            model = drawable,
            contentDescription = null,
            modifier = Modifier.size(size),
            colorFilter = if (tint.isSpecified) ColorFilter.tint(tint) else null
        )
    } else {
        AsyncImage(
            model = customIcon,
            contentDescription = null,
            modifier = Modifier.size(size),
            colorFilter = if (tint.isSpecified) ColorFilter.tint(tint) else null
        )
    }
}

@Composable
fun SearchActionIcon(action: SearchAction, size: Dp = 20.dp) {
    SearchActionIcon(
        icon = action.icon,
        color = action.iconColor,
        customIcon = action.customIcon,
        componentName = (action as? AppSearchAction)?.baseIntent?.component,
        size = size,
    )
}

@Composable
fun SearchActionIcon(builder: CustomizableSearchActionBuilder, size: Dp = 20.dp) {
    SearchActionIcon(
        icon = builder.icon,
        color = builder.iconColor,
        customIcon = builder.customIcon,
        componentName = (builder as? AppSearchActionBuilder)?.baseIntent?.component,
        size = size,
    )
}

fun getSearchActionIconVector(icon: SearchActionIcon): Int {
    return when (icon) {
        SearchActionIcon.Phone -> BaseR.drawable.call_24px
        SearchActionIcon.Website -> BaseR.drawable.language_24px
        SearchActionIcon.Alarm -> BaseR.drawable.alarm_24px
        SearchActionIcon.Timer -> BaseR.drawable.timer_24px
        SearchActionIcon.Contact -> BaseR.drawable.person_add_24px
        SearchActionIcon.Email -> BaseR.drawable.mail_24px
        SearchActionIcon.Message -> BaseR.drawable.sms_24px
        SearchActionIcon.Calendar -> BaseR.drawable.event_24px
        SearchActionIcon.Translate -> BaseR.drawable.translate_24px
        SearchActionIcon.Search -> BaseR.drawable.search_24px
        SearchActionIcon.Custom -> BaseR.drawable.warning_24px
        SearchActionIcon.WebSearch -> BaseR.drawable.travel_explore_24px
        SearchActionIcon.PersonSearch -> BaseR.drawable.person_search_24px
        SearchActionIcon.StatsSearch -> BaseR.drawable.query_stats_24px
        SearchActionIcon.SearchPage -> BaseR.drawable.find_in_page_24px
        SearchActionIcon.SearchList -> BaseR.drawable.manage_search_24px
        SearchActionIcon.ImageSearch -> BaseR.drawable.image_search_24px
        SearchActionIcon.Location -> BaseR.drawable.location_on_24px
        SearchActionIcon.Movie -> BaseR.drawable.movie_24px
        SearchActionIcon.Music -> BaseR.drawable.music_note_24px
        SearchActionIcon.Game -> BaseR.drawable.sports_esports_24px
        SearchActionIcon.Note -> BaseR.drawable.sticky_note_2_24px
        SearchActionIcon.Share -> BaseR.drawable.share_24px
        SearchActionIcon.PrivateSpace -> BaseR.drawable.encrypted_24px
        SearchActionIcon.WorkProfile -> BaseR.drawable.enterprise_24px
    }
}
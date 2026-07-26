package de.mm20.launcher2.searchactions.builders

import android.content.Context
import de.mm20.launcher2.profiles.Profile
import de.mm20.launcher2.profiles.ProfileManager
import de.mm20.launcher2.search.ResultScore
import de.mm20.launcher2.searchactions.R
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon
import de.mm20.launcher2.searchactions.actions.WorkProfileLockAction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WorkProfileLockActionBuilder(
    override val label: String,
) : SearchActionBuilder, KoinComponent {

    private val profileManager: ProfileManager by inject()

    constructor(context: Context) : this(context.getString(R.string.search_query_work_profile))

    override val key = "work_profile"
    override val icon = SearchActionIcon.WorkProfile

    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction? {
        val workProfile = profileManager.getProfile(Profile.Type.Work) ?: return null
        val profileState = profileManager.getProfileStateOnce(workProfile) ?: return null

        val keyword = context.getString(R.string.search_query_work_profile).lowercase()
        val score = ResultScore.from(
            query = classifiedQuery.text.lowercase(),
            primaryFields = listOf(keyword),
        )
        if (score.score < 0.8f) return null

        return WorkProfileLockAction(
            label = context.getString(
                if (profileState.locked) R.string.search_action_work_profile_resume
                else R.string.search_action_work_profile_pause
            ),
            isPaused = profileState.locked,
            profile = workProfile,
            profileManager = profileManager,
        )
    }
}

package de.mm20.launcher2.licenses
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.content.Context

object AppLicense {
    fun get(context: Context): OpenSourceLibrary {
        return OpenSourceLibrary(
            name = context.getString(BaseR.string.app_name),
            description = context.getString(I18nR.string.preference_about_license),
            copyrightNote = "Copyright (C) 2021–2026 MM2-0 and the Kvaesitso contributors",
            licenseName = BaseR.string.gpl3_name,
            licenseText = BaseR.raw.license_gpl_3,
            url = "https://github.com/koukomin/Telos-Launcher"
        )
    }
}

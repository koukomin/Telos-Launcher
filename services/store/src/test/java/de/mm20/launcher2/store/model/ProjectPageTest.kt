package de.mm20.launcher2.store.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectPageTest {
    @Test fun releaseAndDownloadLinksOpenTheProject() {
        assertEquals("https://github.com/kde/kdeconnect", AppSource.DirectApk("https://github.com/kde/kdeconnect/releases/download/v1/app.apk?token=x").projectPageUrl())
        assertEquals("https://gitlab.example/group/project", AppSource.Html("https://gitlab.example/group/project/-/releases/v1").projectPageUrl())
        assertEquals("https://example.org/", AppSource.DirectApk("https://example.org/files/app.apk").projectPageUrl())
    }
    @Test fun listingsRetainTheirAppIdentity() {
        assertEquals("https://f-droid.org/packages/org.example/", AppSource.FDroid("org.example").projectPageUrl())
        assertEquals("https://example.org/apps/about", AppSource.Html("https://example.org/apps/about").projectPageUrl())
        assertEquals("https://github.com/owner/project", AppSource.GitHub("owner", "project").projectPageUrl())
    }
}

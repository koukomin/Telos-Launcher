package dev.izumi.appopsnext.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun `blank language tags follow the system`() {
        assertEquals(
            AppLanguage.SYSTEM,
            AppLanguage.fromLanguageTags(""),
        )
    }

    @Test
    fun `supported language tags map by primary locale`() {
        assertEquals(
            AppLanguage.SIMPLIFIED_CHINESE,
            AppLanguage.fromLanguageTags("zh-CN,en-US"),
        )
        assertEquals(
            AppLanguage.ENGLISH,
            AppLanguage.fromLanguageTags("en-US"),
        )
    }

    @Test
    fun `unsupported explicit locale fails back to system selection`() {
        assertEquals(
            AppLanguage.SYSTEM,
            AppLanguage.fromLanguageTags("zz-ZZ"),
        )
    }
    @org.junit.Test fun hostGreekAndTraditionalChineseArePreserved() {
        org.junit.Assert.assertEquals(AppLanguage.GREEK, AppLanguage.fromLanguageTags("el-GR"))
        org.junit.Assert.assertEquals(AppLanguage.TRADITIONAL_CHINESE, AppLanguage.fromLanguageTags("zh-TW"))
        org.junit.Assert.assertEquals(AppLanguage.TRADITIONAL_CHINESE, AppLanguage.fromLanguageTags("zh-Hant"))
    }
}

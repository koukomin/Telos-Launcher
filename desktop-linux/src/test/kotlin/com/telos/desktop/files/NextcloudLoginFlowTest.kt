package com.telos.desktop.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextcloudLoginFlowTest {
    @Test
    fun parsesLoginV2Json() {
        val start = """{"poll":{"token":"abc","endpoint":"https://cloud.example/index.php/login/v2/poll"},"login":"https://cloud.example/index.php/login/v2/flow"}"""
        assertEquals("https://cloud.example/index.php/login/v2/flow", NextcloudLoginFlow.jsonField(start, "login"))
        assertEquals("abc", NextcloudLoginFlow.jsonField(start, "token"))
        assertEquals("https://cloud.example/index.php/login/v2/poll", NextcloudLoginFlow.jsonField(start, "endpoint"))
        val poll = """{"server":"https://cloud.example","loginName":"anna","appPassword":"app-secret"}"""
        assertEquals("anna", NextcloudLoginFlow.jsonField(poll, "loginName"))
        assertEquals("app-secret", NextcloudLoginFlow.jsonField(poll, "appPassword"))
        assertNull(NextcloudLoginFlow.jsonField("{}", "login"))
        assertEquals("https://cloud.example", NextcloudLoginFlow.baseUrl("davs://cloud.example/remote.php/dav/files/a"))
        assertEquals("https://cloud.example", NextcloudLoginFlow.baseUrl("https://cloud.example/index.php/login/v2"))
    }
}

package com.example.mobileschedule.ui.importentry

import org.junit.Assert.*
import org.junit.Test

class SchoolLoginNavigationTest {
    @Test fun upgradesSchoolIdentityLinkWithoutChangingItsEncodedQuery() {
        val raw = "http://org.app.fjnu.edu.cn/openplatform/oauth3/authorize?appId=67&redirectUri=http%3A%2F%2Fjwglxt.fjnu.edu.cn%2Fsso%2FoauthLogin&state=synthetic"
        assertEquals("https://org.app.fjnu.edu.cn/openplatform/oauth3/authorize?appId=67&redirectUri=http%3A%2F%2Fjwglxt.fjnu.edu.cn%2Fsso%2FoauthLogin&state=synthetic",
            SchoolLoginNavigation.httpsUrl(raw))
    }

    @Test fun upgradesTeachingSystemCallbackAndDefaultHttpPort() {
        assertEquals("https://jwglxt.fjnu.edu.cn/sso/oauthLogin?code=synthetic%2Bcode#return",
            SchoolLoginNavigation.httpsUrl("http://jwglxt.fjnu.edu.cn:80/sso/oauthLogin?code=synthetic%2Bcode#return"))
    }

    @Test fun preservesExistingHttpsNavigation() {
        assertEquals("https://org.app.fjnu.edu.cn/openplatform/login.html",
            SchoolLoginNavigation.httpsUrl("https://org.app.fjnu.edu.cn/openplatform/login.html"))
    }

    @Test fun blocksOtherHttpHostsLocalSchemesAndMalformedUrls() {
        listOf("http://other.invalid/", "http://org.app.fjnu.edu.cn.other.invalid/",
            "http://user@org.app.fjnu.edu.cn/", "http://org.app.fjnu.edu.cn:8080/",
            "file:///sdcard/test", "content://test", "intent://test", "javascript:alert(1)",
            "http://[bad-host", "https://").forEach { assertNull(it, SchoolLoginNavigation.httpsUrl(it)) }
    }
}

package com.example.mobileschedule.ui.importentry

import java.net.URI

/** Uses HTTPS for the school's identity entry and teaching-system callback, preserving OAuth parameters. */
internal object SchoolLoginNavigation {
    private val upgradeHosts = setOf("org.app.fjnu.edu.cn", "jwglxt.fjnu.edu.cn")

    fun httpsUrl(url: String): String? = try {
        val uri = URI(url)
        when {
            uri.host.isNullOrBlank() || uri.userInfo != null -> null
            uri.scheme.equals("https", ignoreCase = true) -> url
            uri.scheme.equals("http", ignoreCase = true) &&
                uri.host.lowercase() in upgradeHosts && uri.port in setOf(-1, 80) -> buildString {
                append("https://")
                append(if (uri.port == 80) uri.rawAuthority.removeSuffix(":80") else uri.rawAuthority)
                append(uri.rawPath.orEmpty())
                uri.rawQuery?.let { append('?'); append(it) }
                uri.rawFragment?.let { append('#'); append(it) }
            }
            else -> null
        }
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: java.net.URISyntaxException) {
        null
    }
}

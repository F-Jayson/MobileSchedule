package com.example.mobileschedule.data.model

/**
 * Download mirror sources for application update packages.
 * Helps users in regions with unstable or blocked GitHub access to download updates via high-speed CDN mirrors or personal blog source.
 */
enum class DownloadMirror(
    val key: String,
    val displayName: String,
    val urlPrefix: String,
    val description: String,
) {
    BLOG(
        key = "blog",
        displayName = "个人博客源 (官方高速直链)",
        urlPrefix = "https://blog.fjayson.com",
        description = "作者个人博客专属发布源，国内高速直连，免受 GitHub 限制",
    ),
    GH_PROXY(
        key = "gh_proxy",
        displayName = "加速源 1 (gh-proxy.com)",
        urlPrefix = "https://gh-proxy.com/",
        description = "国内常用高速 CDN 镜像加速，推荐使用",
    ),
    GHPROXY_NET(
        key = "ghproxy_net",
        displayName = "加速源 2 (ghproxy.net)",
        urlPrefix = "https://ghproxy.net/",
        description = "公共镜像加速节点，备用源",
    ),
    OFFICIAL(
        key = "official",
        displayName = "官方源 (GitHub 直连)",
        urlPrefix = "",
        description = "直接连接 GitHub 官方下载服务器，需良好国际网络",
    ),
    CUSTOM(
        key = "custom",
        displayName = "自定义镜像源",
        urlPrefix = "",
        description = "自定义反向代理或镜像前缀，适合自建服务",
    );

    /**
     * Converts a raw download URL (e.g. GitHub Releases asset URL or Blog download URL) into an accelerated mirror URL.
     */
    fun buildDownloadUrl(rawUrl: String, customPrefix: String = "", blogOrigin: String = DEFAULT_BLOG_ORIGIN): String {
        if (this == BLOG) {
            if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                return rawUrl
            }
            val base = (if (customPrefix.isNotBlank()) customPrefix else blogOrigin).trim().removeSuffix("/")
            val path = if (rawUrl.startsWith("/")) rawUrl else "/$rawUrl"
            return "$base$path"
        }
        if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
            return rawUrl
        }
        val prefix = when (this) {
            CUSTOM -> customPrefix.trim()
            else -> urlPrefix.trim()
        }
        if (prefix.isBlank()) {
            return rawUrl
        }
        val normalizedPrefix = if (prefix.endsWith("/")) prefix else "$prefix/"
        return "$normalizedPrefix$rawUrl"
    }

    companion object {
        const val DEFAULT_BLOG_ORIGIN = "https://blog.fjayson.com"
        val DEFAULT = BLOG

        fun fromKey(key: String?): DownloadMirror {
            return entries.firstOrNull { it.key == key } ?: DEFAULT
        }
    }
}

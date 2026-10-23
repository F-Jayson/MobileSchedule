package com.example.mobileschedule.ui.about

import com.example.mobileschedule.BuildConfig
import com.example.mobileschedule.data.model.AppUpdateInfo
import com.example.mobileschedule.data.model.VersionComparator

/**
 * Represents a single version release changelog item.
 */
data class ChangelogEntry(
    val versionName: String,
    val releaseDate: String,
    val releaseTitle: String,
    val highlights: List<String>,
    val fullNotes: String,
    val isRemoteNew: Boolean = false,
    val remoteUpdateInfo: AppUpdateInfo? = null,
)

/**
 * Repository providing bundled historical changelog entries and merging them with remote updates.
 */
object ChangelogRepository {

    /**
     * Parse raw release notes (markdown or bullet list) into clean highlight bullet strings.
     */
    fun parseHighlights(rawNotes: String): List<String> {
        if (rawNotes.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        var inCodeBlock = false
        for (rawLine in rawNotes.lines()) {
            val line = rawLine.trim()
            if (line.startsWith("```")) {
                inCodeBlock = !inCodeBlock
                continue
            }
            if (inCodeBlock || line.isBlank()) continue
            if (line.startsWith("#") || line.startsWith(">") || line.startsWith("---")) continue

            val cleaned = line.removePrefix("• ")
                .removePrefix("- ")
                .removePrefix("* ")
                .replace(Regex("""^\d+\.\s*"""), "")
                .trim()
            if (cleaned.isNotBlank()) {
                result.add(cleaned)
            }
        }
        return result
    }

    /**
     * Bundled immutable historical changelog up to the current release.
     */
    val bundledChangelogs: List<ChangelogEntry> = listOf(
        ChangelogEntry(
            versionName = "1.5.1",
            releaseDate = "2026-10-23",
            releaseTitle = "课表背景壁纸功能全链路修复与体验优化",
            highlights = listOf(
                "课表背景壁纸稳定性修复：修复相册超大图导入时 OOM 与异常崩溃，引入单次流式读取与智能等比安全采样",
                "壁纸更新实时生效：引入版本戳驱动与 StateFlow 全局响应式流，彻底解决更换壁纸后课表界面不刷新的缓存死锁问题",
                "照片旋转自动纠正：引入原生 ExifInterface 读取拍照方向属性，自动纠偏 90°/180°/270° 旋转，竖屏照片不再倒置",
                "全屏沉浸式毛玻璃视觉：开启壁纸后学期表头与周次切换栏自动适配为半透明磨砂质感卡片，消除实色遮挡割裂感",
                "设置界面交互增强：增加选图处理中状态指示、按钮防抖禁用态与操作反馈 Toast 提示，初次开启智能推荐 65% 透明度",
            ),
            fullNotes = """
                ### 课表背景壁纸健壮性修复
                - 彻底解决从系统相册选取高分辨率照片（如 1200 万/4800 万像素实拍照）时由于二次打开 InputStream 导致的权限失效与原图解码分配超大内存触发的 OutOfMemoryError。
                - 增加采样缩放算法与单次流沙箱临时拷贝机制，壁纸选取与保存稳定性达到 100%。
                - 引入原生 EXIF 方向解析器，自动纠正照片旋转角度，避免竖屏拍摄的壁纸在课表上倒立或横向拉伸。

                ### 状态响应式分发与缓存死锁破解
                - 为壁纸配置增设毫秒级版本时间戳（wallpaperUpdatedAt），破除因文件路径字符串固定导致 Compose mutableStateOf 判定未变而不重组的死锁缺陷。
                - 界面 remember 机制同步升级为联合键索引，更换新壁纸或恢复默认无壁纸即刻无感重绘生效。
                - 在设置持久化存储层引入全局 StateFlow 响应式广播，课表界面双重保底秒级感知最新显示选项。

                ### 界面质感与操作体验优化
                - 课表顶部学期卡片与底部周次切换控制条在开启壁纸时自适应调整为带微高光边框的半透明磨砂毛玻璃材质，整屏沉浸感大幅增强。
                - 课表显示设置界面增加异步处理进度状态、禁用态与 Toast 提示，初次开启自动推荐通透的 65% 透明度，毛玻璃效果立竿见影。
            """.trimIndent(),
        ),
        ChangelogEntry(
            versionName = "1.5.0",
            releaseDate = "2026-10-20",
            releaseTitle = "大屏自适应、多规格小组件、壁纸定制与体验优化",
            highlights = listOf(
                "大屏与折叠屏自适应：平板与折叠屏展开态支持双栏课表与侧边导航轨（Navigation Rail）",
                "多规格桌面小部件：新增 2×2 极简当前课程与 4×4 全周微缩网格小部件，支持个性化主题与今天高亮",
                "自定义课表壁纸：支持相册壁纸导入、卡片不透明度平滑调节与动态 WCAG 高对比度文字算法",
                "课程任务与提醒联动：支持作业/考试/实验等待办关联，支持系统日历双向同步与课程前闹钟提醒",
                "上课免打扰与状态胶囊：上课期间自动开启免打扰，通知栏实时展示进行中课程常驻胶囊",
                "全链路强制更新支持：接入服务端版本强制升级标记，弹窗拦截跳过与退出，保障核心更新覆盖",
                "WebDAV 云端备份：支持第三方网盘与 NAS 自动/手动无损备份与多端课表恢复",
                "教务登录输入体验优化：修复登录页面软键盘唤起与连续键入时的视图抖动缺陷，输入平稳流畅",
            ),
            fullNotes = """
                ### 大屏与多端响应式体验
                - 针对平板与折叠屏大屏展开态（宽度 ≥ 840dp），自动呈现双栏自适应课表（左侧今日时间轴与快捷入口 + 右侧全周网格）。
                - 大屏下底部导航自动收折为左侧垂直导航轨（Navigation Rail），充分释放横向阅读空间。

                ### 桌面小组件与个性化壁纸
                - 新增 2×2 极简聚焦小部件，实时呈现进行中课程、下一节预告与明日首节卡片。
                - 新增 4×4 全周微缩课表部件，支持 7 列 5 时段色块网格纵览整周排课与当天高亮。
                - 课表显示设置支持从相册选择个性化背景壁纸，结合半透明卡片与毛玻璃视觉。
                - 内置动态 WCAG 文字对比度计算算法，无论暗色还是亮色壁纸，课表字迹均清晰易读。

                ### 日程同步与上课体验
                - 支持将课程日程无缝同步至系统日历，课前支持独立通知与系统闹钟精准提醒。
                - 上课自动免打扰模式与通知栏常驻课程状态胶囊，专注课堂不被打扰。
                - 课程关联待办清单：支持作业、考试、实验、报告等多类型任务追踪。

                ### 强制更新支持与系统维护
                - 客户端全面对接服务端重要发布标记，支持接收并识别 `forceUpdate` 属性。
                - 触发强制更新时弹窗展示高优先级警示标语，禁用外部点击与返回键退出，隐藏“稍后再说”和“稍后安装”按钮，确保关键功能升级即时就绪。

                ### 云端备份与教务登录修复
                - 支持 WebDAV 云端备份与恢复，跨设备数据迁移轻松安全。
                - 彻底修复教务系统登录 WebView 在软键盘唤起与连续键入时的异常上下抖动问题。
            """.trimIndent(),
        ),
        ChangelogEntry(
            versionName = "1.2.0",
            releaseDate = "2026-10-15",
            releaseTitle = "课表功能扩展、桌面小组件与外观主题",
            highlights = listOf(
                "响应式今日课表：顶部动态展示公历与周次，实时计算已下课、进行中与即将开始状态",
                "4×2 桌面小组件：无需启动 App 即可在桌面直接查看今日待上课程及明日预告",
                "课表高级视图：支持隐藏周末五天排版、周日起始日历排布以及显示课程全名开关",
                "独立外观主题：支持跟随系统、浅色模式与深色模式持久化无缝切换",
                "关于与开源合规：内置依赖许可证搜索浏览、设备运行环境信息与隐私离线声明",
                "交互细节精修：平滑手势切周、空周居中提示、复制课程表单触底优化与已下课卡片阴影重构",
            ),
            fullNotes = """
                ### 课表显示与高级排版
                - 支持在设置中开启“显示课程全名”，长课程名不再被强制省略号截断。
                - 优化周课表空周展示，无课提示居中卡片呈现，精简右下角多余悬浮加号按钮。
                - 支持“隐藏周末（仅显示工作日五天）”与“周日为第一列”高级日历排版视图。
                - 手势横向切周重构，实现同帧原子状态置换，消除切周过渡视觉闪烁。

                ### 今日课表与桌面小组件
                - 重构今日课表（Today）响应式时间流，高亮正在进行的课程，展示下课倒计时。
                - 新增 4×2 标准规格桌面小组件，随系统日期与应用课表实时联动刷新。
                - 优化复制为手动课程弹窗滑动触底平稳度，消除嵌套滚动死循环。
                - 优化已下课课程卡片透明度与硬件加速阴影渲染，消除边缘暗环。

                ### 外观主题与合规
                - 设置中增加“外观主题”分组，支持系统默认、浅色、深色模式三选一即时生效。
                - 新增“关于与帮助”页面，展示系统运行环境（Android 版本、芯片 ABI）及开源合规声明。
                - 页面顶部统一状态栏安全区基线，消除多层嵌套造成的额外边距。
            """.trimIndent(),
        ),
        ChangelogEntry(
            versionName = "1.1.0",
            releaseDate = "2026-10-13",
            releaseTitle = "Material 3 视觉重构与在线更新分发",
            highlights = listOf(
                "Material 3 现代界面重构：8 组莫兰迪专属色板，纯色极简卡片与平滑切周过渡动效",
                "结构化课程详情抽屉：28dp 大圆角底栏抽屉，按基本信息、时间地点、周次与来源分块呈现",
                "在线更新与流式下载：支持自动检查更新、断点续传、实时进度回显与原生安装器拉起",
                "芯片架构自适应与多源加速：自动匹配 ARM64/x86_64 专版，支持 GitHub/加速镜像/博客多源切换",
            ),
            fullNotes = """
                ### 界面与视觉体验升级
                - Material 3 现代设计重构：对标最新 Material 3 设计规范，重塑应用整体色调体系与层级表现。
                - 莫兰迪专属课程色板：设计 8 组高对比、柔和通透的莫兰迪调色板，根据课程名称哈希稳定映射。
                - 纯色极简课程卡片：去除传统竖条遮挡，采用纯色背景平铺填充，提升排版信息密度。
                - 平滑切周过渡动效：网格引入 AnimatedContent 动效曲线，随滑动方向自适应横向位移。
                - 结构化课程详情抽屉：采用 28dp 大圆角抽屉分块展示课程详细信息。

                ### 在线检查更新与分发体系
                - 在设置页提供“检查新版本”入口，支持安装包体积与更新说明格式化展示。
                - 后台流式下载器：支持大文件分片流式下载、断点安全写入与重定向跟踪。
                - 原生包安装器调起：基于 FileProvider 安全生成 APK 内容 URI，一键唤起系统安装器。
                - 芯片架构（ABI）智能匹配：遵循 ARM64-v8a -> x86_64 -> Universal 调度算法下载专版。
                - 多镜像加速：内置 GitHub 官方直连、加速镜像与个人博客私有高速源切换。
            """.trimIndent(),
        ),
        ChangelogEntry(
            versionName = "1.0.0",
            releaseDate = "2026-09-27",
            releaseTitle = "首发版本：正方教务导入与离线课表",
            highlights = listOf(
                "福建师范大学正方教务系统适配：支持安全登录导入、冲突预检与离散教学周次精确解析",
                "全屏周课表网格：自适应标准 7 天 8 节排版，跨节合并与毫秒级离线冷启动",
                "学期周期管理：自定义开学日期、总教学周数及当前周次智能推算，支持深浅色模式跟随",
            ),
            fullNotes = """
                ### 正方教务系统适配（福建师范大学）
                - 教务系统在线导入：专属适配福建师范大学正方教务系统（zftal 架构），支持学号安全登录与课表解析。
                - 智能预检与冲突处理：导入前提供完整课程信息预览，智能识别时间重叠与课程冲突。
                - 离散周次精确映射：支持单双周、跳周、起止周次等复杂离散教学周次解析。
                - 批次事务化存储：支持增量同步与全量覆盖重导，确保本地数据一致性。

                ### 课表核心视图与设置
                - 全屏周课表网格：自适应标准 7 天 8 节课表网格，跨节次课程自动合并呈现。
                - 离线优先架构：基于 Room 数据库本地持久化存储，无需联网即可毫秒级冷启动。
                - 学期周期管理：支持自定义学期开学日期、总教学周数及当前周次推算。
                - 系统主题跟随：支持系统深色模式与浅色模式无缝自适应切换。
            """.trimIndent(),
        ),
    )

    /**
     * Merges bundled historical changelogs with remote update information.
     * If [remoteInfo] represents a strictly newer version than current app,
     * prepends a remote [ChangelogEntry] with [ChangelogEntry.isRemoteNew] set to true.
     */
    fun mergeWithRemoteUpdate(
        remoteInfo: AppUpdateInfo?,
        currentVersion: String = BuildConfig.VERSION_NAME,
    ): List<ChangelogEntry> {
        if (remoteInfo == null) return bundledChangelogs

        val hasNewVersion = remoteInfo.hasUpdate || VersionComparator.isNewer(remoteInfo.latestVersionName, currentVersion)
        if (!hasNewVersion) return bundledChangelogs

        val parsedHighlights = parseHighlights(remoteInfo.releaseNotes)
        val highlights = if (parsedHighlights.isNotEmpty()) {
            parsedHighlights.take(6)
        } else {
            listOf(remoteInfo.releaseNotes.take(120).trim())
        }

        val remoteEntry = ChangelogEntry(
            versionName = remoteInfo.latestVersionName,
            releaseDate = remoteInfo.publishedAt ?: "最新云端版本",
            releaseTitle = remoteInfo.releaseTitle.ifBlank { "v${remoteInfo.latestVersionName} 新版本发布" },
            highlights = highlights,
            fullNotes = remoteInfo.releaseNotes,
            isRemoteNew = true,
            remoteUpdateInfo = remoteInfo,
        )

        // Filter out any bundled entry that might have the same version name to avoid duplicates
        val filteredBundled = bundledChangelogs.filter { it.versionName != remoteEntry.versionName }
        return listOf(remoteEntry) + filteredBundled
    }
}

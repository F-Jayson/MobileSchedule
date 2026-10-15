package com.example.mobileschedule.ui.about

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobileschedule.BuildConfig
import com.example.mobileschedule.R

@Composable
fun AboutRoute(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenChangelog: () -> Unit = {},
) {
    AboutScreen(
        onBack = onBack,
        onOpenLicenses = onOpenLicenses,
        onOpenChangelog = onOpenChangelog,
    )
}

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenChangelog: () -> Unit = {},
) {
    var showPrivacyDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("about_page"),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AboutTopBar(onBack = onBack)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // App Brand Hero Header
            AppBrandHeader()

            // Environment Info Card
            EnvironmentInfoCard()

            // Feature and Compliance Entries
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "合规与开源",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )

                AboutActionEntry(
                    title = "更新日志（Changelog）",
                    description = "查看应用版本迭代历史与最新功能特性",
                    tag = "about_changelog_entry",
                    onClick = onOpenChangelog,
                )

                AboutActionEntry(
                    title = "开源许可证（Open Source Licenses）",
                    description = "查看本项目依赖的全部开源组件、版本与授权协议",
                    tag = "about_licenses_entry",
                    onClick = onOpenLicenses,
                )

                AboutActionEntry(
                    title = "免责声明与隐私保护",
                    description = "课表纯本地离线存储，账号密码隔离不上传",
                    tag = "about_privacy_entry",
                    onClick = { showPrivacyDialog = true },
                )
            }

            Spacer(Modifier.height(16.dp))

            // Footer Copyright
            Text(
                text = "MobileSchedule · 福建师范大学课程表\n基于 Apache-2.0 协议开源构建",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }

    if (showPrivacyDialog) {
        PrivacyDisclaimerDialog(onDismiss = { showPrivacyDialog = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text("关于与帮助", fontWeight = FontWeight.Bold)
        },
        windowInsets = WindowInsets(0),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
        ),
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("about_back"),
            ) {
                Text("‹", fontSize = 28.sp, fontWeight = FontWeight.Light)
            }
        },
    )
}

@Composable
private fun AppBrandHeader() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appIcon = remember(context) {
        try {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            val bitmap = if (drawable is android.graphics.drawable.BitmapDrawable) {
                drawable.bitmap
            } else {
                val bmp = android.graphics.Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    android.graphics.Bitmap.Config.ARGB_8888,
                )
                val canvas = android.graphics.Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }
            bitmap.asImageBitmap()
        } catch (_: Throwable) {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp),
            shadowElevation = 2.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = "应用图标",
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_schedule),
                        contentDescription = "应用图标",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
        }

        Text(
            text = "福师大课程表",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "MobileSchedule for FNUBBS / FJNU",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = "版本 v${BuildConfig.VERSION_NAME} (构建号 ${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.testTag("about_version").padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun EnvironmentInfoCard() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("about_environment_info"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "当前运行环境",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )

            EnvironmentItemRow(
                label = "Android 系统版本",
                value = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                tag = "env_android_version",
            )

            EnvironmentItemRow(
                label = "设备型号",
                value = "${Build.MANUFACTURER} ${Build.MODEL}",
                tag = "env_device_model",
            )

            EnvironmentItemRow(
                label = "硬件架构 ABI",
                value = Build.SUPPORTED_ABIS.firstOrNull() ?: "未知架构",
                tag = "env_abi",
            )
        }
    }
}

@Composable
private fun EnvironmentItemRow(label: String, value: String, tag: String) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AboutActionEntry(
    title: String,
    description: String,
    tag: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "›",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun PrivacyDisclaimerDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("privacy_dialog"),
        title = {
            Text("免责声明与隐私保护", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PrivacyClauseItem(
                    title = "1. 纯本地离线存储",
                    content = "本应用为本地离线课表工具，所有学期日历配置、作息时间与课程排课数据均存储在当前设备的本地 SQLite (Room) 数据库中，不设云端存储，绝不回传课表数据。",
                )
                PrivacyClauseItem(
                    title = "2. 账号与登录凭证安全",
                    content = "在执行教务系统课表导入时，所有登录与交互均在 Android 系统标准 WebView 隔离沙箱中与福建师范大学正方教务系统官方站点直连。本应用绝不拦截、保存明文密码或将凭证发送给任何第三方。",
                )
                PrivacyClauseItem(
                    title = "3. 无广告与无追踪",
                    content = "本软件完全开源且无商业推广行为，不包含任何第三方广告 SDK、埋点统计或网络行为追踪组件，充分尊重并捍卫高校师生的数字隐私安全。",
                )
                PrivacyClauseItem(
                    title = "4. 非官方开源产品免责",
                    content = "本软件为开源课表工具，非福建师范大学官方或正方软件公司出品。教务数据展示仅供个人学习作息参考，请以学校官方教务系统公布的排课通知为准。",
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("privacy_dialog_confirm"),
            ) {
                Text("我已知悉")
            }
        },
    )
}

@Composable
private fun PrivacyClauseItem(title: String, content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
        )
    }
}

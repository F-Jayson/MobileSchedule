<p align="center">
  <img src="artwork/app-icon-original.png" width="168" alt="Mobile Schedule 应用图标" />
</p>


<h1 align="center">Mobile Schedule</h1>

<p align="center">大学教务课表的安卓原生客户端</p>

---

<p align="center">读取本学期课程，在本地按周查看、切换与核对。</p>

<p align="center">
  <img alt="作者" src="https://img.shields.io/badge/author-F--Jayson-4C6EF5" />
  <img alt="许可" src="https://img.shields.io/badge/license-MIT-22C55E" />
  <img alt="版本" src="https://img.shields.io/badge/version-1.0.0-2F6FED" />
  <img alt="平台" src="https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white" />
  <img alt="语言" src="https://img.shields.io/badge/Kotlin-2.2.21-7F52FF?logo=kotlin&logoColor=white" />
  <img alt="界面" src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white" />
  <a href="https://github.com/F-Jayson/MobileSchedule/actions/workflows/android.yml"><img alt="构建" src="https://img.shields.io/github/actions/workflow/status/F-Jayson/MobileSchedule/android.yml?branch=main&label=build" /></a>
  <a href="https://github.com/F-Jayson/MobileSchedule/stargazers"><img alt="Stars" src="https://img.shields.io/github/stars/F-Jayson/MobileSchedule?style=flat&label=Stars" /></a>
</p>


## 简介

Mobile Schedule（课程表）是面向大学正方教务系统的单模块 Android 应用。用户在学校页面完成本人登录后，应用读取指定学期的课表，经校验与预览确认后写入本地数据库，并以一周七天的网格呈现。

当前版本为 **1.0.0**（`versionCode` 4）。应用标识为 `com.example.mobileschedule`，最低支持 Android 8.0（API 26），编译与目标 SDK 为 36。

课程数据保存在本机。登录发生在学校页面内，应用不保存教务账号和密码。确认保存时，按同一来源学期整批替换既有安排；取消、解析失败或校验不通过时，不改动已保存的课程。

## 功能

- **周课表。** 按活动学期与浏览周展示周一至周日、节次时间与跨节课程。支持上一周、下一周、周次选择、左右滑动，以及回到当前周。
- **课程详情。** 点开课程可查看名称、教师、地点、星期、节次与上课周次；同一时段有多门课程时分别进入。
- **显示设置。** 周课表卡片可单独隐藏教室或教师。课程名称始终显示，详情中仍保留完整信息。
- **学期配置。** 可新建、编辑并切换当前学期，设置第 1 周周一、总周数、节次数，以及每节的起止时间。未完成必要配置时，课表给出明确空状态，不生成示例课程。
- **教务导入。** 面向大学正方教务。用户选择已配置的学期，在学校页面登录并读取课表；预览列出来源、安排、计数与不能保存的原因，再次确认后写入本地。
- **本地持久化。** 使用 Room 保存学期、节次、导入批次与课程周次。关闭并重新打开应用后，已保存的课表仍然可读。

## 运行环境

| 项目           | 要求                                                         |
| -------------- | ------------------------------------------------------------ |
| 操作系统       | 可运行 Android Studio 与 JDK 21 的开发环境；下文命令以 Windows PowerShell 为例 |
| JDK            | 21。Java 与 Kotlin 字节码目标为 17                           |
| Android SDK    | Platform 36，Build Tools 35.0.0                              |
| Android Studio | 可直接打开本仓库并完成 Gradle 同步                           |
| 运行设备       | Android 8.0 及以上的真机或模拟器                             |

依赖版本集中在 `gradle/libs.versions.toml`。当前组合为 Android Gradle Plugin 8.13.2、Gradle 8.14.5、Kotlin 2.2.21、Jetpack Compose BOM 2025.12.00、Hilt 2.57.2、Room 2.8.4。

## 构建与安装

1. 使用 Android Studio 打开仓库根目录，将 Gradle JDK 设为 JDK 21。
2. 在 SDK Manager 中安装 Android SDK Platform 36 与 Build Tools 35.0.0。
3. 在本机 `local.properties` 中写入自己的 SDK 路径。该文件不进入版本库。

```properties
sdk.dir=C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk
```

4. 选择 Android 8.0 及以上设备，运行 `app`。

命令行验证与打包：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

已连接并授权的模拟器或真机可额外执行：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

首次构建需要网络。项目使用 Gradle Wrapper，不要求另行安装全局 Gradle。

Debug 包按 CPU 架构分别生成，位于 `app/build/outputs/apk/debug/`：

| 文件                                          | 适用架构           |
| --------------------------------------------- | ------------------ |
| `mobileschedule-v1.0.0-arm64-v8a-debug.apk`   | 当前主流 64 位真机 |
| `mobileschedule-v1.0.0-armeabi-v7a-debug.apk` | 32 位 ARM 设备     |
| `mobileschedule-v1.0.0-x86_64-debug.apk`      | 多数 x86_64 模拟器 |

安装时选择与设备架构一致的文件。这些是调试包，未开启代码压缩。

持续集成定义在 `.github/workflows/android.yml`。向仓库推送或发起拉取请求时，会在 Ubuntu 上安装对应 SDK，并执行单元测试、Lint 与 Debug 打包。

## 使用说明

1. 打开应用。若尚无活动学期，按提示进入设置，新建本地学期。
2. 填写第 1 周的周一、总周数和节次数。节次时间可以稍后补充；未单独配置时，课表对第 1 至 8 节使用默认时间。
3. 将该学期设为当前学期。
4. 从课表或设置进入「导入课程表」，在学校页面完成本人登录，核对预览中的来源学期与课程条数。
5. 确认保存后返回周课表。可用周次控制或左右滑动查看其他教学周，点按课程查看详情。
6. 如需调整卡片上的教室或教师，进入设置中的「课表设置」。

重新导入同一来源学期并确认保存后，该范围的旧安排会被本次结果替换。请在预览页核对条数与课程内容后再确认。

## 技术架构

界面使用 Kotlin 与 Jetpack Compose，导航分为课表与设置两个主入口，导入、学期表单和显示设置作为独立页面进入。依赖注入使用 Hilt。界面状态由 ViewModel 暴露，Compose 不直接访问数据库。

```text
学校页面登录与读取
        │
        ▼
正方课表解析、校验与预览
        │
        ▼
用户确认后的导入事务
        │
        ▼
Room（学期、节次、批次、课程与离散周次）
        │
        ▼
Repository Flow → ViewModel → 周课表
```

周次以离散集合保存，不以连续的起止周代替任意周次。数据库结构导出在 `app/schemas/`，版本升级通过迁移完成，不使用破坏性回退删除已有课程。

## 目录结构

```text
MobileSchedule/
├── app/
│   ├── build.gradle.kts                 应用版本、SDK 与 ABI 分包
│   ├── schemas/                         Room 结构导出
│   └── src/
│       ├── main/java/com/example/mobileschedule/
│       │   ├── data/                    模型、规则、导入、Room 与仓库
│       │   ├── di/                      Hilt 模块
│       │   └── ui/                      课表、设置、导入与主题
│       ├── test/                        JVM 单元测试
│       └── androidTest/                 设备与数据库测试
├── artwork/app-icon-original.png        应用图标
├── gradle/                              版本目录与 Wrapper
├── .github/workflows/android.yml        构建、测试与 Lint
├── docs/                                设计、环境、测试与过程记录
├── CONTRIBUTING.md                      协作约定
└── README.md
```

## 验证

本地完成一项改动后，至少执行：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

涉及界面、数据库迁移或导入保存时，再在已连接设备上执行 `connectedDebugAndroidTest`。测试范围、历史结果与已知限制见 [测试与验证](docs/testing.md)。

## 相关文档

| 文档                              | 内容                           |
| --------------------------------- | ------------------------------ |
| [文档索引](docs/README.md)        | 设计、过程与验证资料的阅读顺序 |
| [环境与构建](docs/environment.md) | JDK、SDK、镜像与本机构建差异   |
| [架构说明](docs/architecture.md)  | 模块边界与数据流               |
| [界面设计](docs/ui-design.md)     | 页面、状态与交互               |
| [测试与验证](docs/testing.md)     | 自动测试与人工核对记录         |
| [协作规范](CONTRIBUTING.md)       | 分支、评审与提交要求           |

## 许可

本项目以 MIT 协议授权。你可以自由使用、复制、修改、合并、发布、分发和再授权本软件，但须在副本中保留原版权声明与本许可文本。软件按「原样」提供，作者不承担任何明示或默示担保。

完整文本见仓库根目录的 [LICENSE](LICENSE)。

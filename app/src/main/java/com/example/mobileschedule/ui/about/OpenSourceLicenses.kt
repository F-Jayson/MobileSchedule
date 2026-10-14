package com.example.mobileschedule.ui.about

data class OpenSourceLibrary(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val license: String,
    val url: String,
    val description: String,
    val licenseContent: String = APACHE_2_0_LICENSE,
)

object OpenSourceLicenseRepository {

    val libraries: List<OpenSourceLibrary> = listOf(
        OpenSourceLibrary(
            id = "jetpack-compose",
            name = "Android Jetpack Compose",
            version = "BOM 2025.12.00 (UI 1.8.0, Material3 1.3.1)",
            author = "Google LLC / The Android Open Source Project",
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack/compose",
            description = "Android 官方用于构建原生界面的新一代声明式 UI 工具包。",
            licenseContent = APACHE_2_0_LICENSE,
        ),
        OpenSourceLibrary(
            id = "room",
            name = "AndroidX Room",
            version = "2.8.4",
            author = "Google LLC / The Android Open Source Project",
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/room",
            description = "Android Jetpack 官方 SQLite 对象映射库，提供流畅的本地数据库访问体验。",
            licenseContent = APACHE_2_0_LICENSE,
        ),
        OpenSourceLibrary(
            id = "dagger-hilt",
            name = "Google Dagger Hilt",
            version = "2.57.2",
            author = "Google LLC",
            license = "Apache-2.0",
            url = "https://dagger.dev/hilt/",
            description = "专为 Android 设计的依赖注入标准库，简化架构解耦与组件生命周期绑定。",
            licenseContent = APACHE_2_0_LICENSE,
        ),
        OpenSourceLibrary(
            id = "navigation-compose",
            name = "AndroidX Navigation Compose",
            version = "2.9.6",
            author = "Google LLC / The Android Open Source Project",
            license = "Apache-2.0",
            url = "https://developer.android.com/guide/navigation",
            description = "支持在 Jetpack Compose 应用中构建单 Activity 多屏幕导航与页面转场动画。",
            licenseContent = APACHE_2_0_LICENSE,
        ),
        OpenSourceLibrary(
            id = "workmanager",
            name = "AndroidX WorkManager",
            version = "2.11.2",
            author = "Google LLC / The Android Open Source Project",
            license = "Apache-2.0",
            url = "https://developer.android.com/topic/libraries/architecture/workmanager",
            description = "推荐用于安排即使应用退出或设备重启也能可靠运行的可延期后台任务的官方库。",
            licenseContent = APACHE_2_0_LICENSE,
        ),
        OpenSourceLibrary(
            id = "core-ktx",
            name = "AndroidX Core KTX",
            version = "1.17.0",
            author = "Google LLC / The Android Open Source Project",
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/core",
            description = "为 Android 平台核心框架 API 提供简洁的 Kotlin 语言扩展支持。",
            licenseContent = APACHE_2_0_LICENSE,
        ),
        OpenSourceLibrary(
            id = "activity-compose",
            name = "AndroidX Activity Compose",
            version = "1.12.0",
            author = "Google LLC / The Android Open Source Project",
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/activity",
            description = "桥接 ComponentActivity 与 Compose 视图层级，提供 BackHandler 与内容设置入口。",
            licenseContent = APACHE_2_0_LICENSE,
        ),
        OpenSourceLibrary(
            id = "lifecycle",
            name = "AndroidX Lifecycle (ViewModel & Compose)",
            version = "2.9.4",
            author = "Google LLC / The Android Open Source Project",
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/lifecycle",
            description = "感知生命周期并在配置变更时保留状态的 Android 架构组件库。",
            licenseContent = APACHE_2_0_LICENSE,
        ),
        OpenSourceLibrary(
            id = "hilt-navigation-compose",
            name = "AndroidX Hilt Navigation Compose",
            version = "1.3.0",
            author = "Google LLC / The Android Open Source Project",
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/hilt",
            description = "将 Hilt ViewModel 与 Navigation Compose 回退栈条目无缝集成的扩展库。",
            licenseContent = APACHE_2_0_LICENSE,
        ),
        OpenSourceLibrary(
            id = "kotlinx-coroutines",
            name = "Kotlinx Coroutines",
            version = "1.10.2",
            author = "JetBrains s.r.o.",
            license = "Apache-2.0",
            url = "https://github.com/Kotlin/kotlinx.coroutines",
            description = "Kotlin 官方协程支持库，用于高效、轻量地进行异步流处理与并发编程。",
            licenseContent = APACHE_2_0_LICENSE,
        ),
        OpenSourceLibrary(
            id = "kotlinx-serialization",
            name = "Kotlinx Serialization",
            version = "1.8.1",
            author = "JetBrains s.r.o.",
            license = "Apache-2.0",
            url = "https://github.com/Kotlin/kotlinx.serialization",
            description = "无反射、跨平台的 Kotlin 官方多格式 JSON 序列化框架。",
            licenseContent = APACHE_2_0_LICENSE,
        ),
        OpenSourceLibrary(
            id = "kotlin-stdlib",
            name = "Kotlin Standard Library",
            version = "2.2.21",
            author = "JetBrains s.r.o.",
            license = "Apache-2.0",
            url = "https://kotlinlang.org/",
            description = "Kotlin 现代多范式编程语言核心标准库与运行时支持。",
            licenseContent = APACHE_2_0_LICENSE,
        ),
    )

    fun filter(query: String): List<OpenSourceLibrary> {
        if (query.isBlank()) return libraries
        val trimmed = query.trim().lowercase()
        return libraries.filter {
            it.name.lowercase().contains(trimmed) ||
                it.author.lowercase().contains(trimmed) ||
                it.license.lowercase().contains(trimmed) ||
                it.description.lowercase().contains(trimmed)
        }
    }
}

val APACHE_2_0_LICENSE = """
                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

   1. Definitions.

      "License" shall mean the terms and conditions for use, reproduction,
      and distribution as defined by Sections 1 through 9 of this document.

      "Licensor" shall mean the copyright owner or entity authorized by
      the copyright owner that is granting the License.

      "Legal Entity" shall mean the union of the acting entity and all
      other entities that control, are controlled by, or are under common
      control with that entity. For the purposes of this definition,
      "control" means (i) the power, direct or indirect, to cause the
      direction or management of such entity, whether by contract or
      otherwise, or (ii) ownership of fifty percent (50%) or more of the
      outstanding shares, or (iii) beneficial ownership of such entity.

      "You" (or "Your") shall mean an individual or Legal Entity
      exercising permissions granted by this License.

      "Source" form shall mean the preferred form for making modifications,
      including but not limited to software source code, documentation
      source, and configuration files.

      "Object" form shall mean any form resulting from mechanical
      transformation or translation of a Source form, including but
      not limited to compiled object code, generated documentation,
      and conversions to other media types.

      "Work" shall mean the work of authorship, whether in Source or
      Object form, made available under the License, as indicated by a
      copyright notice that is included in or attached to the work
      (an example is provided in the Appendix below).

      "Derivative Works" shall mean any work, whether in Source or Object
      form, that is based on (or derived from) the Work and for which the
      editorial revisions, annotations, elaborations, or other modifications
      represent, as a whole, an original work of authorship. For the purposes
      of this License, Derivative Works shall not include works that remain
      separable from, or merely link (or bind by name) to the interfaces of,
      the Work and Derivative Works thereof.

      "Contribution" shall mean any work of authorship, including
      the original version of the Work and any modifications or additions
      to that Work or Derivative Works thereof, that is intentionally
      submitted to Licensor for inclusion in the Work by the copyright owner
      or by an individual or Legal Entity authorized to submit on behalf of
      the copyright owner. For the purposes of this definition, "submitted"
      means any form of electronic, verbal, or written communication sent
      to the Licensor or its representatives, including but not limited to
      communication on electronic mailing lists, source code control systems,
      and issue tracking systems that are managed by, or on behalf of, the
      Licensor for the purpose of discussing and improving the Work, but
      excluding communication that is conspicuously marked or otherwise
      designated in writing by the copyright owner as "Not a Contribution."

      "Contributor" shall mean Licensor and any individual or Legal Entity
      on behalf of whom a Contribution has been received by Licensor and
      subsequently incorporated within the Work.

   2. Grant of Copyright License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      copyright license to reproduce, prepare Derivative Works of,
      publicly display, publicly perform, sublicense, and distribute the
      Work and such Derivative Works in Source or Object form.

   3. Grant of Patent License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      (except as stated in this section) patent license to make, have made,
      use, offer to sell, sell, import, and otherwise transfer the Work,
      where such license applies only to those patent claims licensable
      by such Contributor that are necessarily infringed by their
      Contribution(s) alone or by combination of their Contribution(s)
      with the Work to which such Contribution(s) was submitted. If You
      institute patent litigation against any entity (including a
      cross-claim or counterclaim in a lawsuit) alleging that the Work
      or a Contribution incorporated within the Work constitutes direct
      or contributory patent infringement, then any patent licenses
      granted to You under this License for that Work shall terminate
      as of the date such litigation is filed.

   4. Redistribution. You may reproduce and distribute copies of the
      Work or Derivative Works thereof in any medium, with or without
      modifications, and in Source or Object form, provided that You
      meet the following conditions:

      (a) You must give any other recipients of the Work or
          Derivative Works a copy of this License; and

      (b) You must cause any modified files to carry prominent notices
          stating that You changed the files; and

      (c) You must retain, in the Source form of any Derivative Works
          that You distribute, all copyright, patent, trademark, and
          attribution notices from the Source form of the Work,
          excluding those notices that do not pertain to any part of
          the Derivative Works; and

      (d) If the Work includes a "NOTICE" text file as part of its
          distribution, then any Derivative Works that You distribute must
          include a readable copy of the attribution notices contained
          within such NOTICE file, excluding those notices that do not
          pertain to any part of the Derivative Works, in at least one
          of the following places: within a NOTICE text file distributed
          as part of the Derivative Works; within the Source form or
          documentation, if provided along with the Derivative Works; or,
          within a display generated by the Derivative Works, if and
          wherever such third-party notices normally appear. The contents
          of the NOTICE file are for informational purposes only and
          do not modify the License. You may add Your own attribution
          notices within Derivative Works that You distribute, alongside
          or as an addendum to the NOTICE text from the Work, provided
          that such additional attribution notices cannot be construed
          as modifying the License.

      You may add Your own copyright statement to Your modifications and
      may provide additional or different license terms and conditions
      for use, reproduction, or distribution of Your modifications, or
      for any such Derivative Works as a whole, provided Your use,
      reproduction, and distribution of the Work otherwise complies with
      the conditions stated in this License.

   5. Submission of Contributions. Unless You explicitly state otherwise,
      any Contribution intentionally submitted for inclusion in the Work
      by You to the Licensor shall be under the terms and conditions of
      this License, without any additional terms or conditions.
      Notwithstanding the above, nothing herein shall supersede or modify
      the terms of any separate license agreement you may have executed
      with Licensor regarding such Contributions.

   6. Trademarks. This License does not grant permission to use the trade
      names, trademarks, service marks, or product names of the Licensor,
      except as required for reasonable and customary use in describing the
      origin of the Work and reproducing the content of the NOTICE file.

   7. Disclaimer of Warranty. Unless required by applicable law or
      agreed to in writing, Licensor provides the Work (and each
      Contributor provides its Contributions) on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
      implied, including, without limitation, any warranties or conditions
      of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
      PARTICULAR PURPOSE. You are solely responsible for determining the
      appropriateness of using or redistributing the Work and assume any
      risks associated with Your exercise of permissions under this License.

   8. Limitation of Liability. In no event and under no legal theory,
      whether in tort (including negligence), contract, or otherwise,
      unless required by applicable law (such as deliberate and grossly
      negligent acts) or agreed to in writing, shall any Contributor be
      liable to You for damages, including any direct, indirect, special,
      incidental, or consequential damages of any character arising as a
      result of this License or out of the use or inability to use the
      Work (including but not limited to damages for loss of goodwill,
      work stoppage, computer failure or malfunction, or any and all
      other commercial damages or losses), even if such Contributor
      has been advised of the possibility of such damages.

   9. Accepting Warranty or Additional Liability. While redistributing
      the Work or Derivative Works thereof, You may choose to offer,
      and charge a fee for, acceptance of support, warranty, indemnity,
      or other liability obligations and/or rights consistent with this
      License. However, in accepting such obligations, You may act only
      on Your own behalf and on Your sole responsibility, not on behalf
      of any other Contributor, and only if You agree to indemnify,
      defend, and hold each Contributor harmless for any liability
      incurred by, or claims asserted against, such Contributor by reason
      of your accepting any such warranty or additional liability.

   END OF TERMS AND CONDITIONS
""".trimIndent()

val MIT_LICENSE = """
MIT License

Copyright (c) 

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
""".trimIndent()

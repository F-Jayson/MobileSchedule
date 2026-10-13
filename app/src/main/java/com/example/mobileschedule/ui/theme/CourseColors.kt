package com.example.mobileschedule.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

@Immutable
data class CourseColorPalette(
    val container: Color,
    val onContainer: Color,
    val secondaryText: Color,
    val accent: Color,
)

object CourseColorTheme {
    private val LightCoursePalettes = listOf(
        // 1. 晴空天蓝
        CourseColorPalette(
            container = Color(0xFFE8F1FC),
            onContainer = Color(0xFF1E3A8A),
            secondaryText = Color(0xFF3B82F6),
            accent = Color(0xFF2563EB),
        ),
        // 2. 润泽薄荷
        CourseColorPalette(
            container = Color(0xFFE6F4EA),
            onContainer = Color(0xFF14532D),
            secondaryText = Color(0xFF16A34A),
            accent = Color(0xFF10B981),
        ),
        // 3. 浅雾暖紫
        CourseColorPalette(
            container = Color(0xFFF3E8FF),
            onContainer = Color(0xFF581C87),
            secondaryText = Color(0xFF9333EA),
            accent = Color(0xFF8B5CF6),
        ),
        // 4. 蜜桃珊瑚
        CourseColorPalette(
            container = Color(0xFFFFEDD5),
            onContainer = Color(0xFF7C2D12),
            secondaryText = Color(0xFFEA580C),
            accent = Color(0xFFF97316),
        ),
        // 5. 翠微松石
        CourseColorPalette(
            container = Color(0xFFE0F2FE),
            onContainer = Color(0xFF0C4A6E),
            secondaryText = Color(0xFF0284C7),
            accent = Color(0xFF06B6D4),
        ),
        // 6. 樱花柔粉
        CourseColorPalette(
            container = Color(0xFFFCE7F3),
            onContainer = Color(0xFF831843),
            secondaryText = Color(0xFFDB2777),
            accent = Color(0xFFEC4899),
        ),
        // 7. 琥珀微芒
        CourseColorPalette(
            container = Color(0xFFFEF3C7),
            onContainer = Color(0xFF78350F),
            secondaryText = Color(0xFFD97706),
            accent = Color(0xFFF59E0B),
        ),
        // 8. 远山灰青
        CourseColorPalette(
            container = Color(0xFFE2E8F0),
            onContainer = Color(0xFF1E293B),
            secondaryText = Color(0xFF475569),
            accent = Color(0xFF64748B),
        ),
    )

    private val DarkCoursePalettes = listOf(
        // 1. 晴空深蓝
        CourseColorPalette(
            container = Color(0xFF1E293B),
            onContainer = Color(0xFFBAE6FD),
            secondaryText = Color(0xFF7DD3FC),
            accent = Color(0xFF38BDF8),
        ),
        // 2. 苍翠深绿
        CourseColorPalette(
            container = Color(0xFF143026),
            onContainer = Color(0xFFBBF7D0),
            secondaryText = Color(0xFF86EFAC),
            accent = Color(0xFF34D399),
        ),
        // 3. 幽兰深紫
        CourseColorPalette(
            container = Color(0xFF2E1065),
            onContainer = Color(0xFFE9D5FF),
            secondaryText = Color(0xFFD8B4FE),
            accent = Color(0xFFA78BFA),
        ),
        // 4. 熔岩深橙
        CourseColorPalette(
            container = Color(0xFF331A12),
            onContainer = Color(0xFFFED7AA),
            secondaryText = Color(0xFFFDBA74),
            accent = Color(0xFFFB923C),
        ),
        // 5. 沧海深青
        CourseColorPalette(
            container = Color(0xFF082F49),
            onContainer = Color(0xFFBAE6FD),
            secondaryText = Color(0xFF7DD3FC),
            accent = Color(0xFF22D3EE),
        ),
        // 6. 暮色粉紫
        CourseColorPalette(
            container = Color(0xFF381428),
            onContainer = Color(0xFFFBCFE8),
            secondaryText = Color(0xFFF472B6),
            accent = Color(0xFFF43F5E),
        ),
        // 7. 暗金琥珀
        CourseColorPalette(
            container = Color(0xFF352008),
            onContainer = Color(0xFFFDE68A),
            secondaryText = Color(0xFFFCD34D),
            accent = Color(0xFFFBBF24),
        ),
        // 8. 星夜黛青
        CourseColorPalette(
            container = Color(0xFF1E2638),
            onContainer = Color(0xFFE2E8F0),
            secondaryText = Color(0xFF94A3B8),
            accent = Color(0xFFCBD5E1),
        ),
    )

    fun forCourse(courseName: String, isDark: Boolean): CourseColorPalette {
        val palettes = if (isDark) DarkCoursePalettes else LightCoursePalettes
        val hash = abs(courseName.hashCode())
        val index = hash % palettes.size
        return palettes[index]
    }
}

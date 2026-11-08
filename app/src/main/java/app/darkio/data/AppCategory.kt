package app.darkio.data

import android.content.pm.ApplicationInfo
import androidx.annotation.StringRes
import app.darkio.R

enum class AppCategory(val slug: String, @StringRes val labelRes: Int) {
    ALL("alle", R.string.category_all),
    SOCIAL("social", R.string.category_social),
    GAME("spil", R.string.category_game),
    PHOTO("foto", R.string.category_photo),
    WORK("arbejde", R.string.category_work),
    TOOL("værktøj", R.string.category_tool),
    OTHER("andet", R.string.category_other),
}

fun appCategoryOf(category: Int): AppCategory = when (category) {
    ApplicationInfo.CATEGORY_GAME -> AppCategory.GAME
    ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
    ApplicationInfo.CATEGORY_IMAGE, ApplicationInfo.CATEGORY_VIDEO -> AppCategory.PHOTO
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.WORK
    ApplicationInfo.CATEGORY_MAPS, ApplicationInfo.CATEGORY_ACCESSIBILITY -> AppCategory.TOOL
    else -> AppCategory.OTHER
}

fun appCategoryOf(info: ApplicationInfo?): AppCategory =
    if (info == null) AppCategory.OTHER else appCategoryOf(info.category)

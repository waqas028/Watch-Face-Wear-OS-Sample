package com.waqas028.watch_faces_wear_os_sample.utils

import android.content.Context
import android.graphics.drawable.Icon
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.wear.watchface.style.UserStyleSetting
import androidx.wear.watchface.style.UserStyleSetting.ListUserStyleSetting
import com.waqas028.watch_faces_wear_os_sample.R

// Defaults for all styles.
// X_COLOR_STYLE_ID - id in watch face database for each style id.
// X_COLOR_STYLE_NAME_RESOURCE_ID - String name to display in the user settings UI for the style.
// X_COLOR_STYLE_ICON_ID - Icon to display in the user settings UI for the style.
const val AMBIENT_COLOR_STYLE_ID = "ambient_style_id"
private val AMBIENT_COLOR_STYLE_NAME_RESOURCE_ID = R.string.ambient_style_name
private val AMBIENT_COLOR_STYLE_ICON_ID = R.drawable.red_style

const val RED_COLOR_STYLE_ID = "red_style_id"
private val RED_COLOR_STYLE_NAME_RESOURCE_ID = R.string.red_style_name
private val RED_COLOR_STYLE_ICON_ID = R.drawable.red_style

const val GREEN_COLOR_STYLE_ID = "green_style_id"
private val GREEN_COLOR_STYLE_NAME_RESOURCE_ID = R.string.green_style_name
private val GREEN_COLOR_STYLE_ICON_ID = R.drawable.red_style

const val BLUE_COLOR_STYLE_ID = "blue_style_id"
private val BLUE_COLOR_STYLE_NAME_RESOURCE_ID = R.string.blue_style_name
private val BLUE_COLOR_STYLE_ICON_ID = R.drawable.red_style

const val WHITE_COLOR_STYLE_ID = "white_style_id"
private val WHITE_COLOR_STYLE_NAME_RESOURCE_ID = R.string.white_style_name
private val WHITE_COLOR_STYLE_ICON_ID = R.drawable.red_style

/**
 * Represents watch face color style options the user can select (includes the unique id, the
 * complication style resource id, and general watch face color style resource ids).
 *
 * The companion object offers helper functions to translate a unique string id to the correct enum
 * and convert all the resource ids to their correct resources (with the Context passed in). The
 * renderer will use these resources to render the actual colors and ComplicationDrawables of the
 * watch face.
 */
enum class ColorStyleIdAndResourceIds(
    val id: String,
    @StringRes val nameResourceId: Int,
    @DrawableRes val iconResourceId: Int,
    @DrawableRes val complicationStyleDrawableId: Int,
    @ColorRes val primaryColorId: Int,
    @ColorRes val secondaryColorId: Int,
    @ColorRes val backgroundColorId: Int,
    @ColorRes val outerElementColorId: Int
) {
    AMBIENT(
        id = AMBIENT_COLOR_STYLE_ID,
        nameResourceId = AMBIENT_COLOR_STYLE_NAME_RESOURCE_ID,
        iconResourceId = AMBIENT_COLOR_STYLE_ICON_ID,
        complicationStyleDrawableId = R.drawable.complication_red_style,
        primaryColorId = R.color.ambient_primary_color,
        secondaryColorId = R.color.ambient_secondary_color,
        backgroundColorId = R.color.ambient_background_color,
        outerElementColorId = R.color.ambient_outer_element_color
    ),

    RED(
        id = RED_COLOR_STYLE_ID,
        nameResourceId = RED_COLOR_STYLE_NAME_RESOURCE_ID,
        iconResourceId = RED_COLOR_STYLE_ICON_ID,
        complicationStyleDrawableId = R.drawable.complication_red_style,
        primaryColorId = R.color.red_primary_color,
        secondaryColorId = R.color.red_secondary_color,
        backgroundColorId = R.color.red_background_color,
        outerElementColorId = R.color.red_outer_element_color
    ),

    GREEN(
        id = GREEN_COLOR_STYLE_ID,
        nameResourceId = GREEN_COLOR_STYLE_NAME_RESOURCE_ID,
        iconResourceId = GREEN_COLOR_STYLE_ICON_ID,
        complicationStyleDrawableId = R.drawable.complication_red_style,
        primaryColorId = R.color.green_primary_color,
        secondaryColorId = R.color.green_secondary_color,
        backgroundColorId = R.color.green_background_color,
        outerElementColorId = R.color.green_outer_element_color
    ),

    BLUE(
        id = BLUE_COLOR_STYLE_ID,
        nameResourceId = BLUE_COLOR_STYLE_NAME_RESOURCE_ID,
        iconResourceId = BLUE_COLOR_STYLE_ICON_ID,
        complicationStyleDrawableId = R.drawable.complication_red_style,
        primaryColorId = R.color.blue_primary_color,
        secondaryColorId = R.color.blue_secondary_color,
        backgroundColorId = R.color.blue_background_color,
        outerElementColorId = R.color.blue_outer_element_color
    ),

    WHITE(
        id = WHITE_COLOR_STYLE_ID,
        nameResourceId = WHITE_COLOR_STYLE_NAME_RESOURCE_ID,
        iconResourceId = WHITE_COLOR_STYLE_ICON_ID,
        complicationStyleDrawableId = R.drawable.complication_red_style,
        primaryColorId = R.color.white_primary_color,
        secondaryColorId = R.color.white_secondary_color,
        backgroundColorId = R.color.white_background_color,
        outerElementColorId = R.color.white_outer_element_color
    );

    companion object {
        /**
         * Translates the string id to the correct ColorStyleIdAndResourceIds object.
         */
        fun getColorStyleConfig(id: String): ColorStyleIdAndResourceIds {
            return when (id) {
                AMBIENT.id -> AMBIENT
                RED.id -> RED
                GREEN.id -> GREEN
                BLUE.id -> BLUE
                WHITE.id -> WHITE
                else -> WHITE
            }
        }

        /**
         * Returns a list of [UserStyleSetting.ListUserStyleSetting.ListOption] for all
         * ColorStyleIdAndResourceIds enums. The watch face settings APIs use this to set up
         * options for the user to select a style.
         */
        fun toOptionList(context: Context): List<ListUserStyleSetting.ListOption> {
            val colorStyleIdAndResourceIdsList = enumValues<ColorStyleIdAndResourceIds>()

            return colorStyleIdAndResourceIdsList.map { colorStyleIdAndResourceIds ->
                ListUserStyleSetting.ListOption(
                    UserStyleSetting.Option.Id(colorStyleIdAndResourceIds.id),
                    context.resources,
                    colorStyleIdAndResourceIds.nameResourceId,
                    Icon.createWithResource(
                        context,
                        colorStyleIdAndResourceIds.iconResourceId
                    )
                )
            }
        }
    }
}

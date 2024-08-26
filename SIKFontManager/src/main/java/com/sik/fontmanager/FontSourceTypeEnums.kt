package com.sik.fontmanager

/**
 * 字体来源类型枚举
 */
enum class FontSourceTypeEnums {
    /**
     * 位置类型
     */
    UNKNOW,

    /**
     * Assets文件夹中
     */
    ASSETS,

    /**
     * res/fonts中
     */
    RES,

    /**
     * 文件路径
     */
    FILE;

    companion object {
        /**
         * 获取字体来源类型枚举
         */
        fun getFontSourceType(fontSourceType: String): FontSourceTypeEnums {
            return when (fontSourceType.uppercase()) {
                "ASSETS" -> ASSETS
                "RES" -> RES
                "FILE" -> FILE
                else -> UNKNOW
            }
        }
    }
}
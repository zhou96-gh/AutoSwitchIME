package com.rimevim.core

import java.io.File

/**
 * IME 相关常量
 */
object ImeConstants {
    /**
     * 获取输入法状态文件名
     * 每个输入法独立文件，避免混淆
     */
    fun getStateFileName(type: ImeType): String = when (type) {
        ImeType.RIME -> "ime-state-rime.json"
        ImeType.SOGOU -> "ime-state-sogou.json"
        ImeType.MS_PINYIN -> "ime-state-mspinyin.json"
        ImeType.CUSTOM -> "ime-state-custom.json"
    }
    
    /**
     * 获取输入法状态文件完整路径
     */
    fun getStateFilePath(type: ImeType): String =
        System.getenv("TEMP") + File.separator + getStateFileName(type)
}

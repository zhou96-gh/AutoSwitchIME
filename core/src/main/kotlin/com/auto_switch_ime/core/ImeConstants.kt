package com.auto_switch_ime.core

/**
 * IME 相关常量
 */
object ImeConstants {
    fun getRimeStateFilePath(): String =
        System.getenv("TEMP") + java.io.File.separator + "ime-state-rime-v2.json"
}

package com.rimevim.core.ime

import com.rimevim.core.util.Logger
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File

/**
 * WeaselServer.exe 路径检测
 * 优先级：注册表 > 常见路径扫描
 */
object WeaselPathDetector {

    private val COMMON_PATHS = listOf(
        "C:\\Program Files (x86)\\Rime",
        "C:\\Program Files\\Rime",
        "D:\\Program Files\\Rime"
    )

    /**
     * 自动检测 WeaselServer.exe 路径
     */
    fun detect(logger: Logger? = null): String? {
        return readFromRegistry(logger) ?: scanCommonPaths(logger)
    }

    private fun readFromRegistry(logger: Logger? = null): String? {
        return try {
            val root = Advapi32Util.registryGetStringValue(
                WinReg.HKEY_LOCAL_MACHINE,
                "SOFTWARE\\Rime\\Weasel",
                "WeaselRoot"
            )
            val serverPath = "$root\\WeaselServer.exe"
            if (File(serverPath).exists()) serverPath else null
        } catch (e: UnsatisfiedLinkError) {
            logger?.debug("JNA native library not available, skipping registry lookup")
            null
        } catch (e: Exception) {
            logger?.debug("Registry lookup failed: ${e.message}")
            null
        }
    }

    private fun scanCommonPaths(logger: Logger? = null): String? {
        for (basePath in COMMON_PATHS) {
            val baseDir = File(basePath)
            if (!baseDir.exists()) continue

            val weaselDirs = baseDir.listFiles { f ->
                f.isDirectory && f.name.startsWith("weasel-")
            } ?: continue

            for (weaselDir in weaselDirs.sortedDescending()) {
                val serverPath = File(weaselDir, "WeaselServer.exe")
                if (serverPath.exists()) {
                    return serverPath.absolutePath
                }
            }
        }
        return null
    }
}

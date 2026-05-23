package com.rimevim.ime

import com.intellij.openapi.diagnostic.thisLogger
import java.io.File

object WeaselPathDetector {

    private val COMMON_PATHS = listOf(
        "C:\\Program Files (x86)\\Rime",
        "C:\\Program Files\\Rime",
        "D:\\Program Files\\Rime"
    )

    /**
     * 自动检测 WeaselServer.exe 路径
     * 优先级：注册表 > 常见路径扫描
     */
    fun detect(): String? {
        return readFromRegistry() ?: scanCommonPaths()
    }

    private fun readFromRegistry(): String? {
        return try {
            // 使用 JNA 访问 Windows 注册表
            val root = com.sun.jna.platform.win32.Advapi32Util.registryGetStringValue(
                com.sun.jna.platform.win32.WinReg.HKEY_LOCAL_MACHINE,
                "SOFTWARE\\Rime\\Weasel",
                "WeaselRoot"
            )
            val serverPath = "$root\\WeaselServer.exe"
            if (File(serverPath).exists()) serverPath else null
        } catch (e: UnsatisfiedLinkError) {
            thisLogger().debug("JNA native library not available, skipping registry lookup")
            null
        } catch (e: Exception) {
            thisLogger().debug("Registry lookup failed: ${e.message}")
            null
        }
    }

    private fun scanCommonPaths(): String? {
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

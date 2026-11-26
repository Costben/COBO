package com.cobo.app.data

import com.cobo.app.platform.PlatformContext
import com.cobo.app.platform.PlatformFile
import com.cobo.app.platform.openExternalUrl

internal actual object PlatformUpdater {
    actual val userAgent: String = "Cobo-iOS"
    actual val preferIpaAsset: Boolean = true

    actual fun openDownload(info: UpdateInfo) {
        openExternalUrl(info.pageUrl.ifBlank { "https://github.com/Costben/COBO/releases" })
    }

    actual suspend fun downloadPackage(
        context: PlatformContext,
        url: String,
        version: String,
        force: Boolean,
        onProgress: (progress: Float, currentBytes: Long, totalBytes: Long) -> Unit,
    ): PlatformFile {
        openExternalUrl(url.ifBlank { "https://github.com/Costben/COBO/releases" })
        throw ApiException("iOS 请前往 GitHub Releases 获取 IPA 安装")
    }

    actual fun installPackage(context: PlatformContext, file: PlatformFile) {
        openExternalUrl("https://github.com/Costben/COBO/releases")
    }
}

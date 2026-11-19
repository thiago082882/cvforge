package com.cvforge.app.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

actual val platformModule: Module = module {
    single<FileExporter> { IosFileExporter() }
}

/** Grava em tmp e abre a folha de compartilhamento nativa (Salvar em Arquivos, AirDrop, e-mail…). */
@OptIn(ExperimentalForeignApi::class)
class IosFileExporter : FileExporter {
    override suspend fun export(fileName: String, mimeType: String, bytes: ByteArray) {
        val path = NSTemporaryDirectory() + fileName
        withContext(Dispatchers.Default) {
            bytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong()).writeToFile(path, true)
            }
        }
        withContext(Dispatchers.Main) {
            val controller = UIActivityViewController(activityItems = listOf(NSURL.fileURLWithPath(path)), applicationActivities = null)
            var top: UIViewController? = UIApplication.sharedApplication.keyWindow?.rootViewController
            while (top?.presentedViewController != null) top = top.presentedViewController
            // iPad exige um ponto de ancoragem para o popover.
            controller.popoverPresentationController?.sourceView = top?.view
            top?.presentViewController(controller, animated = true, completion = null)
        }
    }
}

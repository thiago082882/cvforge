package com.cvforge.app.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual val platformModule: Module = module {
    single<FileExporter> { DesktopFileExporter() }
}

/** Diálogo nativo "Salvar como…". Cancelar não é erro. */
class DesktopFileExporter : FileExporter {
    override suspend fun export(fileName: String, mimeType: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val dialog = FileDialog(null as Frame?, "Salvar arquivo", FileDialog.SAVE).apply {
            file = fileName
            isVisible = true // bloqueia até o usuário escolher
        }
        val dir = dialog.directory
        val name = dialog.file
        if (dir != null && name != null) File(dir, name).writeBytes(bytes)
    }
}

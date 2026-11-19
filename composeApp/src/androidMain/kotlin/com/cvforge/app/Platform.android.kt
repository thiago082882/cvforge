package com.cvforge.app.platform

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

actual val platformModule: Module = module {
    single<FileExporter> { AndroidFileExporter(androidContext()) }
}

/** Grava em cache/exports e abre a folha de compartilhamento (WhatsApp, e-mail, Drive, salvar em arquivos…). */
class AndroidFileExporter(private val context: Context) : FileExporter {
    override suspend fun export(fileName: String, mimeType: String, bytes: ByteArray) {
        val file = withContext(Dispatchers.IO) {
            File(context.cacheDir, "exports").apply { mkdirs() }.let { File(it, fileName).apply { writeBytes(bytes) } }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

package com.cvforge.app.platform

import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.io.encoding.Base64

actual val platformModule: Module = module {
    single<FileExporter> { WebFileExporter() }
}

@JsFun(
    """(name, mime, b64) => {
        const bin = atob(b64);
        const arr = new Uint8Array(bin.length);
        for (let i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
        const url = URL.createObjectURL(new Blob([arr], { type: mime }));
        const a = document.createElement('a');
        a.href = url; a.download = name; document.body.appendChild(a); a.click();
        document.body.removeChild(a);
        setTimeout(() => URL.revokeObjectURL(url), 10000);
    }""",
)
private external fun downloadBlob(name: String, mime: String, base64: String)

/** Dispara o download pelo navegador usando um Blob. */
class WebFileExporter : FileExporter {
    override suspend fun export(fileName: String, mimeType: String, bytes: ByteArray) {
        downloadBlob(fileName, mimeType, Base64.encode(bytes))
    }
}

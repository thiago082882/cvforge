package com.cvforge.app.platform

import org.koin.core.module.Module

/**
 * Entrega um arquivo ao usuário da forma nativa de cada plataforma:
 * Android/iOS → folha de compartilhamento; Desktop → diálogo "Salvar como"; Web → download do navegador.
 */
interface FileExporter {
    suspend fun export(fileName: String, mimeType: String, bytes: ByteArray)
}

/** Módulo Koin com as implementações específicas de cada plataforma. */
expect val platformModule: Module

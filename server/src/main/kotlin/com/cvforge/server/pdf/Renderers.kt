package com.cvforge.server.pdf

import com.cvforge.contract.CurriculumDto
import com.cvforge.contract.CvLanguage
import com.cvforge.contract.CvTemplate
import com.lowagie.text.Chunk
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.Rectangle
import com.lowagie.text.pdf.BaseFont
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import com.lowagie.text.pdf.draw.LineSeparator
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * PDF de verdade (texto selecionável, coluna única, fontes padrão) — é isso que os ATS conseguem ler.
 * O layout em duas colunas com gradiente/ícones do projeto original costuma quebrar a leitura dos ATS.
 */
class PdfRenderer {
    private class Style(
        val nameSize: Float, val body: Float, val heading: Float, val margin: Float,
        val accent: Color, val gap: Float,
    )

    private fun styleOf(t: CvTemplate) = when (t) {
        CvTemplate.CLASSIC -> Style(20f, 10f, 10.5f, 42f, Color(0x11, 0x18, 0x27), 7f)
        CvTemplate.MODERN -> Style(22f, 10f, 10.5f, 42f, Color(0x0F, 0x7B, 0x5F), 8f)
        CvTemplate.COMPACT -> Style(17f, 9f, 9.5f, 32f, Color(0x37, 0x41, 0x51), 4f)
    }

    private val regular: BaseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED)
    private val bold: BaseFont = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED)
    private val dark = Color(0x1F, 0x29, 0x37)
    private val muted = Color(0x4B, 0x55, 0x63)

    fun render(cv: CurriculumDto, template: CvTemplate): ByteArray {
        val s = styleOf(template)
        val c = cv.content
        val en = cv.language == CvLanguage.EN
        val out = ByteArrayOutputStream()
        val doc = Document(PageSize.A4, s.margin, s.margin, s.margin, s.margin)
        PdfWriter.getInstance(doc, out)
        doc.addTitle(safe("${c.name} – CV"))
        doc.addAuthor(safe(c.name))
        doc.addCreator("CVForge")
        doc.open()

        fun f(base: BaseFont, size: Float, color: Color = dark) = Font(base, size, Font.NORMAL, color)

        // Cabeçalho
        doc.add(Paragraph(safe(c.name), f(bold, s.nameSize, s.accent)).apply { spacingAfter = 2f })
        val contact = listOfNotNull(c.email, c.phone, c.city.ifBlank { null }, c.linkedin, c.website)
            .joinToString("  |  ")
        doc.add(Paragraph(safe(contact), f(regular, s.body, muted)).apply { spacingAfter = s.gap })

        fun section(title: String) {
            doc.add(Paragraph(safe(title.uppercase()), f(bold, s.heading, s.accent)).apply {
                spacingBefore = s.gap + 3f
                spacingAfter = 1f
            })
            doc.add(Chunk(LineSeparator(0.6f, 100f, s.accent, Element.ALIGN_CENTER, -2f)))
            doc.add(Paragraph(" ", f(regular, 3f)))
        }

        fun bullet(text: String) {
            doc.add(Paragraph().apply {
                add(Chunk("•  ", f(regular, s.body)))
                add(Chunk(safe(text), f(regular, s.body)))
                indentationLeft = 14f
                firstLineIndent = -10f
                setLeading(0f, 1.25f)
                spacingAfter = 1.5f
            })
        }

        if (c.summary.isNotBlank()) {
            section(if (en) "Professional Summary" else "Resumo Profissional")
            doc.add(Paragraph(safe(c.summary), f(regular, s.body)).apply { setLeading(0f, 1.3f) })
        }

        if (c.skills.isNotEmpty()) {
            section(if (en) "Skills" else "Habilidades")
            doc.add(Paragraph(safe(c.skills.joinToString("  •  ")), f(regular, s.body)).apply { setLeading(0f, 1.3f) })
        }

        if (c.experiences.isNotEmpty()) {
            section(if (en) "Experience" else "Experiência Profissional")
            c.experiences.forEach { e ->
                doc.add(twoColumn(
                    left = Phrase().apply {
                        add(Chunk(safe(e.role), f(bold, s.body + 0.5f)))
                        add(Chunk(safe("  ·  ${e.company}"), f(regular, s.body, muted)))
                    },
                    right = Phrase(safe(e.period), f(regular, s.body - 0.5f, muted)),
                ))
                e.bullets.forEach(::bullet)
                doc.add(Paragraph(" ", f(regular, s.gap)))
            }
        }

        if (c.education.isNotEmpty()) {
            section(if (en) "Education" else "Formação Acadêmica")
            c.education.forEach { ed ->
                val degree = listOf(ed.degree, ed.fieldOfStudy).filter { it.isNotBlank() }.joinToString(" – ")
                doc.add(twoColumn(
                    left = Phrase().apply {
                        add(Chunk(safe(degree), f(bold, s.body)))
                        add(Chunk(safe("  ·  ${ed.institution}"), f(regular, s.body, muted)))
                    },
                    right = Phrase(ed.graduationYear?.toString() ?: if (en) "In progress" else "Em andamento", f(regular, s.body - 0.5f, muted)),
                ))
            }
        }

        doc.close()
        return out.toByteArray()
    }

    private fun twoColumn(left: Phrase, right: Phrase): PdfPTable = PdfPTable(floatArrayOf(76f, 24f)).apply {
        widthPercentage = 100f
        setSpacingBefore(2f)
        setSpacingAfter(2f)
        addCell(PdfPCell(left).apply { border = Rectangle.NO_BORDER; setPadding(0f) })
        addCell(PdfPCell(right).apply { border = Rectangle.NO_BORDER; setPadding(0f); horizontalAlignment = Element.ALIGN_RIGHT })
    }
}

/** Mantém só caracteres que a fonte padrão (Windows-1252) sabe desenhar; troca o resto por espaço. */
internal fun safe(text: String): String {
    val enc = Charset.forName("windows-1252").newEncoder()
    return buildString(text.length) {
        for (ch in text) {
            when {
                ch == '\n' || ch == '\t' -> append(' ')
                enc.canEncode(ch) -> append(ch)
                ch == '\u2192' -> append("->")
                else -> append(' ')
            }
        }
    }.replace(Regex(" {2,}"), " ").let { it }
}

object TextRenderer {
    /** TXT puro: ideal para colar em formulários de candidatura que não aceitam arquivo. */
    fun render(cv: CurriculumDto): String {
        val c = cv.content
        val en = cv.language == CvLanguage.EN
        return buildString {
            appendLine(c.name.uppercase())
            appendLine(listOfNotNull(c.email, c.phone, c.city.ifBlank { null }, c.linkedin, c.website).joinToString(" | "))
            appendLine()
            if (c.summary.isNotBlank()) { appendLine(if (en) "PROFESSIONAL SUMMARY" else "RESUMO PROFISSIONAL"); appendLine(c.summary); appendLine() }
            if (c.skills.isNotEmpty()) { appendLine(if (en) "SKILLS" else "HABILIDADES"); appendLine(c.skills.joinToString(", ")); appendLine() }
            if (c.experiences.isNotEmpty()) {
                appendLine(if (en) "EXPERIENCE" else "EXPERIÊNCIA PROFISSIONAL")
                c.experiences.forEach { e ->
                    appendLine("${e.role} - ${e.company} (${e.period})")
                    e.bullets.forEach { appendLine("- $it") }
                    appendLine()
                }
            }
            if (c.education.isNotEmpty()) {
                appendLine(if (en) "EDUCATION" else "FORMAÇÃO ACADÊMICA")
                c.education.forEach { ed ->
                    val degree = listOf(ed.degree, ed.fieldOfStudy).filter { it.isNotBlank() }.joinToString(" - ")
                    appendLine("$degree - ${ed.institution} (${ed.graduationYear ?: if (en) "in progress" else "em andamento"})")
                }
            }
        }.trimEnd() + "\n"
    }
}

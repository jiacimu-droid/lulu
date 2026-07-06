package me.rerere.document

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocxParserTest {
    @Test
    fun `parser finds document xml when docx entries have a folder prefix`() {
        val file = File.createTempFile("docx-parser-prefix", ".docx")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("prefix/word/document.xml"))
                zip.write(
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body>
                        <w:p><w:r><w:t>第一段内容</w:t></w:r></w:p>
                      </w:body>
                    </w:document>
                    """.trimIndent().toByteArray()
                )
                zip.closeEntry()
            }

            val parsed = DocxParser.parse(file)

            assertTrue(parsed, parsed.contains("第一段内容"))
        } finally {
            file.delete()
        }
    }
}

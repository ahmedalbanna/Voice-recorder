package com.example

import com.example.ai.GeminiService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class GeminiServiceTest {

    private val service = GeminiService()

    @Test
    fun transcribe_mock_returnsArabic() = runTest {
        val tmp = Files.createTempFile("اجتماع_اختبار", ".m4a").toFile()
        tmp.writeBytes(ByteArray(1024) { 0 })
        val result = service.transcribe(tmp)
        assertTrue(result.isNotBlank())
        // Mock should contain Arabic characters for اجتماع
        assertTrue(result.contains("اجتماع") || result.contains("مرحب"))
        tmp.delete()
    }

    @Test
    fun summarize_mock_notEmpty() = runTest {
        val summary = service.summarize("اجتماع الفريق ناقش الخطة واتفق على التسليم الخميس.")
        assertTrue(summary.contains("•"))
    }

    @Test
    fun suggestTitle_mock() = runTest {
        val title = service.suggestTitle("اجتماع الفريق الأسبوعي")
        assertTrue(title.isNotBlank())
    }

    @Test
    fun extractActionItems_mock() = runTest {
        val items = service.extractActionItems("اجتماع الفريق اتفقنا على أن أحمد يراجع النماذج.")
        assertTrue(items.isNotEmpty())
    }
}

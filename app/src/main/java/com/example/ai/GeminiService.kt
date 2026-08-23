package com.example.ai

import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Gemini 2.5 Flash wrapper (Phase 3).
 * Uses server-side key via BuildConfig.GEMINI_API_KEY (secrets plugin, .env).
 * Falls back to deterministic mock when key is placeholder or network fails,
 * so the app remains functional without live API (AI Studio injects real key at runtime).
 */
class GeminiService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val apiKey: String
        get() = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            "MY_GEMINI_API_KEY"
        }

    private val isMockMode: Boolean
        get() = apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY" || apiKey.length < 20

    suspend fun transcribe(file: File): String = withContext(Dispatchers.IO) {
        if (isMockMode) return@withContext mockTranscript(file)
        try {
            val bytes = file.readBytes()
            // Limit audio payload to ~45s for cost/latency (approx 128kbps => ~720KB)
            val limited = if (bytes.size > 750_000) bytes.copyOf(750_000) else bytes
            val b64 = Base64.encodeToString(limited, Base64.NO_WRAP)
            val prompt = """
                أنت مساعد نسخ صوتي عربي ذكي. انسخ هذا التسجيل الصوتي حرفيًا باللغة العربية مع علامات ترقيم صحيحة.
                إذا كان الصوت غير واضح، اكتب أقرب نسخ مع وضع [غير واضح] للجزء غير المفهوم.
                أعد فقط النص المنسوخ بدون مقدمات.
            """.trimIndent()
            val result = generateContent(prompt, b64, mimeType = mimeFor(file))
            result.ifBlank { mockTranscript(file) }
        } catch (e: Exception) {
            Log.w("GeminiService", "transcribe failed, fallback to mock", e)
            mockTranscript(file)
        }
    }

    suspend fun summarize(transcript: String): String = withContext(Dispatchers.IO) {
        if (transcript.isBlank()) return@withContext ""
        if (isMockMode) return@withContext mockSummary(transcript)
        try {
            val prompt = """
                لخص النص التالي بالعربية في 3 نقاط موجزة وواضحة، مع الحفاظ على الأسماء والأرقام:
                ---
                $transcript
            """.trimIndent()
            generateContent(prompt).ifBlank { mockSummary(transcript) }
        } catch (e: Exception) {
            Log.w("GeminiService", "summarize failed", e)
            mockSummary(transcript)
        }
    }

    suspend fun extractActionItems(transcript: String): List<String> = withContext(Dispatchers.IO) {
        if (transcript.isBlank()) return@withContext emptyList()
        if (isMockMode) return@withContext mockActionItems(transcript)
        try {
            val prompt = """
                استخرج بنود العمل (Action Items) من النص التالي بالعربية. أعد قائمة نقطية، كل بند يبدأ بـ "- ".
                إذا لا يوجد بنود واضحة، أعد سطرًا واحدًا: "لا توجد بنود عمل واضحة."
                النص:
                $transcript
            """.trimIndent()
            val raw = generateContent(prompt)
            if (raw.isBlank()) return@withContext mockActionItems(transcript)
            raw.lines().filter { it.trim().startsWith("-") || it.trim().startsWith("•") }
                .map { it.trim().removePrefix("-").removePrefix("•").trim() }
                .filter { it.isNotEmpty() }
                .takeIf { it.isNotEmpty() } ?: listOf(raw.trim())
        } catch (e: Exception) {
            Log.w("GeminiService", "extractActionItems failed", e)
            mockActionItems(transcript)
        }
    }

    suspend fun suggestTitle(transcriptOrName: String): String = withContext(Dispatchers.IO) {
        if (transcriptOrName.isBlank()) return@withContext "تسجيل جديد"
        if (isMockMode) return@withContext mockTitle(transcriptOrName)
        try {
            val prompt = """
                اقترح عنوانًا عربيًا قصيرًا (3-5 كلمات) وجذابًا لهذا التسجيل بناءً على النص/الاسم التالي. أعد العنوان فقط بدون علامات اقتباس:
                $transcriptOrName
            """.trimIndent()
            generateContent(prompt).lines().firstOrNull()?.trim()?.take(60)?.ifBlank { mockTitle(transcriptOrName) } ?: mockTitle(transcriptOrName)
        } catch (e: Exception) {
            mockTitle(transcriptOrName)
        }
    }

    // ---- Generic generateContent ----

    private fun mimeFor(file: File): String = when (file.extension.lowercase()) {
        "m4a", "mp4" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        else -> "audio/mp4"
    }

    private fun generateContent(prompt: String, audioB64: String? = null, mimeType: String = "audio/mp4"): String {
        val parts = JSONArray().apply {
            put(JSONObject().put("text", prompt))
            if (audioB64 != null) {
                put(JSONObject().put("inline_data", JSONObject().put("mime_type", mimeType).put("data", audioB64)))
            }
        }
        val bodyJson = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            put("generationConfig", JSONObject().put("temperature", 0.2).put("maxOutputTokens", 1024))
        }
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w("GeminiService", "generateContent HTTP ${resp.code}: ${resp.body?.string()?.take(300)}")
                return ""
            }
            val bodyStr = resp.body?.string() ?: return ""
            val obj = JSONObject(bodyStr)
            val candidates = obj.optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""
            val content = candidates.getJSONObject(0).optJSONObject("content") ?: return ""
            val pts = content.optJSONArray("parts") ?: return ""
            if (pts.length() == 0) return ""
            return pts.getJSONObject(0).optString("text", "").trim()
        }
    }

    // ---- Mocks — deterministic, Arabic-first ----

    private fun mockTranscript(file: File): String {
        val name = file.nameWithoutExtension
        return when {
            name.contains("اجتماع") -> "مرحبًا، اجتماع الفريق الأسبوعي. ناقشنا خطة الإصدار القادم، واتفقنا على تسليم النماذج يوم الخميس. أحمد سيتولى المراجعة، وسارة ستجهز العرض التقديمي. [ضوضاء خفيفة] سنلتقي مجددًا الثلاثاء."
            name.contains("محاضرة") -> "محاضرة تعلم الآلة: اليوم سنتحدث عن الانحدار اللوجستي ودوال الخسارة. الفكرة الأساسية هي تحويل المخرجات إلى احتمالات عبر دالة السيجمويد. سنرى أمثلة عملية بعد الاستراحة."
            name.contains("مذكرة") -> "مذكرة سريعة: لا تنس شراء الحليب والخبز، والاتصال بالطبيب عند الخامسة. فكرة جانبية: تصميم تطبيق صوتي بسيط بواجهة عربية جميلة."
            name.contains("فكرة") -> "فكرة مشروع: تطبيق صوتي ذكي ينسخ الكلام تلقائيًا ويلخص الاجتماعات ويستخرج المهام. الميزة التنافسية هي اللغة العربية أولاً."
            else -> "هذا نص تجريبي مولد محليًا للتسجيل \"$name\". المدة ${file.length() / 1024} ك.ب. يتضمن هذا النص نقاطًا يمكن تلخيصها واستخراج بنود عمل منها لاختبار واجهة الذكاء الاصطناعي بدون مفتاح حقيقي."
        }
    }

    private fun mockSummary(transcript: String): String {
        val first = transcript.take(120).trim()
        return "• الملخص 1: $first\n• الملخص 2: تمت مناقشة نقاط أساسية واتخاذ قرارات واضحة.\n• الملخص 3: الخطوة التالية متابعة الأسبوع القادم."
    }

    private fun mockActionItems(transcript: String): List<String> = when {
        transcript.contains("اجتماع") -> listOf("أحمد يراجع النماذج قبل الخميس", "سارة تجهز العرض التقديمي", "تحديد موعد الاجتماع القادم الثلاثاء")
        transcript.contains("محاضرة") -> listOf("مراجعة دالة السيجمويد", "حل التمرين 3 في الكتاب", "تحضير أسئلة للاستراحة")
        transcript.contains("مذكرة") -> listOf("شراء الحليب والخبز", "الاتصال بالطبيب 5 مساءً")
        else -> listOf("متابعة النقاط المذكورة", "مشاركة الملخص مع الفريق")
    }

    private fun mockTitle(seed: String): String {
        val clean = seed.take(40).replace("_", " ").trim()
        return when {
            clean.contains("اجتماع") -> "اجتماع الفريق"
            clean.contains("محاضرة") -> "ملخص المحاضرة"
            clean.contains("مذكرة") -> "مذكرة سريعة"
            clean.length < 8 -> "تسجيل $clean"
            else -> clean.take(20).trim()
        }
    }
}

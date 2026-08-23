package com.example

import com.example.ui.formatFileSize
import com.example.ui.formatMillis
import com.example.ui.formatSeconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class RecordingUtilsTest {

    @Test
    fun formatSeconds_zero() {
        assertEquals("00:00", formatSeconds(0))
    }

    @Test
    fun formatSeconds_65() {
        assertEquals("01:05", formatSeconds(65))
    }

    @Test
    fun formatMillis_185000() {
        assertEquals("03:05", formatMillis(185_000L))
    }

    @Test
    fun formatFileSize_bytes_ar() {
        val default = Locale.getDefault()
        try {
            Locale.setDefault(Locale("ar"))
            // 0 may be "0 ب" or "٠ ب" depending on formatting — just check unit
            val zero = formatFileSize(0)
            assertTrue(zero.contains("ب"))
            val s = formatFileSize(1024)
            // Arabic locale may use Eastern Arabic numerals and Arabic decimal separator "٫" — just check unit and non-empty
            assertTrue(s.isNotBlank())
            assertTrue(s.contains("ك.ب") || s.contains("ب") || s.contains("KB"))
        } finally {
            Locale.setDefault(default)
        }
    }

    @Test
    fun formatFileSize_en() {
        val default = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            assertEquals("0 B", formatFileSize(0))
            val s = formatFileSize(2048)
            assertTrue(s.contains("2.0"))
            assertTrue(s.contains("KB") || s.contains("ك"))
        } finally {
            Locale.setDefault(default)
        }
    }

    @Test
    fun formatFileSize_large() {
        val s = formatFileSize(34_603_008L)
        assertTrue(s.isNotEmpty())
    }
}

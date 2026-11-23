package com.cleansaver.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object ExtractionLogger {
    private const val MAX_ENTRIES = 50
    private val entries = CopyOnWriteArrayList<String>()

    fun log(tag: String, message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        entries.add("[$time] $tag: $message")
        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(0)
        }
    }

    fun getLogs(): List<String> = entries.toList().reversed()

    fun clear() {
        entries.clear()
    }
}


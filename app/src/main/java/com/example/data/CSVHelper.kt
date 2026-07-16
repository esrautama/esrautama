package com.example.data

object CSVHelper {

    fun parseCSV(content: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        var currentField = StringBuilder()
        var insideQuotes = false
        var i = 0

        while (i < content.length) {
            val c = content[i]
            when {
                c == '"' -> {
                    if (insideQuotes && i + 1 < content.length && content[i + 1] == '"') {
                        currentField.append('"')
                        i++ // Skip second quote
                    } else {
                        insideQuotes = !insideQuotes
                    }
                }
                c == ',' && !insideQuotes -> {
                    currentRow.add(currentField.toString().trim())
                    currentField.setLength(0)
                }
                (c == '\n' || c == '\r') && !insideQuotes -> {
                    if (c == '\r' && i + 1 < content.length && content[i + 1] == '\n') {
                        i++ // Skip \n after \r
                    }
                    currentRow.add(currentField.toString().trim())
                    currentField.setLength(0)
                    if (currentRow.any { it.isNotEmpty() }) {
                        result.add(currentRow)
                    }
                    currentRow = mutableListOf()
                }
                else -> {
                    currentField.append(c)
                }
            }
            i++
        }
        
        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString().trim())
            if (currentRow.any { it.isNotEmpty() }) {
                result.add(currentRow)
            }
        }
        return result
    }

    fun toCSV(headers: List<String>, data: List<List<String>>): String {
        val sb = StringBuilder()
        sb.append(headers.joinToString(",") { escapeCSV(it) }).append("\r\n")
        for (row in data) {
            sb.append(row.joinToString(",") { escapeCSV(it) }).append("\r\n")
        }
        return sb.toString()
    }

    private fun escapeCSV(value: String): String {
        var escaped = value.replace("\"", "\"\"")
        if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\r") || escaped.contains("\"")) {
            escaped = "\"$escaped\""
        }
        return escaped
    }
}

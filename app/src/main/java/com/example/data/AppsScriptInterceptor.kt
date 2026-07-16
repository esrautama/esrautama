package com.example.data

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

class AppsScriptInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        
        if (response.isSuccessful) {
            val body = response.body
            if (body != null) {
                val contentType = body.contentType()
                val isHtml = contentType?.subtype?.contains("html") == true || contentType?.type?.contains("html") == true
                
                // Read a bit of the body to see if it's actually HTML when content type might not be accurate
                val source = body.source()
                source.request(Long.MAX_VALUE) // Buffer the entire body.
                val buffer = source.buffer
                val bodyString = buffer.clone().readUtf8()
                
                if (isHtml || bodyString.trim().startsWith("<")) {
                    throw IOException("Invalid response from Google Apps Script. Please ensure you are using the correct Web App URL ending in '/exec' and that it is deployed with access 'Anyone'.")
                }
            }
        }
        return response
    }
}

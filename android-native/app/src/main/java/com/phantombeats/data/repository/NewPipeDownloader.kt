package com.phantombeats.data.repository

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NewPipeRequest
import org.schabi.newpipe.extractor.downloader.Response as NewPipeResponse
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewPipeDownloader @Inject constructor() : Downloader() {

    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: NewPipeRequest): NewPipeResponse {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder = Request.Builder()
            .method(httpMethod, dataToSend?.toRequestBody())
            .url(url)

        headers.forEach { (key, values) ->
            if (values.size > 1) {
                requestBuilder.removeHeader(key)
                values.forEach { value ->
                    requestBuilder.addHeader(key, value)
                }
            } else if (values.size == 1) {
                requestBuilder.header(key, values[0])
            }
        }

        val response = client.newCall(requestBuilder.build()).execute()

        val responseHeaders = mutableMapOf<String, List<String>>()
        response.headers.names().forEach { name ->
            responseHeaders[name] = response.headers.values(name)
        }

        return NewPipeResponse(
            response.code,
            response.message,
            responseHeaders,
            response.body?.string() ?: "",
            response.request.url.toString()
        )
    }
}

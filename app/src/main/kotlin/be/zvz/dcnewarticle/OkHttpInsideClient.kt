package be.zvz.dcnewarticle

import be.zvz.kotlininside.http.HttpException
import be.zvz.kotlininside.http.HttpInterface
import okhttp3.* // ktlint-disable no-wildcard-imports
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.EMPTY_REQUEST
import org.apache.tika.Tika
import org.apache.tika.mime.MimeTypes
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class OkHttpInsideClient(private val okHttpClient: OkHttpClient) : HttpInterface {
    private val tika = Tika()

    private inline fun <T1 : Any, T2 : Any, R : Any> safeLet(p1: T1?, p2: T2?, block: (T1, T2) -> R?): R? {
        return if (p1 != null && p2 != null) block(p1, p2) else null
    }

    private fun optionToUrl(url: String, option: HttpInterface.Option?): String {
        if (option == null) return url
        var replacedUrl = url
        option.pathParameter.forEach { (key, value) ->
            replacedUrl = replacedUrl.replace("{$key}", URLEncoder.encode(value, StandardCharsets.UTF_8))
        }
        var firstKey = false

        return StringBuilder(replacedUrl).apply {
            option.queryParameter.forEach { (key, value) ->
                if (!firstKey) {
                    firstKey = true
                    append("?")
                } else {
                    append("&")
                }
                    .append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8))
            }
        }.toString()
    }

    private fun getRequestFormBody(
        option: HttpInterface.Option
    ): RequestBody = safeLet(option.body, option.contentType) { body, contentType ->
        body.toRequestBody(contentType.toMediaType())
    } ?: FormBody.Builder().apply { option.bodyParameter.forEach(::add) }.build()

    private fun getRequestMultipartBody(
        option: HttpInterface.Option
    ): RequestBody = safeLet(option.body, option.contentType) { body, contentType ->
        body.toRequestBody(contentType.toMediaType())
    } ?: run {
        MultipartBody.Builder()
            .setType(MultipartBody.FORM).apply {
                option.multipartParameter.forEach(::addFormDataPart)
                var index = 0
                option.multipartFile.forEach { (key, value) ->
                    addToPart(this, key, value, index++)
                }
                option.multipartFileList.forEach { (key, value) ->
                    value.forEach {
                        addToPart(this, key, it, index++)
                    }
                }
            }.build()
    }

    private fun addToPart(
        multipartBodyBuilder: MultipartBody.Builder,
        key: String,
        fileInfo: HttpInterface.Option.FileInfo,
        infix: Int
    ) {
        fileInfo.mimeType?.let { mimeType ->
            multipartBodyBuilder.addFormDataPart(
                key,
                "image$infix${fromMimeTypeToExtension(mimeType)}",
                fileInfo.stream.readAllBytes().toRequestBody(mimeType.toMediaType())
            )
        } ?: run {
            val (contentType, fileName) = getFileInfoFromStream(fileInfo.stream, infix)
            multipartBodyBuilder.addFormDataPart(
                key,
                fileName,
                fileInfo.stream.readAllBytes().toRequestBody(contentType.toMediaType())
            )
        }
    }

    override fun get(url: String, option: HttpInterface.Option?): String {
        val request = Request.Builder()
            .url(optionToUrl(url, option))
            .header("Connection", "Keep-Alive")
            .apply {
                option?.let {
                    it.headers.forEach(::header)
                    it.userAgent?.let { userAgent ->
                        header("User-Agent", userAgent)
                    }
                }
            }
            .get()
            .build()
        return okHttpClient.newCall(request).execute().use {
            if (it.isSuccessful) {
                it.body.string()
            } else {
                throw HttpException(it.code, it.message)
            }
        }
    }

    override fun post(url: String, option: HttpInterface.Option?): String {
        val request = Request.Builder()
            .url(optionToUrl(url, option))
            .header("Connection", "Keep-Alive")
            .apply {
                option?.let {
                    it.headers.forEach(::header)
                    it.userAgent?.let { userAgent ->
                        header("User-Agent", userAgent)
                    }
                    post(getRequestFormBody(it))
                } ?: post(EMPTY_REQUEST)
            }
            .build()
        return okHttpClient.newCall(request).execute().use {
            if (it.isSuccessful) {
                it.body.string()
            } else {
                throw HttpException(it.code, it.message)
            }
        }
    }

    override fun delete(url: String, option: HttpInterface.Option?): String? {
        TODO("Not yet implemented")
    }

    override fun head(url: String, option: HttpInterface.Option?): String? {
        TODO("Not yet implemented")
    }

    override fun put(url: String, option: HttpInterface.Option?): String? {
        TODO("Not yet implemented")
    }

    override fun patch(url: String, option: HttpInterface.Option?): String? {
        TODO("Not yet implemented")
    }

    override fun upload(url: String, option: HttpInterface.Option?): String? {
        val request = Request.Builder()
            .url(optionToUrl(url, option))
            .header("Connection", "Keep-Alive")
            .apply {
                option?.let {
                    it.headers.forEach(::header)
                    it.userAgent?.let { userAgent ->
                        header("User-Agent", userAgent)
                    }
                    post(getRequestMultipartBody(it))
                } ?: post(EMPTY_REQUEST)
            }
            .build()
        return okHttpClient.newCall(request).execute().use {
            if (it.isSuccessful) {
                it.body.string()
            } else {
                throw HttpException(it.code, it.message)
            }
        }
    }

    private data class FileInfo(
        val contentType: String,
        val fileName: String
    )

    private fun fromMimeTypeToExtension(contentType: String?): String =
        MimeTypes.getDefaultMimeTypes().forName(contentType ?: DEFAULT_MIME_TYPE).extension

    private fun getFileInfoFromStream(inputStream: InputStream, infix: Int): FileInfo {
        val contentType = try {
            tika.detect(inputStream)
        } catch (_: IOException) {
            DEFAULT_MIME_TYPE
        }
        return FileInfo(
            contentType,
            "image$infix${fromMimeTypeToExtension(contentType)}"
        )
    }

    companion object {
        const val DEFAULT_MIME_TYPE = "video/mp4"
    }
}

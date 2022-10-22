/**
 * Copyright (c) Chavin(chavinchen@hotmail.com). 20019—2022. All rights reserved.
 */
package com.chavin.util.http

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

/**
 * 日志记录
 *
 * Created by chavin(chavinchen@hotmail.com) on 2022-10-22 10:50.
 */
open class ChvRequest(url: String, connTime: Int, readTime: Int) : Closeable {
    var throwable: Throwable? = null
    private val mConn: HttpURLConnection? = try {
        (URL(url).openConnection() as HttpURLConnection).apply {
            this.connectTimeout = connTime
            this.readTimeout = readTime
            this.doInput = true
        }
    } catch (e: Exception) {
        throwable = e
        null
    }
    private var mRespCode: Int? = null
    private var mRespMessage: String? = null
    private val mRespHeader = HashMap<String, String>()

    /**
     * 设置请求方法
     */
    fun method(m: String): ChvRequest {
        mConn?.requestMethod = m
        return this
    }

    /**
     * 设置请求头
     */
    fun header(header: Map<String, String?>): ChvRequest {
        if (header.isEmpty()) return this
        val conn = mConn ?: return this
        val entrySet: Set<Map.Entry<String, String?>> = header.entries
        for ((key, value) in entrySet) {
            if (key.isNotEmpty() && null != value) {
                conn.addRequestProperty(key, value)
            }
        }
        return this
    }

    /**
     * 设置请求体
     */
    fun data(data: ByteArray?): ChvRequest {
        val raw = data ?: return this
        val conn = mConn ?: return this
        conn.doOutput = true

        try {
            conn.outputStream.use { writer ->
                writer.write(raw)
                writer.flush()
            }
        } catch (e: IOException) {
            ChvLog.log(TAG, "write data error", e)
            throwable = e
        }
        return this
    }

    /**
     * 发起请求
     */
    fun connect(): Boolean {
        val conn = mConn ?: return false
        try {
            conn.connect()
        } catch (e: IOException) {
            ChvLog.log(TAG, "connect error", e)
            throwable = e
            return false
        }
        return true
    }

    /**
     * 获取Http状态码
     */
    fun statusCode(): Int {
        val code = try {
            mConn?.responseCode
        } catch (e: IOException) {
            ChvLog.log(TAG, "get response code error", e)
            throwable = e
            null
        }
        if (null == code || CODE_EXCEPTION == code) {
            if (null == mRespCode) {
                header()
            }
        } else {
            mRespCode = code
        }
        return mRespCode ?: CODE_EXCEPTION
    }

    /**
     * 获取Http消息
     */
    fun message(): String? {
        val msg = try {
            mConn?.responseMessage
        } catch (e: IOException) {
            ChvLog.log(TAG, "get response message error", e)
            throwable = e
            null
        }
        if (null == msg) {
            if (null == mRespMessage) {
                header()
            }
        } else {
            mRespMessage = msg
        }
        return mRespMessage
    }

    /**
     * 获取Http响应头
     */
    fun header(): Map<String, String> {
        if (mRespHeader.isNotEmpty()) {
            return mRespHeader
        }
        val conn = mConn ?: return mRespHeader

        val source: Map<String?, List<String>> = try {
            conn.headerFields
        } catch (ignore: IOException) {
            Collections.emptyMap()
        }
        val entrySet = source.entries
        var statusLine = EMPTY_STR
        for ((key, value) in entrySet) {
            if (!key.isNullOrEmpty()) {
                mRespHeader[key] = value.joinToString("; ")
            } else if (value.isNotEmpty() && value[0].isNotEmpty()) {
                // Key为空，是状态行
                statusLine = value[0]
            }
        }
        // code & msg from status line
        if (statusLine.startsWith("HTTP/")) {
            val codePos: Int = statusLine.indexOf(' ')
            if (codePos > 0) {
                var phrasePos: Int = statusLine.indexOf(' ', codePos + 1)
                if (phrasePos > 0 && phrasePos < statusLine.length && null == mRespMessage) {
                    mRespMessage = statusLine.substring(phrasePos + 1)
                }
                if (phrasePos < 0) phrasePos = statusLine.length
                if (null == mRespCode) {
                    try {
                        mRespCode = statusLine.substring(codePos + 1, phrasePos).toInt()
                    } catch (ignore: NumberFormatException) {
                    }
                }
            }
        }
        return mRespHeader
    }

    /**
     * 获取Http响应体
     */
    fun body(): InputStream? {
        return try {
            mConn?.inputStream
        } catch (e: IOException) {
            ChvLog.log(TAG, "get input stream error", e)
            throwable = e
            null
        }
    }

    override fun close() {
        val conn = mConn ?: return
        conn.disconnect()
    }

    companion object {
        const val EMPTY_STR = ""
        const val CODE_EXCEPTION = -1
        private const val TAG = "CV-Req"
    }

}
/**
 * Copyright (c) Chavin(chavinchen@hotmail.com). 20019—2022. All rights reserved.
 */
package com.chavin.util.http

import org.json.JSONObject
import java.io.Closeable
import java.net.URLEncoder
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 网络请求的异步包装器
 *
 * Created by chavin(chavinchen@hotmail.com) on 2019-06-27 14:39.
 */
object ChvHttpUtil {
    const val CODE_EXCEPTION = ChvRequest.CODE_EXCEPTION
    const val METHOD_POST = "POST"
    const val METHOD_GET = "GET"

    private const val TAG = "CV-Http"
    private var CONNECT_TIMEOUT_MS = 3000
    private var READ_TIMEOUT_MS = 5000
    private val mLock = Object()
    private var mAsyncExecutor: Executor? = null
    private var mCallbackExecutor: Executor? = null

    /**
     * 配置
     *
     * @param connectTimeoutMs 最大连接时间
     * @param readTimeoutMs    最大读取时间
     * @param asyncExecutor    异步线程池
     */
    fun config(connectTimeoutMs: Int, readTimeoutMs: Int, asyncExecutor: Executor?, callbackExecutor: Executor?) {
        CONNECT_TIMEOUT_MS = connectTimeoutMs
        READ_TIMEOUT_MS = readTimeoutMs
        mAsyncExecutor = asyncExecutor
        mCallbackExecutor = callbackExecutor
    }

    /**
     * 异步 GET 请求
     */
    fun get(url: String, callback: Callback): Closeable {
        return get(url, null, callback)
    }

    /**
     * 异步 GET 请求
     */
    fun get(url: String, args: JSONObject?, callback: Callback): Closeable {
        return get(url, HashMap<String, String?>().also { it["Connection"] = "Keep-Alive"; }, args, callback)
    }

    /**
     * 异步 GET 请求
     */
    fun get(
        url: String, header: Map<String, String?>?, args: JSONObject?,
        callback: Callback
    ): Closeable {
        val delayed = DelayedCloseable()
        connectAsync({
            val req = (object : ChvRequest(buildUrlWithArgs(url, args), CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS) {
                override fun close() {
                    super.close()
                    it.run()
                }
            }).method(METHOD_GET)
            header?.let {
                req.header(it)
            }
            req
        }, delayed, callback)
        return delayed
    }

    /**
     * 异步 POST 请求
     */
    fun post(url: String, args: JSONObject?, callback: Callback): Closeable {
        return post(url, HashMap<String, String?>().also { it["Connection"] = "Keep-Alive"; }, args, callback)
    }

    /**
     * 异步 POST 请求
     */
    fun post(
        url: String, header: Map<String, String?>?, args: JSONObject?,
        callback: Callback
    ): Closeable {
        val delayed = DelayedCloseable()
        connectAsync({
            val req = (object : ChvRequest(url, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS) {
                override fun close() {
                    super.close()
                    it.run()
                }
            }).method(METHOD_POST)
            header?.let {
                req.header(it)
            }
            args?.let {
                req.data(it.toString().toByteArray())
            }
            req
        }, delayed, callback)
        return delayed
    }


    // =================================== PRIVATE =================================================
    // 将JSON参数添加到URL中
    private fun buildUrlWithArgs(url: String, args: JSONObject?): String {
        if (null == args || !args.keys().hasNext()) {
            return url
        }
        val builder = StringBuilder(url)
        var needPrefix = if (-1 != url.indexOf('?')) {
            builder.append('?')
            false
        } else true
        val it = args.keys()
        var k: String?
        var v: String?
        while (it.hasNext()) {
            k = it.next()
            if (k.isNullOrEmpty()) continue
            if (needPrefix) builder.append("&")
            v = args.optString(k)
            builder.append(URLEncoder.encode(k, Charsets.UTF_8.name()))
                .append("=").append(URLEncoder.encode(v, Charsets.UTF_8.name()))
            needPrefix = true
        }
        return builder.toString()
    }

    private fun connectAsync(
        buildRequest: (onClose: Runnable) -> ChvRequest,
        closeable: DelayedCloseable,
        callback: Callback
    ) {
        if (null == mAsyncExecutor) {
            synchronized(mLock) {
                if (null == mAsyncExecutor) {
                    val cpuCnt: Int = Runtime.getRuntime().availableProcessors()
                    val corePoolSize: Int = Math.max(2, Math.min(cpuCnt - 1, 4))
                    val maximumPoolSize: Int = cpuCnt * 2 + 1
                    val keepAliveSec = 30
                    val threadPoolExecutor = ThreadPoolExecutor(
                        corePoolSize, maximumPoolSize, keepAliveSec.toLong(), TimeUnit.SECONDS,
                        LinkedBlockingQueue(128)
                    )
                    threadPoolExecutor.allowCoreThreadTimeOut(true)
                    mAsyncExecutor = threadPoolExecutor
                }
            }
        }
        mAsyncExecutor!!.execute {
            ChvLog.log(TAG, "request start.")
            if (closeable.finished.get()) {
                ChvLog.log(TAG, "request finished.canceled")
                callbackCanceled(callback)
                return@execute
            }
            val request = buildRequest {
                ChvLog.log(TAG, "request finished.canceled")
                callbackCanceled(callback)
            }.also { closeable.target = it }
            if (request.connect()) {
                ChvLog.log(TAG, "request finished.succeed")
                closeable.finished.compareAndSet(false, true)
                callSucceed(
                    callback,
                    request.statusCode(), request.message(),
                    request.header(), request.body()?.readBytes()?.decodeToString() ?: ChvRequest.EMPTY_STR
                )
                request.body()?.close()
            } else {
                ChvLog.log(TAG, "request finished.failed")
                closeable.finished.compareAndSet(false, true)
                callbackFailed(
                    callback,
                    request.statusCode(), request.message(), request.throwable
                )
            }
        }
    }

    private fun callbackCanceled(callback: Callback) {
        mCallbackExecutor?.let {
            it.execute { callback.onCanceled() }
        } ?: callback.onCanceled()
    }

    private fun callbackFailed(
        callback: Callback, code: Int, status: String?,
        e: Throwable?
    ) {
        mCallbackExecutor?.let {
            it.execute { callback.onFailed(code, status, e) }
        } ?: callback.onFailed(code, status, e)

    }

    private fun callSucceed(
        callback: Callback, code: Int, status: String?,
        header: Map<String, String>, body: String
    ) {
        mCallbackExecutor?.let {
            it.execute { callback.onSucceed(code, status, header, body) }
        } ?: callback.onSucceed(code, status, header, body)
    }

    private class DelayedCloseable : Closeable {
        var finished = AtomicBoolean(false)
        var target: Closeable? = null
        override fun close() {
            if (finished.compareAndSet(false, true)) {
                target?.close()
            }
        }
    }

    interface Callback {
        fun onCanceled() {}
        fun onSucceed(code: Int, status: String?, header: Map<String, String>, body: String?)
        fun onFailed(code: Int, status: String?, e: Throwable?)
    }
}
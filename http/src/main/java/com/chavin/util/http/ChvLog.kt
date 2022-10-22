/**
 * Copyright (c) Chavin(chavinchen@hotmail.com). 20019—2022. All rights reserved.
 */
package com.chavin.util.http

/**
 * 日志记录
 *
 * Created by chavin(chavinchen@hotmail.com) on 2022-10-22 10:50.
 */
object ChvLog {
    private var doLog = true

    fun doLog(log: Boolean) {
        doLog = log
    }


    fun log(tag: String, msg: String, e: Throwable? = null) {
        if (!doLog) return
        var lineNumber = -1
        val buffer = StringBuilder()
        var fileName = "Unknown-File"
        var methodName = "Unknown"
        val sElements = Throwable().stackTrace
        if (sElements.size > 2) {
            fileName = sElements[2].fileName ?: fileName
            methodName = sElements[2].methodName ?: methodName
            lineNumber = sElements[2].lineNumber
        }
        val t = if ("main" == Thread.currentThread().name) '★' else '☆'
        buffer.append(t).append(methodName).append('(').append(fileName)
            .append(":").append(lineNumber).append(") $tag")
        val formatTag = buffer.toString();
        println("$formatTag $msg")
        e?.printStackTrace()
    }
}
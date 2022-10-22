package com.chavin.util.http;

import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONObject;

import java.io.Closeable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by chenchangwen on 2019-06-27 14:39.
 */
public class HttpUtil {
    public static final int CODE_EXCEPTION = -1;
    private static final String TAG = "HttpUtil";

    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String METHOD_POST = "POST";
    private static final String METHOD_GET = "GET";

    static int CONNECT_TIMEOUT_MS = 3000;
    static int READ_TIMEOUT_MS = 5000;

    private static Executor mExecutor;

    @WorkerThread
    public interface Callback {
        void onSucceed(int code, String status, JSONObject header, String body);

        void onFailed(int code, String status, Throwable e);
    }

    /**
     * 配置
     *
     * @param connectTimeoutMs 最大连接时间
     * @param readTimeoutMs    最大读取时间
     * @param executor         异步线程池
     */
    public static void config(int connectTimeoutMs, int readTimeoutMs, Executor executor) {
        CONNECT_TIMEOUT_MS = connectTimeoutMs;
        READ_TIMEOUT_MS = readTimeoutMs;
        mExecutor = executor;
    }

    public static Closeable get(String url, Callback callback) {
        return get(url, null, callback);
    }

    public static Closeable get(String url, JSONObject args, Callback callback) {
        return get(url, new HashMap<String, String>() {{
            put("Connection", "Keep-Alive");
        }}, args, callback);
    }


    public static Closeable get(String url, Map<String, String> header, JSONObject args,
                                Callback callback) {
        Request request = new Request(buildUrlWithArgs(url, args));
        if (null == request.mConnection) {
            callbackFailed(callback,
                    request.getStatusCode(), request.getStatusLine(), request.mThrowable);
            return request;
        }
        request.setMethod(METHOD_GET);

        request.addHeaders(header);

        connectAsync(request, callback);

        return request;
    }

    public static Closeable post(String url, JSONObject args, Callback callback) {
        return post(url, new HashMap<String, String>() {{
            put("Connection", "Keep-Alive");
        }}, args, callback);
    }

    public static Closeable post(String url, Map<String, String> header, JSONObject args,
                                 Callback callback) {
        Request request = new Request(url);
        if (null == request.mConnection) {
            callbackFailed(callback,
                    request.getStatusCode(), request.getStatusLine(), request.mThrowable);
            return request;
        }
        request.setMethod(METHOD_POST);

        request.addHeaders(header);

        request.addArgs(args);

        connectAsync(request, callback);

        return request;
    }

    static void appendArgs(StringBuilder builder, Iterator<String> keyIterator,
                                   JSONObject args, boolean needPrefix) {
        String key;
        String val;
        while (keyIterator.hasNext()) {
            key = keyIterator.next();
            if (TextUtils.isEmpty(key)) {
                continue;
            }
            if (needPrefix) {
                builder.append("&");
            }
            val = args.optString(key);
            builder.append(encode(key))
                    .append("=").append(encode(val));
            needPrefix = true;
        }
    }

    // =================================== PRIVATE =================================================

    private static String buildUrlWithArgs(String url, JSONObject args) {
        Iterator<String> keyIterator;
        if (null == args || null == (keyIterator = args.keys()) || keyIterator.hasNext()) {
            return url;
        }

        StringBuilder builder = new StringBuilder(url);
        boolean needPrefix = true;
        if (-1 != url.indexOf('?')) {
            builder.append('?');
            needPrefix = false;
        }
        appendArgs(builder, keyIterator, args, needPrefix);
        return builder.toString();
    }



    private static void connectAsync(final Request request, final Callback callback) {
        Log.d(TAG, "connectAsync: " + request + ", " + callback);
        if (null == mExecutor) {
            synchronized (HttpUtil.class) {
                if (null == mExecutor) {
                    int CPU_COUNT = Runtime.getRuntime().availableProcessors();
                    int CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 4));
                    int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
                    int KEEP_ALIVE_SECONDS = 30;
                    ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                            CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                            new LinkedBlockingQueue<Runnable>(128));
                    threadPoolExecutor.allowCoreThreadTimeOut(true);
                    mExecutor = threadPoolExecutor;
                }
            }
        }
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "connect start.");
                request.connect();
                Log.d(TAG, "connect finished.");
                if (request.isRequestFailed()) {
                    callbackFailed(callback,
                            request.getStatusCode(), request.getStatusLine(), request.mThrowable);
                } else {
                    callSucceed(callback,
                            request.getStatusCode(), request.getStatusLine(),
                            request.getHeaders(), request.getBody());
                }
            }
        });
    }

    private static String encode(String input) {
        try {
            return URLEncoder.encode(input, HttpUtil.CHARSET_UTF8);
        } catch (UnsupportedEncodingException e) {
            return input;
        }
    }

    private static void callbackFailed(Callback callback, int code, String status,
                                       Throwable e) {
        if (null == callback) {
            return;
        }
        callback.onFailed(code, status, e);
    }

    private static void callSucceed(Callback callback, int code, String status,
                                    JSONObject header, String body) {
        if (null == callback) {
            return;
        }
        callback.onSucceed(code, status, header, body);
    }

}

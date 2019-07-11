package com.chavin.util.http;

import android.text.TextUtils;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Request implements Closeable {
    private static final String TAG = "HttpUtil.Request";
    private static final String STATUS_REQUEST_FAILED = "HTTP/0.0 -1 request_failed";
    private static final String EMPTY_STRING = "";
    private static final JSONObject EMPTY_JSON = new JSONObject();

    HttpURLConnection mConnection;
    Throwable mThrowable;

    private int mStatusCode;
    private String mStatusLine;
    private JSONObject mHeaders;
    private String mBody;

    private boolean mRequestFailed;

    Request(String url) {
        try {
            mConnection = (HttpURLConnection) new URL(url).openConnection();

            mConnection.setConnectTimeout(HttpUtil.CONNECT_TIMEOUT_MS);
            mConnection.setReadTimeout(HttpUtil.READ_TIMEOUT_MS);

            mConnection.setDoInput(true);
            mConnection.setDoOutput(true);

        } catch (Exception e) {
            requestFailed(e);
        }
    }

    void setMethod(String method) {
        if (null == mConnection) {
            return;
        }
        try {
            mConnection.setRequestMethod(method);
        } catch (ProtocolException e) {
            requestFailed(e);
        }
    }

    void addHeaders(Map<String, String> header) {
        if (null == mConnection || null == header || header.isEmpty()) {
            return;
        }
        boolean noConnection = true;
        Set<Map.Entry<String, String>> entrySet = header.entrySet();
        for (Map.Entry<String, String> entry : entrySet) {
            if (!TextUtils.isEmpty(entry.getKey()) && null != entry.getValue()) {
                mConnection.addRequestProperty(entry.getKey(), entry.getValue());
                if (entry.getKey().equalsIgnoreCase("connection")) {
                    noConnection = false;
                }
            }
        }
        if (noConnection) {
            // connection keep-alive
            mConnection.addRequestProperty("Connection", "Keep-Alive");
        }
    }

    void addArgs(JSONObject args) {
        Iterator<String> keyIterator;
        if (null == mConnection || null == args
                || null == (keyIterator = args.keys())
                || keyIterator.hasNext()) {
            return;
        }
        StringBuilder builder = new StringBuilder();

        HttpUtil.appendArgs(builder, keyIterator, args, false);

        try (OutputStreamWriter writer = new OutputStreamWriter(mConnection.getOutputStream())) {
            writer.write(builder.toString());
            writer.flush();
        } catch (IOException e) {
            requestFailed(e);
        }

    }

    void connect() {
        if (null == mConnection) {
            return;
        }
        try {
            mConnection.connect();
        } catch (IOException e) {
            requestFailed(e);
            return;
        }
        requestSucceed();
    }

    boolean isRequestFailed() {
        return mRequestFailed;
    }

    int getStatusCode() {
        if (null == mConnection || 0 != mStatusCode) {
            return mStatusCode;
        }
        try {
            tryGetStatusCode();
        } catch (IOException e) {
            requestFailed(e);
        }
        return mStatusCode;
    }

    String getStatusLine() {
        if (null == mConnection || !TextUtils.isEmpty(mStatusLine)) {
            return mStatusLine;
        }
        getHeaders();
        return mStatusLine;
    }

    JSONObject getHeaders() {
        if (null == mConnection || null != mHeaders) {
            return mHeaders;
        }
        tryGetHeaders();
        return mHeaders;
    }

    String getBody() {
        if (null == mConnection || !TextUtils.isEmpty(mBody)) {
            return mBody;
        }
        try {
            tryGetBody();
        } catch (IOException e) {
            requestFailed(e);
        }
        return mBody;
    }

    @Override
    public void close() {
        Log.d(TAG, "close: ");
        if (null != mConnection) {
            mConnection.disconnect();
            mConnection = null;
        }
    }

    private void requestFailed(Throwable throwable) {

        try {
            tryGetStatusCode();
        } catch (IOException e) {
            mStatusCode = HttpUtil.CODE_EXCEPTION;
        }
        tryGetHeaders();
        if (TextUtils.isEmpty(mStatusLine)) {
            mStatusLine = STATUS_REQUEST_FAILED;
        }
        if (null == mHeaders) {
            mHeaders = EMPTY_JSON;
        }
        try {
            tryGetBody();
        } catch (IOException e) {
            mBody = EMPTY_STRING;
        }
        mThrowable = throwable;
        mRequestFailed = true;

        close();
    }

    private void requestSucceed() {
        getStatusCode();
        getStatusLine();
        getHeaders();
        getBody();
        mRequestFailed = false;
    }

    private void tryGetStatusCode() throws IOException {
        if (null == mConnection) {
            return;
        }
        mStatusCode = mConnection.getResponseCode();
    }

    private void tryGetHeaders() {
        if (null == mConnection) {
            return;
        }
        Map<String, List<String>> source = mConnection.getHeaderFields();
        Map<String, List<String>> map = new HashMap<>();
        Set<Map.Entry<String, List<String>>> entrySet = source.entrySet();
        for (Map.Entry<String, List<String>> entry : entrySet) {
            if (!TextUtils.isEmpty(entry.getKey())) {
                map.put(entry.getKey(), entry.getValue());
            } else {
                mStatusLine = entry.getValue().get(0);
            }
        }
        mHeaders = new JSONObject(map);
    }

    private void tryGetBody() throws IOException {
        if (null == mConnection) {
            return;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(
                    new InputStreamReader(mConnection.getInputStream()));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            mBody = builder.toString();
        } finally {
            if (null != reader) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
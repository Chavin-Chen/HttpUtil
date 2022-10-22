package com.chavin.util;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.chavin.util.http.HttpUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Closeable;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "chavin";
    private TextView mTextView;
    private Closeable mGet;
    private Closeable mPost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextView = findViewById(R.id.text);
    }

    public void clickGet(View view) {
        mTextView.setText("Requesting...");
        if(null != mGet){
            try {
                mGet.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mGet = HttpUtil.get("https://www.wanandroid.com/banner/json", new HttpUtil.Callback() {
            @Override
            public void onSucceed(final int code, final String status, final JSONObject header, final String body) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTextView.setText((code + " : " + status + "\n" + header + "\n\n" + body));
                    }
                });
            }

            @Override
            public void onFailed(final int code, final String status, final Throwable e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTextView.setText((code + " : " + status + "\n" +e.getMessage() +"\n" + Log.getStackTraceString(e)));
                    }
                });
            }
        });
    }

    public void clickPost(View view) {
        mTextView.setText("Requesting...");
        if(null != mPost){
            try {
                mPost.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mPost = HttpUtil.post("https://www.wanandroid.com/user/login",
                new JSONObject(){{
                    try {
                        put("username", "test0");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }}, new HttpUtil.Callback() {
                    @Override
                    public void onSucceed(final int code, final String status, final JSONObject header, final String body) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mTextView.setText((code + " : " + status + "\n" + header + "\n\n" + body));
                            }
                        });
                    }

                    @Override
                    public void onFailed(final int code, final String status, final Throwable e) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mTextView.setText((code + " : " + status + "\n" +e.getMessage() +"\n" + Log.getStackTraceString(e)));
                            }
                        });
                    }
                });
    }

}

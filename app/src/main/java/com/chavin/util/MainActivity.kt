package com.chavin.util

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.chavin.util.http.ChvHttpUtil
import com.chavin.util.http.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import java.io.Closeable
import java.io.IOException

@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity() {
    private val mTextView: TextView by lazy {
        findViewById(R.id.text)
    }

    private var mGet: Closeable? = null
    private var mPost: Closeable? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ChvHttpUtil.config(5000, 15000, {
            GlobalScope.launch(Dispatchers.IO) {
                it.run()
            }
        }) {
            MainScope().launch {
                it.run()
            }
        }
    }

    fun clickGet(view: View?) {
        mTextView.text = "Requesting..."
        mGet?.let {
            try {
                it.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        mGet = ChvHttpUtil.get(
            "https://www.wanandroid.com/banner/json",
            object : ChvHttpUtil.Callback {

                override fun onSucceed(code: Int, status: String?, header: Map<String, String>, body: String?) {
                    mTextView.text = "$code : $status\n$header\n\n$body"
                }

                override fun onFailed(code: Int, status: String?, e: Throwable?) {
                    mTextView.text = "$code : $status\n${e?.message}\n${Log.getStackTraceString(e)}"
                }
            })
    }

    fun clickPost(view: View?) {
        mTextView.text = "Requesting..."
        mPost?.let {
            try {
                it.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        mPost = ChvHttpUtil.post("https://www.wanandroid.com/user/login",
            object : JSONObject() {
                init {
                    try {
                        put("username", "test0")
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            }, object : ChvHttpUtil.Callback {
                override fun onSucceed(code: Int, status: String?, header: Map<String, String>, body: String?) {
                    mTextView.text = "$code : $status\n$header\n\n$body"
                }

                override fun onFailed(code: Int, status: String?, e: Throwable?) {
                    mTextView.text = "$code : $status\n${e?.message}\n${Log.getStackTraceString(e)}"
                }
            })
    }
}
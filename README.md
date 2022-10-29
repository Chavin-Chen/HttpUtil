# What is this?

This is a light network library based on HttpUrlConnection.

It may be useful when you write some tools in dev env but cannot use other lib like OkHttp.


## Usage

You can use it in any JVM project, it's a java-library.

```groovy
repositories {
    mavenCentral()
}

dependencies {
    // If you use it not Android, and you need use async wrapper `com.chavin.util.http.ChvHttpUtil`
    // For that you should implementation this json library, otherwise delete the line below
    implementation 'org.json:json:20220924'
    implementation 'io.github.chavin-chen:util-http:1.0.0'
}
```

And then use sync request:

```java
public class Main {
    public static void main(String[] args) throws IOException {
        // A get request
        try (var req = new ChvRequest("https://www.baidu.com", 5000, 8000)) {
            var connected = req.method("GET").
                    header(new HashMap<>() {{
                        put("Connection", "Keep-Alive");
                    }})
                    .connect();
            if (connected) {
                req.statusCode(); // get http status code
                req.message();  // get http status line message like OK or File NOT Found
                InputStream input = req.body();
                System.out.println(new String(input.readAllBytes()));
                // do wtf you want through input stream
            } else {
                // connect failed
                req.getThrowable();
            }
        }
        // A post request
        try (var req = new ChvRequest("https://www.wanandroid.com/user/login", 5000, 8000)) {
            if (req.method("POST")
                    .header(new HashMap<>() {{
                        put("Content-Type", "application/json;charset=UTF-8");
                    }})
                    .data("{'username':'test0'}".getBytes(StandardCharsets.UTF_8))
                    .connect()) {
                System.out.println(new String(req.body().readAllBytes()));
            }
        }
    }
}
```

And also you can use async util, for example in android project:

```kotlin
// first config the util
ChvHttpUtil.config(5000, 15000, {
    // do request in io thread
    GlobalScope.launch(Dispatchers.IO) {
        it.run()
    }
}) {
    // set callback in main thread
    MainScope().launch {
        it.run()
    }
}

// an async get request
ChvHttpUtil.get("https://www.wanandroid.com/banner/json",
    object : ChvHttpUtil.Callback {

        override fun onSucceed(code: Int, status: String?, header: Map<String, String>, body: String?) {
            mTextView.text = "$code : $status\n$header\n\n$body"
        }

        override fun onFailed(code: Int, status: String?, e: Throwable?) {
            mTextView.text = "$code : $status\n${e?.message}\n${Log.getStackTraceString(e)}"
        }
    })

// an async post request, you can hold the closeable task, if you want close the request after requesting(eg. Activity#onDestroy())
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
```

## LICENSE

```plain

Copyright (c) 2019-present, HttpUtil Contributors.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

```

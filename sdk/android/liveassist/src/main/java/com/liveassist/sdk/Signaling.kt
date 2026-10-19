package com.liveassist.sdk

import android.os.Handler
import android.os.Looper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to the Live Video Assist server (sdk/PROTOCOL.md §1).
 * Tries a WebSocket first; if the server doesn't answer within 6 s (e.g. cPanel shared hosting blocks WebSockets)
 * it switches automatically to HTTP long-polling. All callbacks run on the main thread.
 */
internal class SignalingClient(serverUrl: String) {
    var onMessage: ((JSONObject) -> Unit)? = null
    var onClose: (() -> Unit)? = null
    var transport = "none"; private set

    private val base = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder()
        .readTimeout(40, TimeUnit.SECONDS)            // > 25 s long-poll wait
        .connectTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json".toMediaType()

    private var ws: WebSocket? = null
    private var gotFirstWsMessage = false
    private var token: String? = null
    private var closed = false
    private val outbox = ArrayList<JSONObject>()
    private var sending = false

    fun connect(hello: JSONObject) = tryWebSocket(hello)

    fun send(msg: JSONObject) {
        if (closed) return
        if (transport == "websocket") ws?.send(msg.toString())
        else { outbox.add(msg); flushOutbox() }
    }

    fun close() {
        if (closed) return
        token?.let { t ->
            if (transport == "polling") post("signal/close", JSONObject().put("token", t)) { _, _ -> }
        }
        ws?.close(1000, null)
        finish()
    }

    /** GET /config → ICE servers */
    fun fetchConfig(done: (JSONArray) -> Unit) {
        http.newCall(Request.Builder().url(base + "config").build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { done(defaultIce()) } }
            override fun onResponse(call: Call, response: Response) {
                val ice = runCatching { JSONObject(response.body!!.string()).getJSONArray("iceServers") }.getOrElse { defaultIce() }
                main.post { done(ice) }
            }
        })
    }

    private fun defaultIce() = JSONArray().put(JSONObject().put("urls", "stun:stun.l.google.com:19302"))

    // ---- WebSocket ----

    private fun tryWebSocket(hello: JSONObject) {
        val wsUrl = (base + "ws").replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
        val socket = http.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send(hello.toString()) }
            override fun onMessage(webSocket: WebSocket, text: String) {
                main.post {
                    if (ws !== webSocket || closed) return@post
                    if (!gotFirstWsMessage) { gotFirstWsMessage = true; transport = "websocket" }
                    runCatching { JSONObject(text) }.getOrNull()?.let { onMessage?.invoke(it) }
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                main.post { if (ws === webSocket) { if (gotFirstWsMessage) finish() else fallBack(hello) } }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                main.post { if (ws === webSocket) { if (gotFirstWsMessage) finish() else fallBack(hello) } }
            }
        })
        ws = socket
        main.postDelayed({ if (!gotFirstWsMessage && !closed && transport == "none") fallBack(hello) }, 6000)
    }

    private fun fallBack(hello: JSONObject) {
        if (transport != "none" || closed) return
        val old = ws; ws = null
        old?.cancel()
        startPolling(hello)
    }

    // ---- HTTP long-polling ----

    private fun startPolling(hello: JSONObject) {
        transport = "polling"
        post("signal/open", JSONObject()) { code, body ->
            val t = if (code == 200) runCatching { JSONObject(body).getString("token") }.getOrNull() else null
            if (t == null) { finish(); return@post }
            token = t
            outbox.add(0, hello)
            flushOutbox()
            poll(0)
        }
    }

    private fun poll(failures: Int) {
        val t = token ?: return
        if (closed) return
        val url = (base + "signal/poll").toHttpUrl().newBuilder().addQueryParameter("token", t).build()
        http.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { retry(failures) } }
            override fun onResponse(call: Call, response: Response) {
                val code = response.code
                val body = runCatching { response.body?.string() }.getOrNull()
                main.post {
                    if (closed) return@post
                    when {
                        code == 410 -> finish()
                        code != 200 || body == null -> retry(failures)
                        else -> {
                            runCatching { JSONArray(body) }.getOrNull()?.let { arr ->
                                for (i in 0 until arr.length()) { if (!closed) arr.optJSONObject(i)?.let { onMessage?.invoke(it) } }
                            }
                            poll(0)
                        }
                    }
                }
            }
        })
    }

    private fun retry(failures: Int) {
        if (closed) return
        if (failures >= 5) { finish(); return }
        main.postDelayed({ poll(failures + 1) }, 1000L * (failures + 1))
    }

    private fun flushOutbox() {
        val t = token ?: return
        if (transport != "polling" || sending || outbox.isEmpty() || closed) return
        val msgs = JSONArray(); outbox.forEach { msgs.put(it) }; outbox.clear()
        sending = true
        post("signal/send", JSONObject().put("token", t).put("msgs", msgs)) { code, _ ->
            sending = false
            if (code == 410) finish() else flushOutbox()
        }
    }

    private fun post(path: String, body: JSONObject, done: (Int, String) -> Unit) {
        val req = Request.Builder().url(base + path).post(body.toString().toRequestBody(jsonType)).build()
        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { done(0, "") } }
            override fun onResponse(call: Call, response: Response) {
                val text = runCatching { response.body?.string() ?: "" }.getOrDefault("")
                val code = response.code
                main.post { done(code, text) }
            }
        })
    }

    private fun finish() {
        if (closed) return
        closed = true
        ws = null
        onClose?.invoke()
    }
}

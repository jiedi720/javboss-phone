package com.javboss.phone.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * 401 闪退回归测试（v0.3.58）。
 *
 * 实机崩溃报告：`s3.b: 登录状态已失效` 在 OkHttp Dispatcher 线程未被捕获，
 * 冒泡到全局 UncaughtExceptionHandler 导致闪退。根因是 401 拦截器抛出的
 * AuthExpiredException 不是 IOException —— OkHttp 4.12.0 的 AsyncCall 只把
 * IOException 交给 onFailure，其它 Throwable 会被包装成 IOException 回调后
 * 原样重新抛到 Dispatcher 线程；同时 Retrofit 协程只能拿到包装后的 IOException，
 * UI 层 30+ 处 catch(AuthExpiredException) 也永远不会命中。
 *
 * 改成 IOException 子类后：走 onFailure -> 协程恢复原始 AuthExpiredException，
 * 既不崩溃，也能让 sessionExpired 正常置位、跳回登录页。
 */
class AuthExpiredExceptionTest {

    @Test
    fun `auth expired exception is IOException so OkHttp routes it to onFailure`() {
        val error: Throwable = AuthExpiredException()
        assertTrue(
            "401 异常必须是 IOException，否则 OkHttp AsyncCall 会在 Dispatcher 线程重新抛出并崩溃",
            error is IOException,
        )
    }

    @Test
    fun `auth expired exception keeps readable message`() {
        assertEquals("登录状态已失效", AuthExpiredException().message)
    }
}

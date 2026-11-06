package com.javboss.phone.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * 视频标签变更信号：详情页保存视频标签后通知视频列表刷新，
 * 让列表卡片左上角的标签与服务端保持一致（对齐 Web 端保存后立即更新列表的行为）。
 * 只在进程内广播，不持久化、不携带数据。
 */
object VideoTagEvents {
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: SharedFlow<Unit> = _changes

    fun notifyChanged() {
        _changes.tryEmit(Unit)
    }
}

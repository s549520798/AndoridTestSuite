package priv.lzy.andtestsuite.utils

import android.os.Handler
import android.os.Looper

interface IThreadHandler {

    fun post(runnable: Runnable): Boolean

    fun postDelayed(runnable: Runnable, millis: Long): Boolean

    fun removeCallbacks(runnable: Runnable)
}

class ATSMainHandler : IThreadHandler {

    private val mHandler = Handler(Looper.getMainLooper())

    override fun post(runnable: Runnable): Boolean {
        return mHandler.post(runnable)
    }

    override fun postDelayed(runnable: Runnable, millis: Long): Boolean {
        return mHandler.postDelayed(runnable, millis)
    }

    override fun removeCallbacks(runnable: Runnable) {
        mHandler.removeCallbacks(runnable)
    }

}
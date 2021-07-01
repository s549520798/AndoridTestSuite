package priv.lzy.andtestsuite

import android.app.Application
import android.content.Context
import android.util.Log
import com.github.megatronking.netbare.NetBare
import com.github.megatronking.netbare.NetBareListener
import com.github.megatronking.netbare.log.NetBareLogListener
import priv.lzy.andtestsuite.utils.LogUtil

class ATSApplication : Application() {
    private val mTag = "ATSApplication"

    companion object {
        const val JSK_ALIAS = "AndroidTestSuite"

        private lateinit var sInstance: ATSApplication

        fun getInstance(): Application {
            return sInstance
        }
    }

    override fun onCreate() {
        super.onCreate()
        LogUtil.i(mTag, "onCreate")
        sInstance = this

        // 初始化NetBare
        NetBare.setOnNetBareLogListener(netBareLogListener)
        NetBare.get().attachApplication(this, BuildConfig.DEBUG)
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        LogUtil.i(mTag, "attachBaseContext")
    }

    private val netBareLogListener = object : NetBareLogListener {
        override fun v(tag: String?, msg: String) {
            Log.v(tag, msg)
        }

        override fun d(tag: String?, msg: String) {
            Log.d(tag, msg)
        }

        override fun i(tag: String?, msg: String) {
            Log.i(tag, msg)
        }

        override fun e(tag: String?, msg: String) {
            Log.e(tag, msg)
        }

        override fun w(tag: String?, msg: String) {
            Log.w(tag, msg)
        }

    }
}
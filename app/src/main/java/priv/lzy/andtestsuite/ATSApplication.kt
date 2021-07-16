package priv.lzy.andtestsuite

import android.app.Application
import android.content.Context
import android.os.StrictMode
import android.util.Log
import com.github.megatronking.netbare.NetBare
import com.github.megatronking.netbare.log.NetBareLogListener
import com.github.megatronking.netbare.ssl.JKS
import dagger.hilt.android.HiltAndroidApp
import priv.lzy.andtestsuite.utils.LogUtil

@HiltAndroidApp
class ATSApplication : Application() {
    private val mTag = "ATSApplication"

    private lateinit var mJKS: JKS

    companion object {
        const val JSK_ALIAS = "AndroidTestSuite"

        private lateinit var sInstance: ATSApplication

        fun getInstance(): Application {
            return sInstance
        }
    }

    override fun onCreate() {
        // Enable strict mode before Dagger creates graph
        if (BuildConfig.DEBUG) {
            enableStrictMode()
        }
        super.onCreate()
        LogUtil.i(mTag, "onCreate")
        sInstance = this

        mJKS = JKS(this, JSK_ALIAS, JSK_ALIAS.toCharArray(), JSK_ALIAS,JSK_ALIAS,
            JSK_ALIAS, JSK_ALIAS, JSK_ALIAS)

        // 初始化NetBare
        NetBare.setOnNetBareLogListener(netBareLogListener)
        NetBare.get().attachApplication(this, BuildConfig.DEBUG)
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        LogUtil.i(mTag, "attachBaseContext")
    }

    fun getJKS() : JKS{
        return mJKS
    }

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder()
            .detectActivityLeaks()
            .detectLeakedClosableObjects()
            .detectLeakedSqlLiteObjects()
            .penaltyLog()
            .build())
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
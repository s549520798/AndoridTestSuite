package priv.lzy.andtestsuite

import android.app.Application
import android.content.Context
import com.github.megatronking.netbare.NetBare
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
        NetBare.get().attachApplication(this, BuildConfig.DEBUG)
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        LogUtil.i(mTag, "attachBaseContext")
    }
}
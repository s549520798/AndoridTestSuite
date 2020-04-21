package priv.lzy.andtestsuite

import android.app.Application
import android.content.Context
import priv.lzy.andtestsuite.utils.LogUtil

class ATSApplication : Application() {
    val mTag = "ATSApplication"


    override fun onCreate() {
        super.onCreate()
        LogUtil.i(mTag, "onCreate")
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        LogUtil.i(mTag, "attachBaseContext")
    }
}
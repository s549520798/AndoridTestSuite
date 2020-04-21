package priv.lzy.andtestsuite.utils

import android.util.Log
import priv.lzy.andtestsuite.BuildConfig
import java.lang.Error
import java.util.*

/**
 * 日志打印工具
 */
class LogUtils {

    private val TAG = "ATSLog"

    /**
     * 日志打印等级
     */
    public val ERROR = 10;
    public val WARNING = 20;
    public val INFO = 30;
    public val DEBUG = 40;
    public val VERBOSE = 50;

    fun printTag(level: Int, tag: String, format: String, args: Array<Any>?) {
        var mLevel: Int = 0;
        if (level == VERBOSE || level == DEBUG)
        //过滤低级别日志信息
            return

        if (level == WARNING) {
            mLevel = ERROR;
        }
        try {
            var message =
                if (args == null || args.isEmpty()) format else String.format(format, args)
            if (BuildConfig.DEBUG) {
                Log.println(toSysLevel(mLevel), tag, message)
            }

        } catch (e: MissingFormatArgumentException) {
            e.printStackTrace()
        }


    }

    fun toSysLevel(level: Int): Int {
        var outLevel = 0;
        when(level){
            
        }
    }
}
package priv.lzy.andtestsuite.utils

class TimeUtils {

    companion object {
        /**
         * 时间转换成 int类型。 精确到分
         * eg. "17:00" = 17*60 + 00 = 1020
         */
        fun convertTime2Int(time: String): Int {
            val tmp = time.split(":")
            val hour = tmp[0].toInt()
            val minute = tmp[1].toInt()
            return hour * 60 + minute
        }

        /**
         * int 类型转换成时间
         * eg.  420 = "7:00"
         */
        fun convertInt2Time(time: Int): String {
            val hour = time / 60
            val minute = time % 60
            return "$hour:$minute"
        }
    }
}
package priv.lzy.andtestsuite

import org.junit.Test

import org.junit.Assert.*
import priv.lzy.andtestsuite.utils.TimeUtils

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testTimeUtils() {
        val start = "17:00"
        assertTrue(TimeUtils.convertTime2Int(start) == 1020)
    }
}

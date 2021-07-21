package priv.lzy.andtestsuite.data.suites

import androidx.annotation.DrawableRes
import androidx.annotation.IntDef
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

abstract class FeatureSuite {

    companion object {
        /** Status flag that denotes the demo and component are ready for use. */
        const val STATUS_READY: Int = 0

        /** Status flag that denotes the demo and/or component is work in progress.  */
        const val STATUS_WIP = 1
    }


    /** Status flag enum for this [FeatureSuite].  */
    @IntDef(STATUS_READY, STATUS_WIP)
    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    annotation class Status

    @StringRes
    private var titleResId = 0

    @DrawableRes
    private var drawableResId = 0

    @Status
    private var status = 0

    constructor(@StringRes titleResId: Int,
                @DrawableRes drawableResId: Int) {
        this.titleResId = titleResId
        this.drawableResId = drawableResId
    }

    constructor(@StringRes titleResId: Int,
                @DrawableRes drawableResId: Int,
                @Status status: Int) : this(titleResId, drawableResId){
        this.status = status
    }

    @StringRes
    open fun getTitleResId(): Int {
        return titleResId
    }

    @DrawableRes
    open fun getDrawableResId(): Int {
        return drawableResId
    }

    open fun getStatus(): Int {
        return status
    }

    abstract fun createFragment(): Fragment

    abstract fun createActivity(): FragmentActivity
}
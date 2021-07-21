package priv.lzy.andtestsuite.utils

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.color.MaterialColors
import com.google.android.material.transition.Hold
import com.google.android.material.transition.MaterialContainerTransform
import priv.lzy.andtestsuite.R

class FeatureSuiteUtils {

    companion object {
        private const val ARG_TRANSITION_NAME = "ARG_TRANSITION_NAME"
        private const val MAIN_ACTIVITY_FRAGMENT_CONTAINER_ID: Int = R.id.container

        fun getDefaultSuite(context: Context): String{
            //TODO
            return ""
        }

        fun startFragment(
            activity: FragmentActivity,
            fragment: Fragment,
            tag: String,
            sharedElement: View?,
            sharedElementName: String?
        ) {
            startFragmentInternal(activity, fragment, tag, sharedElement, sharedElementName)
        }

        private fun startFragmentInternal(
            activity: FragmentActivity,
            fragment: Fragment,
            tag: String,
            sharedElement: View?,
            sharedElementName: String?
        ) {
            val transaction: FragmentTransaction = activity.supportFragmentManager.beginTransaction();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && sharedElement != null
                && sharedElementName != null
            ) {
                val currentFragment: Fragment? = getCurrentFragment(activity)

                val context = currentFragment!!.requireContext();
                val transform = MaterialContainerTransform (context, /* entering= */ true)
                transform.containerColor = MaterialColors.getColor(
                    sharedElement,
                    R.attr.colorSurface
                )
                transform.fadeMode = MaterialContainerTransform.FADE_MODE_THROUGH
                fragment.sharedElementEnterTransition = transform
                transaction.addSharedElement(sharedElement, sharedElementName)

                val hold = Hold()
                // Add root view as target for the Hold so that the entire view hierarchy is held in place as
                // one instead of each child view individually. Helps keep shadows during the transition.
                currentFragment.view?.let { hold.addTarget(it) }
                hold.duration = transform.duration;
                currentFragment.exitTransition = hold;

                if (fragment.arguments == null) {
                    val args = Bundle();
                    args.putString(ARG_TRANSITION_NAME, sharedElementName);
                    fragment.arguments = args;
                } else {
                    fragment.arguments?.putString(ARG_TRANSITION_NAME, sharedElementName);
                }
            } else {
                transaction.setCustomAnimations(
                    R.anim.abc_grow_fade_in_from_bottom,
                    R.anim.abc_fade_out,
                    R.anim.abc_fade_in,
                    R.anim.abc_shrink_fade_out_from_bottom
                );
            }

        }

        private fun getCurrentFragment(activity: FragmentActivity): Fragment? {
            return activity
                .supportFragmentManager
                .findFragmentById(MAIN_ACTIVITY_FRAGMENT_CONTAINER_ID)
        }

    }





}
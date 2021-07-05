package priv.lzy.andtestsuite.fragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import priv.lzy.andtestsuite.R


class TopicFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_toptic, container, false)
    }

    companion object {

        @JvmStatic
        fun newInstance() =
            TopicFragment().apply {

            }
    }
}
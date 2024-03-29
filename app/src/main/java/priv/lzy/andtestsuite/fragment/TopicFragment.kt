package priv.lzy.andtestsuite.fragment

import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.math.MathUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import dagger.hilt.android.AndroidEntryPoint
import priv.lzy.andtestsuite.R
import priv.lzy.andtestsuite.adapter.TopicAdapter
import priv.lzy.andtestsuite.data.suites.FeatureSuite
import priv.lzy.andtestsuite.utils.FeatureSuiteUtils
import priv.lzy.andtestsuite.view.GridDividerDecoration
import java.text.Collator
import java.util.*
import kotlin.math.abs

@AndroidEntryPoint
class TopicFragment : Fragment() {

    private val GRID_SPAN_COUNT_MIN = 1
    private val GRID_SPAN_COUNT_MAX = 4

    private var appBarLayout: AppBarLayout? = null
    private var gridTopDivider: View? = null
    private lateinit var recyclerView: RecyclerView
    private var settingButton: ImageButton? = null
    private lateinit var featureSuites: Set<FeatureSuite>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val defaultSuite: String = FeatureSuiteUtils.getDefaultSuite(requireContext())
        if (defaultSuite.isNotEmpty() && savedInstanceState == null) {
            for (demo in featureSuites) {
                val fragment: Fragment = demo.createFragment()
                val key = fragment.javaClass.name
                if (key == defaultSuite) {
                    FeatureSuiteUtils.startFragment(activity, fragment, "fragment_content")
                    return
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_toptic, container, false)
        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        val activity = activity as AppCompatActivity
        activity.setSupportActionBar(toolbar)
        activity.supportActionBar?.setDisplayShowTitleEnabled(false)

        val content = view.findViewById<ViewGroup>(R.id.content)
        View.inflate(context, R.layout.fragment_topic_header, content)

        appBarLayout = view.findViewById(R.id.topic_app_bar_layout)
        gridTopDivider = view!!.findViewById(R.id.topic_grid_top_divider)
        recyclerView = view.findViewById(R.id.topic_grid)
        settingButton = view.findViewById(R.id.topic_setting_button)
        ViewCompat.setOnApplyWindowInsetsListener(
            view
        ) { v: View?, insetsCompat: WindowInsetsCompat ->
            appBarLayout
                ?.findViewById<View>(R.id.topic_collapsingtoolbarlayout)
                ?.setPadding(0, insetsCompat.systemWindowInsetTop, 0, 0)
            insetsCompat
        }

        if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
            addGridDividerVisibilityListener()
        } else {
            gridTopDivider?.visibility = View.VISIBLE
        }

        val gridSpanCount: Int = calculateGridSpanCount()

        recyclerView.layoutManager = GridLayoutManager(context, gridSpanCount)
        recyclerView.addItemDecoration(
            GridDividerDecoration(
                resources.getDimensionPixelSize(R.dimen.topic_grid_divider_size),
                ContextCompat.getColor(requireContext(), R.color.colorDivider),
                gridSpanCount
            )
        )

        val featureList: List<FeatureSuite> = ArrayList(featureSuites)
        // Sort features alphabetically
        // Sort features alphabetically
        val collator = Collator.getInstance()
        Collections.sort(
            featureList
        ) { feature1: FeatureSuite, feature2: FeatureSuite ->
            collator.compare(
                requireContext().getString(feature1.getTitleResId()),
                requireContext().getString(feature2.getTitleResId())
            )
        }

        val tocAdapter = TopicAdapter(requireActivity(), featureList)
        recyclerView.adapter = tocAdapter

        initSettingButton()
        return view
    }

    private fun addGridDividerVisibilityListener() {
        appBarLayout!!.addOnOffsetChangedListener(
            AppBarLayout.OnOffsetChangedListener { appBarLayout: AppBarLayout, verticalOffset: Int ->
                if (abs(verticalOffset) == appBarLayout.totalScrollRange) {
                    // CTL is collapsed, hide top divider
                    gridTopDivider!!.visibility = View.GONE
                } else {
                    // CTL is expanded or expanding, show top divider
                    gridTopDivider!!.visibility = View.VISIBLE
                }
            })
    }

    private fun calculateGridSpanCount(): Int {
        val displayMetrics = resources.displayMetrics
        val displayWidth = displayMetrics.widthPixels
        val itemSize = resources.getDimensionPixelSize(R.dimen.topic_item_size)
        val gridSpanCount = displayWidth / itemSize
        return MathUtils.clamp(
            gridSpanCount,
            GRID_SPAN_COUNT_MIN,
            GRID_SPAN_COUNT_MAX
        )
    }

    private fun initSettingButton() {
        preferencesButton!!.setOnClickListener { v: View? ->
            PreferencesDialogFragment().show(
                parentFragmentManager, "preferences-screen"
            )
        }
    }

    companion object {

        @JvmStatic
        fun newInstance() = TopicFragment()
    }
}
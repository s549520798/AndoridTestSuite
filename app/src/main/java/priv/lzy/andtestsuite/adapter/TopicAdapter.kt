package priv.lzy.andtestsuite.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import priv.lzy.andtestsuite.R
import priv.lzy.andtestsuite.data.suites.FeatureSuite
import priv.lzy.andtestsuite.utils.FeatureSuiteUtils

internal class TopicAdapter(private val activity: FragmentActivity, var featureSuites: List<FeatureSuite>) :
    RecyclerView.Adapter<TopicViewHolder>() {

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): TopicViewHolder {
        return TopicViewHolder(activity, viewGroup)
    }

    override fun onBindViewHolder(tocViewHolder: TopicViewHolder, i: Int) {
        tocViewHolder.bind(activity, featureSuites[i])
    }

    override fun getItemCount(): Int {
        return featureSuites.size
    }
}

internal class TopicViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val FRAGMENT_CONTENT = "fragment_content"

    private var titleView: TextView? = null
    private var imageView: ImageView? = null
    private var statusWipLabelView: TextView? = null

    constructor(activity: FragmentActivity?, viewGroup: ViewGroup?) : this(
        LayoutInflater.from(activity)
            .inflate(R.layout.topic_feature_item, viewGroup, false /* attachToRoot */)
    ) {
        titleView = itemView.findViewById(R.id.topic_item_title)
        imageView = itemView.findViewById(R.id.topic_item_image)
        statusWipLabelView = itemView.findViewById(R.id.topic_item_status_wip_label)
    }

    fun bind(activity: FragmentActivity, featureSuite: FeatureSuite) {
        val transitionName = activity.getString(featureSuite.getTitleResId())
        ViewCompat.setTransitionName(itemView, transitionName)
        titleView?.setText(featureSuite.getTitleResId())
        imageView?.setImageResource(featureSuite.getDrawableResId())
        itemView.setOnClickListener { v: View? ->
            FeatureSuiteUtils.startFragment(
                activity, featureSuite.createFragment(), FRAGMENT_CONTENT, v, transitionName
            )
        }
        statusWipLabelView!!.visibility =
            if (featureSuite.getStatus() == FeatureSuite.STATUS_WIP) View.VISIBLE else View.GONE
    }
}
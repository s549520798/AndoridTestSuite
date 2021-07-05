package priv.lzy.andtestsuite.activity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import priv.lzy.andtestsuite.R
import priv.lzy.andtestsuite.fragment.TopicFragment

class MainActivity : AppCompatActivity() {

    private lateinit var mTopicFragment: TopicFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            mTopicFragment = TopicFragment()
            supportFragmentManager.beginTransaction().add(R.id.main_container, mTopicFragment)
                .commit()
        }
    }
}

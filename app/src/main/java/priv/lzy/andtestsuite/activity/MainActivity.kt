package priv.lzy.andtestsuite.activity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import priv.lzy.andtestsuite.R
import priv.lzy.andtestsuite.databinding.ActivityMainBinding
import priv.lzy.andtestsuite.fragment.TopicFragment
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var mTopicFragment: TopicFragment

    private lateinit var mMainBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().add(R.id.main_container, mTopicFragment)
                .commit()
        }
    }
}

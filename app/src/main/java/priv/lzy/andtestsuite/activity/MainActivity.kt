package priv.lzy.andtestsuite.activity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import priv.lzy.andtestsuite.R
import priv.lzy.andtestsuite.databinding.ActivityMainBinding
import priv.lzy.andtestsuite.fragment.TopicFragment
import priv.lzy.andtestsuite.utils.updateForTheme
import priv.lzy.andtestsuite.viewmodels.MainActivityViewModel
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var mTopicFragment: TopicFragment

    private val viewModel: MainActivityViewModel by viewModels()

    private lateinit var mMainBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateForTheme(viewModel.getDefaultTheme)
        mMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mMainBinding.root)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().add(R.id.main_container, mTopicFragment)
                .commit()
        }
    }
}

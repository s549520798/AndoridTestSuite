package priv.lzy.andtestsuite.activity

import android.net.VpnService
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import priv.lzy.andtestsuite.R
import priv.lzy.andtestsuite.service.TestSuiteVpnService

class PackageCaptureActivity : AppCompatActivity() {

    private lateinit var mBtnBindService: Button
    private lateinit var mVpnService: VpnService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_package_capture)

        mBtnBindService = findViewById(R.id.button_bind_service)
        mBtnBindService.setOnClickListener(mOnClickListener)

        mVpnService = TestSuiteVpnService()
    }

    private var mOnClickListener: View.OnClickListener = View.OnClickListener {
        when(it.id){
            R.id.button_bind_service -> {
                var intent = VpnService.prepare(applicationContext)
            }
        }
    }
}

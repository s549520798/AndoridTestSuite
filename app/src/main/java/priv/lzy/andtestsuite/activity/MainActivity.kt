package priv.lzy.andtestsuite.activity

import android.content.Intent
import android.net.VpnService
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import priv.lzy.andtestsuite.R

class MainActivity : AppCompatActivity(){

    private lateinit var mBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mBtn = findViewById(R.id.button_capture_package)
        mBtn.setOnClickListener(mOnClickListener)
    }


    private var mOnClickListener: View.OnClickListener = View.OnClickListener {
        when(it.id){
            R.id.button_bind_service -> {
                var intent = Intent(this, PackageCaptureActivity::class.java)
                startActivity(intent)
            }
        }
    }
}

package priv.lzy.andtestsuite.service

import android.net.VpnService
import java.net.Socket

class TestSuiteVpnService : VpnService() {

    override fun protect(socket: Socket?): Boolean {
        return super.protect(socket)
    }
}
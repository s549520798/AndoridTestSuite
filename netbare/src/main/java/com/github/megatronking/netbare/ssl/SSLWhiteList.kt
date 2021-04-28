package com.github.megatronking.netbare.ssl

import java.util.*

/**
 * ip whitelist for ssl bypass
 * @author cuisoap
 * @since 2019/08/01 10:00
 */
object SSLWhiteList {
    private val whitelist = HashSet<String?>()
    fun add(ip: String?) {
        whitelist.add(ip)
    }

    operator fun contains(ip: String?): Boolean {
        return whitelist.contains(ip)
    }
}
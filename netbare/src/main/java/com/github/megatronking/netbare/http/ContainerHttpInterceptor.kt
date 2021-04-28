/*  NetBare - An android network capture and injection library.
 *  Copyright (C) 2018-2019 Megatron King
 *  Copyright (C) 2018-2019 GuoShi
 *
 *  NetBare is free software: you can redistribute it and/or modify it under the terms
 *  of the GNU General Public License as published by the Free Software Found-
 *  ation, either version 3 of the License, or (at your option) any later version.
 *
 *  NetBare is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 *  PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with NetBare.
 *  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.megatronking.netbare.http

import androidx.annotation.NonNull
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * One http virtual gateway may have multi http sessions, but we don't want to share interceptors
 * between them. Use a container to manage different sessions, every session has independent
 * interceptor instances.
 *
 * @author Megatron King
 * @since 2019/1/6 16:13
 */
/* package */
internal class ContainerHttpInterceptor(private val mSubInterceptorsFactory: HttpInterceptorsFactory) :
    HttpInterceptor {
    private val mSessions: MutableMap<String?, Session>
    @Throws(IOException::class)
    override fun intercept(@NonNull chain: HttpRequestChain, @NonNull buffer: ByteBuffer) {
        val request = chain.request()
        val session = findSessionById(
            request!!.id()
        )
        session!!.request = request
        if (session.interceptors == null) {
            session.interceptors = mSubInterceptorsFactory.create()
        }
        HttpContainerRequestChain(chain, session.interceptors).process(buffer)
    }

    @Throws(IOException::class)
    override fun intercept(@NonNull chain: HttpResponseChain, @NonNull buffer: ByteBuffer) {
        val response = chain.response()
        val session = findSessionById(
            response!!.id()
        )
        session!!.response = response
        if (session.interceptors == null) {
            session.interceptors = mSubInterceptorsFactory.create()
        }
        HttpContainerResponseChain(chain, session.interceptors).process(buffer)
    }

    override fun onRequestFinished(@NonNull request: HttpRequest) {
        if (request is HttpZygoteRequest) {
            // This means the connection is down, finish all.
            for (session in mSessions.values) {
                if (session.request != null && session.interceptors != null) {
                    for (interceptor in session.interceptors) {
                        interceptor!!.onRequestFinished(session.request)
                    }
                }
            }
            mSessions.clear()
        } else {
            val session = mSessions.remove(request.id())
            if (session != null && session.interceptors != null) {
                for (interceptor in session.interceptors) {
                    interceptor!!.onRequestFinished(session.request)
                }
            }
        }
    }

    override fun onResponseFinished(@NonNull response: HttpResponse) {
        if (response is HttpZygoteResponse) {
            // This means the connection is down, finish all.
            for (session in mSessions.values) {
                if (session != null && session.response != null && session.interceptors != null) {
                    for (interceptor in session.interceptors) {
                        interceptor!!.onResponseFinished(session.response)
                    }
                }
            }
        } else {
            val session = mSessions.remove(response.id())
            if (session != null && session.interceptors != null) {
                for (interceptor in session.interceptors) {
                    interceptor!!.onResponseFinished(session.response)
                }
            }
        }
    }

    private fun findSessionById(id: String?): Session? {
        val session: Session?
        if (mSessions.containsKey(id)) {
            session = mSessions[id]
        } else {
            session = Session()
            mSessions[id] = session
        }
        return session
    }

    private class Session {
        val request: HttpRequest? = null
        val response: HttpResponse? = null
        val interceptors: List<HttpInterceptor?>? = null
    }

    private class HttpContainerRequestChain(
        private val mChain: HttpRequestChain, private val mInterceptors: List<HttpInterceptor?>?,
        private val mIndex: Int = 0, private val mTag: Any? = null
    ) : HttpRequestChain(mChain.zygoteRequest(), mInterceptors, mIndex, mTag) {
        @Throws(IOException::class)
        override fun process(buffer: ByteBuffer?) {
            if (mIndex >= mInterceptors!!.size) {
                mChain.process(buffer)
            } else {
                val interceptor = mInterceptors[mIndex]
                interceptor?.intercept(
                    HttpContainerRequestChain(
                        mChain, mInterceptors,
                        mIndex + 1, mTag
                    ), buffer!!
                )
            }
        }
    }

    private class HttpContainerResponseChain(
        private val mChain: HttpResponseChain, private val mInterceptors: List<HttpInterceptor?>?,
        private val mIndex: Int = 0, private val mTag: Any? = null
    ) : HttpResponseChain(
        mChain.zygoteResponse(), mInterceptors, mIndex, mTag
    ) {
        @Throws(IOException::class)
        override fun process(buffer: ByteBuffer?) {
            if (mIndex >= mInterceptors!!.size) {
                mChain.process(buffer)
            } else {
                val interceptor = mInterceptors[mIndex]
                interceptor?.intercept(
                    HttpContainerResponseChain(
                        mChain, mInterceptors,
                        mIndex + 1, mTag
                    ), buffer!!
                )
            }
        }
    }

    /* package */
    init {
        mSessions = ConcurrentHashMap()
    }
}
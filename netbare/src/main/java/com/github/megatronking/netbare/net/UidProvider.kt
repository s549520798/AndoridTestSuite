package com.github.megatronking.netbare.net

/**
 * This interface provides a known uid for a session.
 *
 * @author Megatron King
 * @since 2019/1/27 21:31
 */
interface UidProvider {
    /**
     * Returns a known uid for this session, if the uid is unknown should return [.UID_UNKNOWN].
     *
     * @param session Network session.
     * @return A known uid or [.UID_UNKNOWN].
     */
    fun uid(session: Session?): Int

    companion object {
        const val UID_UNKNOWN = -1
    }
}
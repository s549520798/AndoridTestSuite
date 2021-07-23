package priv.lzy.andtestsuite.data.db

import android.content.Context

//TODO database implementation
class AppDatabase() {

    companion object {
        fun buildDatabase(context: Context) : AppDatabase {
            return AppDatabase()
        }
    }
}


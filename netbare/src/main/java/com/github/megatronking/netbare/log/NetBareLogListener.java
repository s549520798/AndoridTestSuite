package com.github.megatronking.netbare.log;

import androidx.annotation.NonNull;

public interface NetBareLogListener {

    void v(String tag, @NonNull String msg);

    void d(String tag, @NonNull String msg);

    void i(String tag, @NonNull String msg);

    void e(String tag, @NonNull String msg);

    void w(String tag, @NonNull String msg);

}

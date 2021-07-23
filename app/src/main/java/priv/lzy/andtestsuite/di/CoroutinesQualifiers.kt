package priv.lzy.andtestsuite.di

import javax.inject.Qualifier

class CoroutinesQualifiers {

    @Retention(AnnotationRetention.BINARY)
    @Qualifier
    annotation class DefaultDispatcher

    @Retention(AnnotationRetention.BINARY)
    @Qualifier
    annotation class IoDispatcher

    @Retention(AnnotationRetention.BINARY)
    @Qualifier
    annotation class MainDispatcher

    @Retention(AnnotationRetention.BINARY)
    @Qualifier
    annotation class MainImmediateDispatcher

    @Retention(AnnotationRetention.BINARY)
    @Qualifier
    annotation class ApplicationScope

}
package priv.lzy.andtestsuite.di

import javax.inject.Qualifier

@Qualifier
@Target(
    AnnotationTarget.FUNCTION, AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.VALUE_PARAMETER
)
annotation class MainThreadHandler

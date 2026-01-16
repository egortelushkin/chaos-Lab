package com.chaosLab;

public interface ChaosHttpInterceptor {

    /** вызывается ДО отправки HTTP-запроса */
    void beforeRequest();

    /** вызывается при успешном ответе */
    default void afterResponse() {
        // no-op
    }

    /** вызывается при ошибке запроса */
    default void onError(Throwable error) {
        // no-op
    }
}
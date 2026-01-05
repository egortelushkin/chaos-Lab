package com.helloegor03;

import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public final class ChaosWebClient {

    private ChaosWebClient() {}

    public static WebClient withChaos(
            WebClient delegate,
            ChaosHttpInterceptor interceptor
    ) {
        ExchangeFilterFunction filter = (request, next) -> {
            interceptor.beforeRequest();
            return next.exchange(request)
                    .doOnError(interceptor::onError)
                    .doOnSuccess(r -> interceptor.afterResponse());
        };

        return delegate.mutate()
                .filter(filter)
                .build();
    }
}

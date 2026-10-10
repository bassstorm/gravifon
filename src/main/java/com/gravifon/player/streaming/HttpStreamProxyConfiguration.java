package com.gravifon.player.streaming;

import com.gravifon.player.config.StreamProxyProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class HttpStreamProxyConfiguration {
    @Bean(name = "httpStreamProxyRestClient", defaultCandidate = false)
    RestClient httpStreamProxyRestClient(RestClient.Builder restClientBuilder, StreamProxyProperties proxyProperties) {
        var requestFactory = ClientHttpRequestFactoryBuilder
            .detect()
            .build(ClientHttpRequestFactorySettings
                .defaults()
                .withTimeouts(proxyProperties.proxyConnectTimeout(), proxyProperties.proxyReadTimeout())
            );
        return restClientBuilder.requestFactory(requestFactory).build();
    }
}

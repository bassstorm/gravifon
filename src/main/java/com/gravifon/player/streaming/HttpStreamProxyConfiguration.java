package com.gravifon.player.streaming;

import com.gravifon.player.config.StreamProxyProperties;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class HttpStreamProxyConfiguration {
    @Bean("httpStreamProxyRestClient")
    RestClient httpStreamProxyRestClient(RestClient.Builder restClientBuilder, StreamProxyProperties proxyProperties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(proxyProperties.proxyConnectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(proxyProperties.proxyReadTimeout());
        return restClientBuilder.requestFactory(requestFactory).build();
    }
}

package com.gravifon.player.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import com.gravifon.player.config.StreamProxyProperties;
import com.gravifon.player.config.StreamRefreshProperties;
import com.gravifon.player.registry.stream.StreamTrackRefresher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

@SpringBootTest(classes = {
        RestClientAutoConfiguration.class,
        HttpStreamProxyConfiguration.class,
        HttpStreamProxy.class
})
@EnableConfigurationProperties({StreamProxyProperties.class, StreamRefreshProperties.class})
@ActiveProfiles("test")
class HttpStreamProxyContextTest {
    @MockitoBean
    private StreamTrackRefresher refresher;
    @Autowired
    private HttpStreamProxy proxy;
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    @Qualifier("httpStreamProxyRestClient") private RestClient httpStreamProxyRestClient;

    @Test
    void wiresSuccessfully() {
        assertThat(proxy).isNotNull();
        assertThat(httpStreamProxyRestClient).isNotNull();
        assertThat(applicationContext.getBeansOfType(RestClient.class)).containsOnlyKeys("httpStreamProxyRestClient");
    }
}

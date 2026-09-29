package my.bookshop.config;

import jakarta.annotation.PostConstruct;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.sap.cloud.security.config.Environments;

/**
 * Pre-initializes the JVM TLS/JSSE stack during Spring context startup, before the embedded
 * Tomcat server begins accepting connections.
 *
 * On a fresh CF container with one CPU, the first HTTPS connection loads hundreds of JSSE and
 * Apache HttpComponents classes from JARs. This class-loading overhead can take 10-15 seconds,
 * which causes Cloud SDK's 10-second OAuth2 TimeLimiter to fire, opening the CircuitBreaker and
 * blocking all subsequent requests. Running the warmup in @PostConstruct ensures Tomcat starts
 * (and the health-check passes) only after TLS is fully initialized.
 */
@Configuration
@Profile("cloud")
class SslWarmupConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SslWarmupConfiguration.class);

    @PostConstruct
    void warmUpJsseSslStack() {
        String warmupUrl;
        try {
            var xsuaaConfig = Environments.getCurrent().getXsuaaConfiguration();
            if (xsuaaConfig == null) {
                log.warn("SslWarmup: no XSUAA configuration found, skipping TLS warmup");
                return;
            }
            warmupUrl = xsuaaConfig.getUrl() + "/.well-known/openid-configuration";
        } catch (Exception e) {
            log.warn("SslWarmup: cannot read XSUAA configuration, skipping TLS warmup: {}", e.getMessage());
            return;
        }

        log.info("SslWarmup: pre-initializing JVM TLS stack via {}", warmupUrl);
        long start = System.currentTimeMillis();

        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(30))
            .setConnectTimeout(Timeout.ofSeconds(30))
            .setResponseTimeout(Timeout.ofSeconds(30))
            .build();

        try (var client = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
            try (var response = client.execute(new HttpGet(warmupUrl))) {
                EntityUtils.consume(response.getEntity());
                log.info("SslWarmup: TLS stack warmed in {}ms (HTTP {})",
                    System.currentTimeMillis() - start, response.getCode());
            }
        } catch (Exception e) {
            log.warn("SslWarmup: TLS warmup failed after {}ms — startup continues without warmup: {}",
                System.currentTimeMillis() - start, e.getMessage());
        }
    }
}

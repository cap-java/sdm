package my.bookshop.config;

import jakarta.annotation.PostConstruct;
import java.net.HttpURLConnection;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Pre-initializes the JVM TLS/JSSE stack during Spring context startup, before the
 * embedded Tomcat server begins accepting connections.
 *
 * On a fresh CF container (1 CPU), the first TLS handshake loads hundreds of JSSE
 * classes from JARs and initializes the CF Container Security Provider TrustManager
 * (which reads the system CA bundle). This cold-start overhead consistently takes
 * 10-15 s. Cloud SDK 5.34.0's OAuth2 TimeLimiter fires at 10 s, opening the
 * CircuitBreaker and blocking all subsequent sidecar model-load requests with HTTP 500.
 *
 * @PostConstruct runs inside finishBeanFactoryInitialization(), which Spring calls
 * BEFORE finishRefresh() re-attaches Tomcat's connectors. The health-check therefore
 * does not pass until the warmup completes, so no integration-test request can arrive
 * before TLS is warm.
 */
@Configuration
@Profile("cloud")
class SslWarmupConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SslWarmupConfiguration.class);

    /** XSUAA base URL, injected from CF VCAP_SERVICES by Spring Boot's environment post-processor. */
    @Value("${vcap.services.bookshop-mt-uaa.credentials.url:}")
    private String xsuaaBaseUrl;

    @PostConstruct
    void warmUpJsseSslStack() {
        if (xsuaaBaseUrl.isEmpty()) {
            log.warn("SslWarmup: vcap.services.bookshop-mt-uaa.credentials.url not found — skipping TLS warmup");
            return;
        }
        // OIDC discovery endpoint is publicly accessible (no credentials needed)
        String warmupUrl = xsuaaBaseUrl + "/.well-known/openid-configuration";
        log.info("SslWarmup: pre-initializing JVM TLS stack via {}", warmupUrl);
        long start = System.currentTimeMillis();
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(warmupUrl).openConnection();
            conn.setConnectTimeout(30_000);
            conn.setReadTimeout(30_000);
            int status = conn.getResponseCode();
            conn.disconnect();
            log.info("SslWarmup: TLS stack warmed in {}ms (HTTP {})",
                System.currentTimeMillis() - start, status);
        } catch (Exception e) {
            log.warn("SslWarmup: warmup failed after {}ms — startup continues without warmup: {}",
                System.currentTimeMillis() - start, e.getMessage());
        }
    }
}

package my.bookshop.config;

import jakarta.annotation.PostConstruct;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import com.sap.cloud.sdk.cloudplatform.resilience.NoResilienceDecorationStrategy;
import com.sap.cloud.sdk.cloudplatform.resilience.ResilienceDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Pre-initializes the CF Container Security Provider (CF CSP) and TLS handshake
 * classes before Tomcat accepts connections, preventing the Cloud SDK 5.34.0
 * OAuth2 TimeLimiter (10 s) from firing on the first sidecar token request.
 *
 * Root cause: Cloud SDK's httpclient5 creates SSLContext.getInstance("TLS") and
 * calls init(null, null, null), which triggers CF CSP TrustManagerFactory to read
 * the system CA bundle. On a 1-CPU CF container this takes 10-15 s cold. JDK's
 * HttpURLConnection uses SSLContext.getDefault() (algorithm "Default") which does
 * NOT trigger CF CSP, so it cannot serve as a warmup. A direct SSLSocket with
 * an explicit SSLContext.getInstance("TLS").init(null,null,null) + startHandshake()
 * warms both the CF CSP cert cache and all JSSE handshake classes.
 *
 * Additionally, the Cloud SDK's 10 s TimeLimiter is replaced with
 * NoResilienceDecorationStrategy because on heavily CPU-throttled CF containers
 * (< 10 % entitlement) even a warm SSL connection can exceed 10 s, causing
 * 5 consecutive TimeoutExceptions that permanently open the CircuitBreaker.
 * The underlying Apache HttpClient5 still applies its own socket/connect timeouts,
 * so removing the SDK-level TimeLimiter does not risk indefinite hangs.
 *
 * @PostConstruct runs inside finishBeanFactoryInitialization(), which Spring calls
 * BEFORE finishRefresh() re-attaches Tomcat's connectors, so no test request can
 * arrive before the warmup completes.
 */
@Configuration
@Profile("cloud")
class SslWarmupConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SslWarmupConfiguration.class);

    @Value("${vcap.services.bookshop-mt-uaa.credentials.url:}")
    private String xsuaaBaseUrl;

    @PostConstruct
    void warmUpJsseSslStack() {
        long start = System.currentTimeMillis();

        // Disable the Cloud SDK's 10 s TimeLimiter + CircuitBreaker.
        // On throttled CF containers the timer fires before the XSUAA token response
        // arrives, opening the CB and blocking all subsequent requests permanently.
        // The Apache HttpClient5 underneath still enforces its own socket timeouts.
        ResilienceDecorator.setDecorationStrategy(new NoResilienceDecorationStrategy());
        log.info("SslWarmup: disabled Cloud SDK resilience decorators (TimeLimiter/CircuitBreaker)");

        log.info("SslWarmup: starting CF CSP and JSSE pre-initialization");

        // Step 1: init a new TLS SSLContext with null params — same code path as httpclient5.
        // This triggers CloudFoundryContainerTrustManagerFactory to read and cache the CA bundle.
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, null, null);
            log.info("SslWarmup: SSLContext.getInstance(TLS).init() done in {}ms",
                System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("SslWarmup: SSLContext init failed after {}ms: {}",
                System.currentTimeMillis() - start, e.getMessage());
        }

        // Step 2: perform a real TLS handshake to load all sun.security.ssl.* handshake classes.
        // Skipped only if the XSUAA URL is absent (should never happen in a cloud deployment).
        if (xsuaaBaseUrl.isEmpty()) {
            log.warn("SslWarmup: vcap.services.bookshop-mt-uaa.credentials.url is empty — "
                + "CF CSP init done but TLS handshake classes not pre-loaded");
            return;
        }

        try {
            URI uri = URI.create(xsuaaBaseUrl);
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 443;

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, null, null);
            SSLSocketFactory sf = ctx.getSocketFactory();

            long handshakeStart = System.currentTimeMillis();
            try (Socket plain = new Socket()) {
                plain.connect(new InetSocketAddress(host, port), 30_000);
                try (SSLSocket ssl = (SSLSocket) sf.createSocket(plain, host, port, true)) {
                    ssl.setSoTimeout(30_000);
                    ssl.startHandshake();
                }
            }
            log.info("SslWarmup: TLS handshake to {}:{} done in {}ms — total warmup {}ms",
                host, port, System.currentTimeMillis() - handshakeStart,
                System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("SslWarmup: TLS handshake failed after {}ms — continuing without full warmup: {}",
                System.currentTimeMillis() - start, e.getMessage());
        }
    }
}

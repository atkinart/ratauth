package ru.ratauth.server.command;

import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixObservableCommand;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import ratpack.exec.Promise;
import ratpack.http.MediaType;
import ratpack.http.client.HttpClient;
import ratpack.http.client.ReceivedResponse;
import ratpack.rx.RxRatpack;
import ru.ratauth.entities.UserInfo;
import rx.Observable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class HystrixIdentityProviderCommand extends HystrixObservableCommand<ReceivedResponse> {

    private final HttpClient httpClient;
    private final Map<String, Object> data;
    private final URI uri;

    private String login;
    private String password;

    public HystrixIdentityProviderCommand(@NonNull HttpClient httpClient,
                                   @NonNull Map<String, String> data,
                                   @NonNull UserInfo userInfo,
                                   @NonNull String relyingParty,
                                   @NonNull String enroll,
                                   @NonNull String url,
                                   String login,
                                   String password) throws MalformedURLException, URISyntaxException {
        this(httpClient, data, userInfo, relyingParty, enroll, url);
        this.login = login;
        this.password = password;
    }

    private HystrixIdentityProviderCommand(@NonNull HttpClient httpClient,
                                           @NonNull Map<String, String> data,
                                           @NonNull UserInfo userInfo,
                                           @NonNull String relyingParty,
                                           @NonNull String enroll,
                                           @NonNull String url) throws MalformedURLException, URISyntaxException {
        super(HystrixCommandGroupKey.Factory.asKey(String.format("identity-provider-%s", enroll)));
        this.httpClient = httpClient;
        this.uri = new URL(url).toURI();
        this.data = performData(data, userInfo, relyingParty, enroll);
    }

    private Map<String, Object> performData(Map<String, String> data, UserInfo userInfo, String relyingParty, String enroll) {
        Map<String, Object> result = createKeyPrefix("data", data);
        result.putAll(createKeyPrefix("userinfo", userInfo.toMap()));
        result.put("relyingparty", relyingParty);
        result.put("enroll", enroll);
        return result;
    }

    private static Map<String, Object> createKeyPrefix(String prefix, Map<String, ?> map) {
        return map.entrySet().stream()
                .filter(e -> e.getKey() != null)
                .collect(Collectors.toMap(
                        e -> prefix + "." + e.getKey(),
                        e -> Objects.toString(e.getValue()))
                );
    }

    @SneakyThrows(UnsupportedEncodingException.class)
    private static String createAuthHeader(String login, String password) {
        String auth = login + ":" + password;
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(Charset.forName("UTF-8")));
        return "Basic " + new String(encodedAuth, "UTF-8");
    }

    protected Observable<ReceivedResponse> construct() {
        Promise<ReceivedResponse> promise = httpClient.post(
                uri,
                r -> {
                    r.sslContext(createSSLContext());
                    if (login != null && password != null) {
                        r.headers(headers -> headers.add(HttpHeaders.AUTHORIZATION, createAuthHeader(login, password)));
                    }
                    r.body(body -> {
                        body.type(MediaType.APPLICATION_JSON);
                        body.text(data.entrySet().stream()
                                .filter(e -> e.getKey() != null && e.getValue() != null)
                                .map(e -> e.getKey() + "=" + e.getValue())
                                .collect(Collectors.joining("&")));
                    });
                }
        );
        return RxRatpack.observe(promise);
    }

    @SneakyThrows
    private SSLContext createSSLContext() {
        SSLContext sslContext = SSLContext.getInstance("SSL");

        sslContext.init(null, new TrustManager[]{new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] certs,
                                           String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs,
                                           String authType) {
            }

        }}, new SecureRandom());
        return sslContext;
    }
}
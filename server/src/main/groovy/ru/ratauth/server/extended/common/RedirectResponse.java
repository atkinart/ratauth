package ru.ratauth.server.extended.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import ru.ratauth.server.utils.RedirectUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

@Data
@AllArgsConstructor
abstract public class RedirectResponse {

    final private String location;

    public abstract String putRedirectParameters(String key, String value);

    public abstract Map<String, String> getRedirectParameters();

    public String getRedirectURL() {
        return RedirectUtils.createRedirectURI(getLocation(), getRedirectParameters());
    }

    @SneakyThrows(UnsupportedEncodingException.class)
    private static String encoded(String source) {
        return URLEncoder.encode(source, UTF_8.name());
    }
}

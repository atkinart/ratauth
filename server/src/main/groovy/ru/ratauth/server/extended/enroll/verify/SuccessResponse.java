package ru.ratauth.server.extended.enroll.verify;

import java.util.HashMap;
import java.util.Map;

public class SuccessResponse extends RedirectResponse {

    private final String code;
    private final Map<String, String> redirectParameters;

    public SuccessResponse(String location, String code) {
        super(location);
        redirectParameters = new HashMap<>();
        redirectParameters.put("code", code);
        this.code = code;
    }

    public String putRedirectParameters(String key, String value) {
        return redirectParameters.put(key, value);
    }

    @Override
    Map<String, String> getRedirectParameters() {
        return redirectParameters;
    }

}

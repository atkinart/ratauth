package ru.ratauth.server.extended.enroll.verify;

import ru.ratauth.entities.AcrValues;
import ru.ratauth.server.extended.common.RedirectResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.joining;

public class NeedApprovalResponse extends RedirectResponse {

    private final String redirectURI;
    private final String mfaToken;
    private final String clientId;
    private final Set<String> scope;
    private final AcrValues acrValues;
    private Map<String, String> result;

    public NeedApprovalResponse(String location, String redirectURI, String mfaToken, String clientId, Set<String> scope, AcrValues acrValues) {
        super(location);
        this.redirectURI = redirectURI;
        this.clientId = clientId;
        this.mfaToken = mfaToken;
        this.scope = scope;
        this.acrValues = acrValues;
        result = new HashMap<>();
        result.put("redirect_uri", redirectURI);
        result.put("mfa_token", mfaToken);
        result.put("client_id", clientId);
        result.put("scope", scope.stream().collect(joining(" ")));
        result.put("acr_values", acrValues.toString());
    }

    @Override
    public String putRedirectParameters(String key, String value) {
        return result.put(key, value);
    }

    @Override
    public Map<String, String> getRedirectParameters() {
        return result;
    }

}

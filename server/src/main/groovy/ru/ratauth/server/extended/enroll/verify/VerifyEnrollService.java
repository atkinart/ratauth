package ru.ratauth.server.extended.enroll.verify;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.ratauth.entities.AcrValues;
import ru.ratauth.entities.AuthEntry;
import ru.ratauth.entities.DeviceInfo;
import ru.ratauth.entities.IdentityProvider;
import ru.ratauth.entities.RelyingParty;
import ru.ratauth.entities.Session;
import ru.ratauth.entities.UserInfo;
import ru.ratauth.exception.AuthorizationException;
import ru.ratauth.exception.AuthorizationException.ID;
import ru.ratauth.providers.auth.dto.VerifyInput;
import ru.ratauth.providers.auth.dto.VerifyResult;
import ru.ratauth.server.extended.common.RedirectResponse;
import ru.ratauth.server.providers.IdentityProviderResolver;
import ru.ratauth.server.secutiry.TokenProcessor;
import ru.ratauth.server.services.AuthClientService;
import ru.ratauth.server.services.AuthSessionService;
import ru.ratauth.server.services.DeviceService;
import ru.ratauth.server.services.TokenCacheService;
import ru.ratauth.server.utils.RedirectUtils;
import rx.Observable;

import static java.util.Optional.ofNullable;
import static ru.ratauth.server.utils.DateUtils.fromLocal;
import static ru.ratauth.server.utils.RedirectUtils.createRedirectURI;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class VerifyEnrollService {

    private final AuthClientService clientService;
    private final AuthSessionService sessionService;
//    private final UpdateCodeService updateCodeService;
    private final TokenCacheService tokenCacheService;
    private final TokenProcessor tokenProcessor;
    private final IdentityProviderResolver identityProviderResolver;
    private final DeviceService deviceService;

    @SneakyThrows
    private RedirectResponse createResponse(Session session, RelyingParty relyingParty, VerifyEnrollRequest request, VerifyResult verifyResult) {

//        if (NEED_UPDATE.equals(verifyResult.getStatus())) {
//            String reason = (String) verifyResult.getData().get("reason");
//            String redirectUri = createRedirectURIWithPath(relyingParty, (String) verifyResult.getData().get("redirect_uri"));
//            UpdateEntry updateTokenEntry = updateCodeService.create(session.getId(), LocalDateTime.now().plusMinutes(5L)).toBlocking().single();
//            return new UpdateResponse(reason, updateTokenEntry.getToken(), redirectUri);
//        }

        AcrValues difference = request.getAuthContext().difference(session.getReceivedAcrValues());
        if (difference.getValues().isEmpty()) {
            AuthEntry authEntry = session
                    .getEntry(relyingParty.getName())
                    .orElseThrow(() -> new IllegalStateException("sessionID = " + session.getId() + ", relyingParty = " + relyingParty));

            String authCode = authEntry.getAuthCode();
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime authCodeExpiresIn = now.plus(relyingParty.getCodeTTL(), ChronoUnit.SECONDS);

            sessionService.updateAuthCodeExpired(authCode, fromLocal(authCodeExpiresIn))
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Observable.error(new AuthorizationException(ID.AUTH_CODE_EXPIRES_IN_UPDATE_FAILED)))
                .subscribe();

            long expiresIn = ChronoUnit.SECONDS.between(now, authCodeExpiresIn);
            return new SuccessResponse(createRedirectURI(relyingParty, request.getRedirectURI()), authCode, expiresIn);
        } else {

            String authorizationPageURI = relyingParty.getAuthorizationPageURI();
            URL url = new URL(authorizationPageURI);
            String redirectUrl = RedirectUtils.createRedirectURI(
                    url.getHost() + url.getPath() + "/" + request.getAuthContext().getFirst(),
                    url.getQuery()
            );

            return new NeedApprovalResponse(redirectUrl, request.getRedirectURI(), request.getMfaToken(), request.getClientId(), request.getScope(), request.getAuthContext());
        }
    }


    public Observable<RedirectResponse> incAuthLevel(VerifyEnrollRequest request) {
        return Observable.zip(
                clientService.loadAndAuthRelyingParty(request.getClientId(), null, false),
                sessionService.getByValidMFAToken(request.getMfaToken(), new Date()),
                ImmutablePair::new
        )
                .flatMap(p -> verifyAndUpdateUserInfo(p.right, request, p.left)
                        .map(result -> {
                            Session session = p.right;
                            RedirectResponse response = createResponse(session, p.left, request, result);
                            response.putRedirectParameters("session_token", session.getSessionToken());
                            return response;
                        })
                        .flatMap(response -> {
                            if(response instanceof SuccessResponse) {
                                Session session = p.right;
                                return deviceService
                                        .resolveDeviceInfo(
                                                request.getClientId(),
                                                Objects.toString(request.getAuthContext()),
                                                createDeviceInfoFromRequest(session, request),
                                                extractUserInfo(session)
                                        )
                                        .map(it -> response);
                            }
                            return Observable.just(response);
                        })
                );
    }

    private DeviceInfo createDeviceInfoFromRequest(Session session, VerifyEnrollRequest request) {
        return DeviceInfo.builder()
                .userId(session.getUserId())
                .deviceAppVersion(request.getDeviceAppVersion())
                .deviceId(request.getDeviceId())
                .deviceUUID(request.getDeviceUUID())
                .deviceModel(request.getDeviceModel())
                .deviceGeo(request.getDeviceGeo())
                .deviceLocale(request.getDeviceLocale())
                .deviceCity(request.getDeviceCity())
                .deviceName(request.getDeviceName())
                .deviceOSVersion(request.getDeviceOSVersion())
                .deviceBootTime(request.getDeviceBootTime())
                .deviceTimezone(request.getDeviceTimezone())
                .deviceIp(request.getDeviceIp())
                .deviceUserAgent(request.getDeviceUserAgent())
                .creationDate(new Date())
                .build();
    }


    private Observable<VerifyResult> verifyAndUpdateUserInfo(Session session, VerifyEnrollRequest request, RelyingParty relyingParty) {
        Map<String, Object> tokenInfo = extractUserInfo(session);
        UserInfo userInfo = new UserInfo(tokenProcessor.filterUserInfo(tokenInfo));
        Set<String> authContext = tokenProcessor.extractAuthContext(tokenInfo);

        return verify(request, userInfo, relyingParty)
                .flatMap(result -> updateUserInfo(session, request.getEnroll().getFirst(), userInfo.putAll(result.getData()), request.getScope(), authContext).map(b -> result));
    }

    private Observable<Boolean> updateUserInfo(Session session, String enroll, UserInfo userInfo, Set<String> scopes, Set<String> authContext) {
        AcrValues receivedAcrValues = session.getReceivedAcrValues();
        AcrValues newAcr = receivedAcrValues.add(enroll);
        session.setReceivedAcrValues(newAcr);
        return Observable.zip(
                sessionService.updateIdToken(session, userInfo, scopes, authContext),
                sessionService.updateAcrValues(session),
                (token, acr) -> token && acr);
    }

    private Map<String, Object> extractUserInfo(Session session) {
        return ofNullable(session)
                .map(Session::getUserInfo)
                .map(tokenCacheService::extractUserInfo)
                .orElseThrow(IllegalArgumentException::new);
    }

    private Observable<VerifyResult> verify(VerifyEnrollRequest request, UserInfo userInfo, RelyingParty relyingParty) {
        IdentityProvider identityProvider = identityProviderResolver.getProvider(relyingParty.getIdentityProvider());
        VerifyInput verifyInput = new VerifyInput(request.getData(), request.getEnroll(), userInfo, relyingParty.getName());
        return identityProvider.verify(verifyInput);
    }
}

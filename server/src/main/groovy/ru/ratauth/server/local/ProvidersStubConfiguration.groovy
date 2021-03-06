package ru.ratauth.server.local

import groovy.transform.CompileStatic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import ru.ratauth.entities.AcrValues
import ru.ratauth.exception.AuthorizationException
import ru.ratauth.exception.RegistrationException
import ru.ratauth.providers.auth.Activator
import ru.ratauth.providers.auth.AuthProvider
import ru.ratauth.providers.auth.Verifier
import ru.ratauth.providers.auth.dto.*
import ru.ratauth.providers.registrations.RegistrationProvider
import ru.ratauth.providers.registrations.dto.RegInput
import ru.ratauth.providers.registrations.dto.RegResult
import rx.Observable

import static ru.ratauth.providers.auth.dto.VerifyResult.Status.SUCCESS

/**
 * @author mgorelikov
 * @since 03/11/15
 */
@CompileStatic
class ProvidersStubConfiguration {
    public static final String REG_CREDENTIAL = 'credential'
    public static final String REG_CODE = '123'

    abstract class AbstractAuthProvider implements AuthProvider, RegistrationProvider {}

    abstract class AbstractProvider implements Activator, Verifier {}

    @Bean
    @Primary
    AbstractProvider provider() {
        return new AbstractProvider() {
            @Override
            String name() {
                return "username";
            }

            @Override
            Observable<ActivateResult> activate(ActivateInput input) {
                return Observable.just(new ActivateResult().with {
                    if (input.data.username) {
                        it.data.put("user_id", input.data.username.reverse())
                    }
                    return it
                })
            }

            @Override
            String version() { "-1" }

            @Override
            Observable<VerifyResult> verify(VerifyInput input) {
                if (input.data.password == "password") {
                    return Observable.just(new VerifyResult(status: SUCCESS).with {
                        if (input.data.username) {
                            it.data.put("user_id", input.data.username.reverse())
                        }
                        return it
                    })
                } else return Observable.error(new AuthorizationException(AuthorizationException.ID.CREDENTIALS_WRONG))
            }
        }
    }

    @Bean(name = 'STUB-provider')
    @Primary
    AbstractAuthProvider authProvider() {
        return new AbstractAuthProvider() {
            @Override
            Observable<AuthResult> authenticate(AuthInput input) {
                if (input.data.get(BaseAuthFields.USERNAME.val()) == 'login' && input.data.get(BaseAuthFields.PASSWORD.val()) == 'password')
                    return Observable.just(AuthResult.builder()
                            .data([(BaseAuthFields.USER_ID.val()): 'user_id'] as Map)
                            .acrValues(AcrValues.valueOf("login:sms"))
                            .status(AuthResult.Status.SUCCESS).build())
                else
                    return Observable.error(new AuthorizationException(AuthorizationException.ID.CREDENTIALS_WRONG))
            }

            @Override
            boolean isAuthCodeSupported() {
                return false
            }

            @Override
            Observable<Boolean> checkUserStatus(AuthInput input) {
                return Observable.just(true)
            }

            @Override
            Observable<RegResult> register(RegInput input) {
                if (!input.data.containsKey(BaseAuthFields.CODE.val())) { //first step of registration
                    //one step registration
                    if (input.data.get(BaseAuthFields.USERNAME.val()) == 'login' && input.data.get(BaseAuthFields.PASSWORD.val()) == 'password')
                        return Observable.just(RegResult.builder().acrValues(AcrValues.valueOf("login:sms")).data([(BaseAuthFields.USER_ID.val()): 'user_id'] as Map)
                                .status(RegResult.Status.SUCCESS).build())
                    else if (input.data.get(REG_CREDENTIAL) == 'credential') //two step registration
                        return Observable.just(RegResult.builder().data([
                                (BaseAuthFields.USERNAME.val()): 'login',
                                (BaseAuthFields.CODE.val())    : 'code'] as Map)
                                .status(RegResult.Status.NEED_APPROVAL).build())
                    else
                        return Observable.error(new RegistrationException("Registration failed"))
                } else {//second step of registration
                    if (input.data.get(BaseAuthFields.CODE.val()) == REG_CODE && input.data.get(BaseAuthFields.USERNAME.val()) == 'login')
                        return Observable.just(RegResult.builder().redirectUrl('http://relying.party/gateway')
                        // TODO@ruslan когда приходит код, это место должен обрабатывать сам сервер авторизации
                                .acrValues(AcrValues.valueOf("code"))
                                .data([(BaseAuthFields.USER_ID.val()): 'user_id'] as Map)
                                .status(RegResult.Status.SUCCESS).build())
                    else
                        return Observable.error(new RegistrationException("Registration failed"))
                }
            }

            @Override
            boolean isRegCodeSupported() {
                return true
            }
        }
    }
}

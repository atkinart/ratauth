package ru.ratauth.inmemory.ip;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import ru.ratauth.entities.AuthClient;
import ru.ratauth.entities.RelyingParty;
import ru.ratauth.entities.SessionClient;
import ru.ratauth.inmemory.ip.providers.InMemoryAuthProvider;
import ru.ratauth.inmemory.ip.providers.User;
import ru.ratauth.inmemory.ip.resource.ClassPathEntityResourceLoader;
import ru.ratauth.inmemory.ip.resource.EntityParser;
import ru.ratauth.inmemory.ip.resource.EntityResourceLoader;
import ru.ratauth.inmemory.ip.resource.FileEntityResourceLoader;
import ru.ratauth.inmemory.ip.services.InMemoryClientService;
import ru.ratauth.inmemory.ip.services.InMemorySessionService;
import ru.ratauth.inmemory.ip.services.InMemoryTokenCacheService;
import ru.ratauth.providers.auth.AuthProvider;
import ru.ratauth.providers.registrations.RegistrationProvider;
import ru.ratauth.services.ClientService;
import ru.ratauth.services.SessionService;
import ru.ratauth.services.TokenCacheService;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static org.springframework.core.Ordered.LOWEST_PRECEDENCE;

@Configuration
@ComponentScan
@ConditionalOnClass({ClientService.class,
    SessionService.class,
    TokenCacheService.class,
    AuthProvider.class,
    RegistrationProvider.class})
@ConditionalOnMissingBean({ClientService.class,
    SessionService.class,
    TokenCacheService.class,
    AuthProvider.class,
    RegistrationProvider.class})
@AutoConfigureOrder(LOWEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "ru.ratauth.inmemory.ip", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InMemoryIdentityProviderAutoConfiguration {

  private static final String RELYING_PARTY_FILE = "relying-party.json";
  private static final String AUTH_CLIENT_FILE = "auth-client.json";
  private static final String SESSION_CLIENT_FILE = "session-client.json";
  private static final String USERS_FILE = "users.json";
  private static final String RP_SOURCE_DIR = "rp.source.dir";

  @Autowired
  private Environment environment;

  @Bean(name = "STUB")
  public InMemoryAuthProvider authProvider(EntityParser entityParser) {
    return new InMemoryAuthProvider(asList(entityParser.load(USERS_FILE, User[].class)));
  }

  @Bean
  public ClientService clientService(EntityParser entityParser) {
    return new InMemoryClientService(asList(entityParser.load(RELYING_PARTY_FILE, RelyingParty[].class)),
        toList(entityParser.load(AUTH_CLIENT_FILE, AuthClient[].class)),
        toList(entityParser.load(SESSION_CLIENT_FILE, SessionClient[].class))
    );
  }

  @Bean
  public SessionService sessionService() {
    return new InMemorySessionService();
  }

  @Bean
  public TokenCacheService tokenCacheService() {
    return new InMemoryTokenCacheService();
  }

  @Bean
  public EntityParser entityParser(EntityResourceLoader entityResourceLoader) {
    return new EntityParser(entityResourceLoader);
  }

  @Bean
  public EntityResourceLoader fileEntityResourceLoader() {
    String rpSourceDir = environment.getProperty(RP_SOURCE_DIR);
    if (rpSourceDir != null) {
      return new FileEntityResourceLoader(rpSourceDir);
    }
    return new ClassPathEntityResourceLoader();
  }

  private <T> List<T> toList(T[] array) {
    return stream(array).collect(Collectors.toList());
  }

}
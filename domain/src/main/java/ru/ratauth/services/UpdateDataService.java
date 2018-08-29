package ru.ratauth.services;

import java.time.LocalDateTime;
import ru.ratauth.entities.UpdateDataEntry;
import rx.Observable;

public interface UpdateDataService {

    /**
     * Check is session need update some data
     * @param sessionToken
     */
    Observable<UpdateDataEntry> getUpdateData(String sessionToken);

    /**
     * Create entry with auto generated update_code = random.uuid
     * @param sessionToken - id
     * @param reason
     * @param service - service id
     * @param uri - redirect uri for the service
     * @return
     */
    Observable<UpdateDataEntry> create(String sessionToken, String reason, String service, String uri);

    /**
     * Create code and put to mongo
     * @param sessionToken
     * @return
     */
    Observable<String> getCode(String sessionToken, LocalDateTime expiresAt);

    /**
     * Method return entry if token exists and is not expired
     * @param code
     * @return
     */
    Observable<UpdateDataEntry> getValidEntry(String code);

    /**
     * Invalidate update token
     * @param code update_token parameter
     * @return "true" if request complete
     */
    Observable<Boolean> invalidate(String code);
}
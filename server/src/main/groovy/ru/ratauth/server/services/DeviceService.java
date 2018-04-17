package ru.ratauth.server.services;

import ru.ratauth.entities.DeviceInfo;
import rx.Observable;

public interface DeviceService {

    Observable<DeviceInfo> resolveDeviceInfo(DeviceInfo deviceInfo);

}

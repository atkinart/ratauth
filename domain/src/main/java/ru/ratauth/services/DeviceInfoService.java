package ru.ratauth.services;

import ru.ratauth.entities.DeviceInfo;
import rx.Observable;

public interface DeviceInfoService {

    Observable<DeviceInfo> create(DeviceInfo deviceInfo);

}
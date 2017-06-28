package ru.ratauth.providers.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import ru.ratauth.entities.UserInfo;

import java.util.Map;

@Data
@AllArgsConstructor
public class VerifierInput {

    private Map<String, String> data;
    private UserInfo userInfo;
    private String relyingParty;

}

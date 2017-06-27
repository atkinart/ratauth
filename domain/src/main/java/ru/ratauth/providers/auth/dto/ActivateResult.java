package ru.ratauth.providers.auth.dto;

import lombok.*;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActivateResult {

    @Singular("field")
    private Map<String, String> data;
    private String relyingParty;

}

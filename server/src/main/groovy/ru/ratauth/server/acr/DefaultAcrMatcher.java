package ru.ratauth.server.acr;

import ratpack.http.Request;

import static java.util.Objects.requireNonNull;
import static javaslang.Tuple.of;

public class DefaultAcrMatcher implements AcrMatcher {

    @Override
    public String match(Request request) {
        return of(request.getQueryParams().get("acr"))
                .map(acr -> requireNonNull(acr, "acr can not be null"))
                .map(AcrValue::valueOf)
                .map(AcrValue::getAcrValues)
                .map(acrValues -> acrValues.get(0))
                ._1();
    }
}
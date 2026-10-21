package com.trustvault.trustvault.controller;

import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfController {

    @GetMapping(
            value = "/api/csrf",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public CsrfTokenResponse getCsrfToken(CsrfToken csrfToken) {
        return new CsrfTokenResponse(
                csrfToken.getToken(),
                csrfToken.getHeaderName(),
                csrfToken.getParameterName()
        );
    }

    public record CsrfTokenResponse(
            String token,
            String headerName,
            String parameterName) {
    }
}
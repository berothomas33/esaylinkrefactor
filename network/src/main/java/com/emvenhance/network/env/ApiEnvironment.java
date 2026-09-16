package com.emvenhance.network.env;

/**
 * The set of host environments this build can talk to. Unlike the legacy project's single
 * hardcoded {@code Constants.EndPoint} (with a second URL commented out and an unused
 * {@code isTestingEnvironment} flag), each entry here carries its own real base URL and switching
 * is a first-class runtime operation — see {@link EnvironmentProvider}.
 */
public enum ApiEnvironment {

    DEVELOPMENT("https://dev-api.example.com/"),
    STAGING("https://staging-api.example.com/"),
    PRODUCTION("https://api.example.com/");

    private final String baseUrl;

    ApiEnvironment(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}

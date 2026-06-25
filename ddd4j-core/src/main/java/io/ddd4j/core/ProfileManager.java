package io.ddd4j.core;


import org.springframework.core.env.Environment;

public class ProfileManager {

    private final Environment environment;

    public ProfileManager(Environment environment) {
        this.environment = environment;
    }

    public String getOneActive() {
        for (String profileName : environment.getActiveProfiles()) {
            return profileName;
        }
        return null;
    }
}

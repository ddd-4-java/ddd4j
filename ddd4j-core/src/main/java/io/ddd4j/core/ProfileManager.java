package io.ddd4j.core;



public class ProfileManager {

    private final java.util.function.Supplier<String[]> activeProfilesSupplier;

    public ProfileManager(java.util.function.Supplier<String[]> activeProfilesSupplier) {
        this.activeProfilesSupplier = activeProfilesSupplier;
    }

    public String getOneActive() {
        for (String profileName : activeProfilesSupplier.get()) {
            return profileName;
        }
        return null;
    }
}

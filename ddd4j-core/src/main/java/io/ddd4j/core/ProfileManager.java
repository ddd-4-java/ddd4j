package io.ddd4j.core;



/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
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

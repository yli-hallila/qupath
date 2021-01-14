package qupath.edu.models;

public class ServerConfiguration {

    private final String version;
    private final boolean guestLoginEnabled;
    private final boolean simpleLoginEnabled;
    private final boolean microsoftLoginEnabled;

    public ServerConfiguration(String version, boolean guestLoginEnabled, boolean simpleLoginEnabled, boolean microsoftLoginEnabled) {
        this.version = version;
        this.guestLoginEnabled = guestLoginEnabled;
        this.simpleLoginEnabled = simpleLoginEnabled;
        this.microsoftLoginEnabled = microsoftLoginEnabled;
    }

    public String getVersion() {
        return version;
    }

    public boolean isGuestLoginEnabled() {
        return guestLoginEnabled;
    }

    public boolean isSimpleLoginEnabled() {
        return simpleLoginEnabled;
    }

    public boolean isMicrosoftLoginEnabled() {
        return microsoftLoginEnabled;
    }
}

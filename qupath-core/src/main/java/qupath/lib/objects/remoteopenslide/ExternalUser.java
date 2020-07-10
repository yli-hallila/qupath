package qupath.lib.objects.remoteopenslide;

import java.util.List;

public class ExternalUser {

    private String id;
    private String name;
    private String email;
    private String organization;
    private String organizationId;
    private List<String> roles;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getOrganization() {
        return organization;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public List<String> getRoles() {
        return roles;
    }
}

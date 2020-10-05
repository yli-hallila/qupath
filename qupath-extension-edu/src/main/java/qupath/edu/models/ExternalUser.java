package qupath.edu.models;

import java.util.List;

public class ExternalUser {

    private String id;
    private String name;
    private String email;
    private ExternalOrganization organization;
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

    public ExternalOrganization getOrganization() {
        return organization;
    }

    public String getOrganizationName() {
        return organization.getName();
    }

    public String getOrganizationId() {
        return organization.getId();
    }

    public List<String> getRoles() {
        return roles;
    }
}

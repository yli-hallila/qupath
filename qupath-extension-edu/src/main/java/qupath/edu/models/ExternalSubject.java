package qupath.edu.models;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ExternalSubject {

    private String id;
    private String name;
    private String organization;

    private List<ExternalProject> projects;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    /**
     * Returns an alphabetically sorted list of projects
     * @return
     */
    public List<ExternalProject> getProjects() {
        return projects.stream().sorted(Comparator.comparing(ExternalProject::getName)).collect(Collectors.toList());
    }

    public void setProjects(List<ExternalProject> projects) {
        this.projects = projects;
    }
}

package qupath.edu.models;

import qupath.edu.lib.RemoteOpenslide;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ExternalSubject {

    private String id;
    private String name;
    private String workspace;

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

    public String getWorkspace() {
        return workspace;
    }

    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    /**
     * Returns an alphabetically sorted list of projects with hidden projects filtered out
     * @return List of ExternalProjects
     */
    public List<ExternalProject> getProjects() {
        return projects.stream()
                .filter(project -> !(project.isHidden() && !RemoteOpenslide.isOwner(project.getOwner())))
                .sorted(Comparator.comparing(ExternalProject::getName))
                .collect(Collectors.toList());
    }

    public void setProjects(List<ExternalProject> projects) {
        this.projects = projects;
    }
}

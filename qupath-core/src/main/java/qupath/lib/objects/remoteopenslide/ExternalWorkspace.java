package qupath.lib.objects.remoteopenslide;

import java.util.ArrayList;

public class ExternalWorkspace {

    private String name;
    private String id;
    private String owner;
    private ArrayList<ExternalProject> projects;

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public ArrayList<ExternalProject> getProjects() {
        return projects;
    }
}
package qupath.lib.objects.remoteopenslide;

import java.util.ArrayList;

public class ExternalWorkspace {

    private String name;
    private String id;
    private ArrayList<ExternalProject> projects;

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public ArrayList<ExternalProject> getProjects() {
        return projects;
    }
}
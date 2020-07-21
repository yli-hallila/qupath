package qupath.lib.objects.remoteopenslide;

import java.util.ArrayList;

public class ExternalWorkspace {

    private String name;
    private String id;
    private Owner owner;
    private ArrayList<ExternalProject> projects;

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public String getOwner() {
        return owner.id;
    }

    public ArrayList<ExternalProject> getProjects() {
        return projects;
    }

    public static class Owner {

        private String id;

        private String name;

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
    }
}

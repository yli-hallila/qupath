package qupath.edu.models;

import java.util.List;

public class ExternalWorkspace {

    private String name;
    private String id;
    private ExternalOwner owner;
    private List<ExternalSubject> subjects;

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public String getOwner() {
        return owner.getId();
    }

    public List<ExternalSubject> getSubjects() {
        return subjects;
    }

}

package qupath.edu.models;

import java.util.List;
import java.util.Optional;

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

    public String getOwnerId() {
        return owner.getId();
    }

    public List<ExternalSubject> getSubjects() {
        return subjects;
    }

    public Optional<ExternalSubject> findSubject(String id) {
        return subjects.stream()
                .filter(subject -> subject.getId().equalsIgnoreCase(id))
                .findFirst();
    }
}

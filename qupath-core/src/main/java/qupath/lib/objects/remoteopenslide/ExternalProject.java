package qupath.lib.objects.remoteopenslide;

public class ExternalProject {

    private String id;
    private String name;
    private String description;
    private ExternalWorkspace.Owner owner;
    private String ownerReadable;
    private String timestamp;

    public String getId() {
        return id;
    }

    public String getIdWithTimestamp() {
        if (hasTimestamp()) {
            return id + ":" + timestamp;
        }

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

    public String getDescription() {
        return description == null ? "" : description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOwner() {
        return owner.getId();
    }

    public void setOwner(String owner) {
        this.owner.setId(owner);
    }

    public String getOwnerReadable() {
        return owner.getName();
    }

    public void setOwnerReadable(String ownerReadable) {
        this.owner.setName(ownerReadable);
    }

    public boolean hasTimestamp() {
        return timestamp != null;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}

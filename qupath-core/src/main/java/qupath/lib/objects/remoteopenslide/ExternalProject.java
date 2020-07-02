package qupath.lib.objects.remoteopenslide;

public class ExternalProject {

    private String id;
    private String name;
    private String description;
    private String owner;
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
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getOwnerReadable() {
        return ownerReadable;
    }

    public void setOwnerReadable(String ownerReadable) {
        this.ownerReadable = ownerReadable;
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

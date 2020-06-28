package qupath.lib.objects.remoteopenslide;

import java.util.Map;

public class ExternalSlide {

    private String name;
    private String id;
    private String owner;

    private Map<String, String> parameters;

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    @Override
    public String toString() {
        return "ExternalSlide{" +
                "name='" + name + '\'' +
                ", uuid='" + id + '\'' +
                ", owner='" + owner + '\'' +
                '}';
    }
}

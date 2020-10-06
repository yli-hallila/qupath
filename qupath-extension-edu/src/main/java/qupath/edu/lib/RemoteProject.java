package qupath.edu.lib;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.io.GsonTools;
import qupath.lib.io.PathIO;
import qupath.lib.objects.MetadataMap;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.ResourceManager;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Data structure to store multiple images and their respective data.
 * Stores everything mainly in-memory and syncs it with an external server.
 *
 * Work in progress.
 *
 * Based on {@link qupath.lib.projects.DefaultProject}
 *
 */
public class RemoteProject implements Project<BufferedImage> {

	public final static String IMAGE_ID = "PROJECT_ENTRY_ID";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private List<RemoteProjectImageEntry> images = new ArrayList<>();

	private String LATEST_VERSION = GeneralTools.getVersion();
	private String version = null;

	private String name;
	private File file;

	private boolean maskNames = false;
	private MetadataMap metadata = null;

	private long creationTimestamp;
	private long modificationTimestamp;

	public RemoteProject(File fileProject) throws IOException {
		this.file = fileProject;

		try (BufferedReader fileReader = Files.newBufferedReader(fileProject.toPath(), StandardCharsets.UTF_8)) {
			Gson gson = GsonTools.getInstance();
			JsonObject element = gson.fromJson(fileReader, JsonObject.class);

			creationTimestamp = element.get("createTimestamp").getAsLong();
			modificationTimestamp = element.get("modifyTimestamp").getAsLong();

			if (element.has("version"))
				version = element.get("version").getAsString();

			if (version == null)
				throw new IOException("Older projects are not supported in this version of QuPath, sorry!");

			List<RemoteProjectImageEntry> images = element.has("images") ? gson.fromJson(element.get("images"), new TypeToken<ArrayList<RemoteProjectImageEntry>>() {}.getType()) : Collections.emptyList();
			for (RemoteProjectImageEntry entry: images) {
				addImage(new RemoteProjectImageEntry(entry));
			}

			if (element.has("metadata")) {
				metadata = gson.fromJson(element.get("metadata").getAsString(), MetadataMap.class);
			}

		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	@Override
	public List<PathClass> getPathClasses() {
		return Collections.emptyList();
	}

	@Override
	public boolean getMaskImageNames() {
		return maskNames;
	}

	@Override
	public void setMaskImageNames(boolean maskNames) {
		this.maskNames = maskNames;
	}

	@Override
	public boolean setPathClasses(Collection<? extends PathClass> pathClasses) {
		return true;
	}

	@Override
	public URI getURI() {
		return file.toURI();
	}

	@Override
	public URI getPreviousURI() {
		return null;
	}

	@Override
	public String getVersion() {
		return version;
	}

	@Override
	public Path getPath() {
		return file.toPath();
	}

	@Override
	public Project<BufferedImage> createSubProject(String name, Collection<ProjectImageEntry<BufferedImage>> projectImageEntries) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isEmpty() {
		return images.isEmpty();
	}

	@Override
	public ProjectImageEntry<BufferedImage> addImage(ImageServerBuilder.ServerBuilder<BufferedImage> builder) throws IOException {
		var entry = new RemoteProjectImageEntry(builder, null, null, null, null);

		images.add(entry);

		return entry;
	}

	private boolean addImage(ProjectImageEntry<BufferedImage> entry) {
		if (entry instanceof RemoteProjectImageEntry) {
			images.add((RemoteProjectImageEntry) entry);
			return true;
		}

		try {
			return addImage(new RemoteProjectImageEntry(entry.getServerBuilder(), null, entry.getImageName(), entry.getDescription(), entry.getMetadataMap()));
		} catch (Exception e) {
			logger.error("Unable to add entry " + entry, e);
		}

		return false;
	}

	@Override
	public ProjectImageEntry<BufferedImage> addDuplicate(ProjectImageEntry<BufferedImage> entry, boolean copyData) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public ProjectImageEntry<BufferedImage> getEntry(ImageData<BufferedImage> imageData) {
		String id = (String) imageData.getProperty(IMAGE_ID);

		for (var entry : images) {
			if (entry.getID().equals(id)) {
				return entry;
			}
		}

		return null;
	}

	/**
	 * This implementation does not store any data separately, so
	 * removing an image will remove all of its data also.
	 *
	 * @param entry ProjectImageEntry to remove
	 * @param removeAllData ignored
	 */
	@Override public void removeImage(ProjectImageEntry<?> entry, boolean removeAllData) {
		images.remove(entry);
	}

	@Override
	public void removeAllImages(Collection<ProjectImageEntry<BufferedImage>> projectImageEntries, boolean removeAllData) {
		images.clear();
	}

	@Override
	public void syncChanges() throws IOException {
		if (file == null) {
			throw new IOException("No file found, cannot write project: " + this);
		}

		Gson gson = GsonTools.getInstance(true);

		JsonObject builder = new JsonObject();
		builder.addProperty("version", LATEST_VERSION);
		builder.addProperty("createTimestamp", getCreationTimestamp());
		builder.addProperty("modifyTimestamp", getModificationTimestamp());
		builder.addProperty("uri", file.toURI().toString());

		if (metadata != null) {
			builder.addProperty("metadata", gson.toJson(metadata));
		}

		builder.add("images", gson.toJsonTree(images));

		// Write project to a new file
		var pathProject = file.toPath();
		var pathTempNew = new File(file.getAbsolutePath() + ".tmp").toPath();
		logger.debug("Writing project to {}", pathTempNew);

		try (var writer = Files.newBufferedWriter(pathTempNew, StandardCharsets.UTF_8)) {
			gson.toJson(builder, writer);
		}

		// If we already have a project, back it up
		if (file.exists()) {
			var pathBackup = new File(file.getAbsolutePath() + ".backup").toPath();
			logger.debug("Backing up existing project to {}", pathBackup);
			Files.move(pathProject, pathBackup, StandardCopyOption.REPLACE_EXISTING);
		}

		// If this succeeded, rename files
		logger.debug("Renaming project to {}", pathProject);
		Files.move(pathTempNew, pathProject, StandardCopyOption.REPLACE_EXISTING);

		// TODO: Upload new version to server. This perhaps isn't the best place to sync, as this
		//       method is executed multiple times without the user specifically asking to save.
	}

	@Override
	public List<ProjectImageEntry<BufferedImage>> getImageList() {
		return new ArrayList<>(images);
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public long getCreationTimestamp() {
		return creationTimestamp;
	}

	@Override
	public long getModificationTimestamp() {
		return modificationTimestamp;
	}

	@Override
	public ResourceManager.Manager<String> getScripts() {
		throw new UnsupportedOperationException();
	}

	@Override
	public ResourceManager.Manager<ObjectClassifier<BufferedImage>> getObjectClassifiers() {
		throw new UnsupportedOperationException();
	}

	@Override
	public ResourceManager.Manager<PixelClassifier> getPixelClassifiers() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object storeMetadataValue(String key, String value) {
		if (metadata == null)
			metadata = new MetadataMap();
		return metadata.put(key, value);
	}

	@Override
	public Object retrieveMetadataValue(String key) {
		return metadata == null ? null : metadata.get(key);
	}

	class RemoteProjectImageEntry implements ProjectImageEntry<BufferedImage> {

		/**
		 * ServerBuilder. This should be lightweight & capable of being JSON-ified.
		 */
		private ImageServerBuilder.ServerBuilder<BufferedImage> serverBuilder;

		/**
		 * Unique name that will be used to identify associated data files.
		 */
		private Long entryID;

		/**
		 * Randomized name that will be used when masking image names.
		 */
		private String randomizedName = UUID.randomUUID().toString();

		/**
		 * Image name to display.
		 */
		private String imageName;

		/**
		 * Image description to display.
		 */
		private String description;

		/**
		 * Map of associated metadata for the entry.
		 */
		private Map<String, String> metadata = new LinkedHashMap<>();

		/**
		 * Serialized ImageData.
		 */
		private ImageData<BufferedImage> imageData;

		/**
		 * Thumbnail for this slide.
		 */
		private transient BufferedImage thumbnail;

		RemoteProjectImageEntry(final ImageServerBuilder.ServerBuilder<BufferedImage> builder, final Long entryID, final String imageName, final String description, final Map<String, String> metadataMap) {
			this.serverBuilder = builder;

			if (entryID == null)
				this.entryID = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
			else
				this.entryID = entryID;

			if (imageName == null)
				this.imageName = "Image " + entryID;
			else
				this.imageName = imageName;

			if (description != null)
				setDescription(description);

			if (metadataMap != null)
				metadata.putAll(metadataMap);

		}

		public RemoteProjectImageEntry(RemoteProjectImageEntry entry) {
			this.serverBuilder = entry.serverBuilder;
			this.entryID = entry.entryID;
			this.imageName = entry.imageName;
			this.description = entry.description;
			this.metadata = entry.metadata;
			this.imageData = entry.imageData;
		}

		@Override
		public String getID() {
			return Long.toString(entryID);
		}

		@Override
		public void setImageName(String name) {
			this.imageName = name;
		}

		@Override
		public String getImageName() {
			if (maskNames) {
				return randomizedName;
			}

			return imageName;
		}

		@Override
		public String getOriginalImageName() {
			return imageName;
		}

		@Override
		public Path getEntryPath() {
			return null;
		}

		@Override
		public String removeMetadataValue(final String key) {
			return metadata.remove(key);
		}

		@Override
		public String getMetadataValue(final String key) {
			return metadata.get(key);
		}

		@Override
		public String putMetadataValue(final String key, final String value) {
			return metadata.put(key, value);
		}

		@Override
		public boolean containsMetadata(final String key) {
			return metadata.containsKey(key);
		}

		@Override
		public String getDescription() {
			return description;
		}

		@Override
		public void setDescription(String description) {
			this.description = description;
		}

		@Override
		public void clearMetadata() {
			this.metadata.clear();
		}

		@Override
		public Map<String, String> getMetadataMap() {
			return Collections.unmodifiableMap(metadata);
		}

		@Override
		public Collection<String> getMetadataKeys() {
			return Collections.unmodifiableSet(metadata.keySet());
		}

		@Override
		public ImageServerBuilder.ServerBuilder<BufferedImage> getServerBuilder() {
			return serverBuilder;
		}

		@Override
		public ImageData<BufferedImage> readImageData() throws IOException {
			if (containsMetadata("imageData")) {
				InputStream is = new ByteArrayInputStream(getMetadataValue("imageData").getBytes());

				return PathIO.readImageData(is, null, null, BufferedImage.class);
			}

			// TODO: What if serverBuilder is null? Can it be null?

			try {
				ImageData<BufferedImage> imageData = new ImageData<>(serverBuilder.build(), null, ImageData.ImageType.BRIGHTFIELD_H_E);
				imageData.setProperty(IMAGE_ID, entryID.toString());

				return imageData;
			} catch (Exception e) {
				throw new IOException(e);
			}
		}

		@Override
		public void saveImageData(ImageData<BufferedImage> imageData) throws IOException {
			imageData.getHistoryWorkflow().clear();

			OutputStream os = new ByteArrayOutputStream();
			PathIO.writeImageData(os, imageData);

			putMetadataValue("imageData", os.toString());
		}

		@Override
		public PathObjectHierarchy readHierarchy() throws IOException {
			return new PathObjectHierarchy();
		}

		@Override
		public boolean hasImageData() {
			return containsMetadata("imageData");
		}

		@Override
		public String getSummary() {
			StringBuilder sb = new StringBuilder();

			sb.append(getImageName()).append("\n");
			sb.append("ID:\t").append(getID()).append("\n\n");

			if (!getMetadataMap().isEmpty()) {
				for (Map.Entry<String, String> mapEntry : getMetadataMap().entrySet()) {
					if (mapEntry.getKey().equals("imageData")) {
						continue;
					}

					sb.append(mapEntry.getKey()).append(":\t").append(mapEntry.getValue()).append("\n");
				}

				sb.append("\n");
			}

			return sb.toString();
		}

		@Override
		public BufferedImage getThumbnail() {
			if (thumbnail != null) {
				return thumbnail;
			}

			try {
				thumbnail = serverBuilder.build().getDefaultThumbnail(0, 0);
			} catch (Exception e) {
				logger.error("Unable to generate thumbnail for {}", entryID, e);
			}

			return thumbnail;
		}

		@Override
		public void setThumbnail(BufferedImage img) {
			this.thumbnail = img;
		}

		@Override
		public Collection<URI> getServerURIs() {
			if (serverBuilder == null) {
				return Collections.emptyList();
			}

			return serverBuilder.getURIs();
		}

		@Override
		public boolean updateServerURIs(Map<URI, URI> replacements) {
			var builderBefore = serverBuilder;
			serverBuilder = serverBuilder.updateURIs(replacements);

			return builderBefore != serverBuilder;
		}

		@Override
		public ResourceManager.Manager<ImageServer<BufferedImage>> getImages() {
			throw new UnsupportedOperationException();
		}
	}
}

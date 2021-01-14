package qupath.edu.lib;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.EduExtension;
import qupath.edu.exceptions.HttpException;
import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.commands.ProjectImportImagesCommand;
import qupath.lib.gui.dialogs.Dialogs;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
	public final static String PROJECT_INFORMATION = "PROJECT_INFORMATION";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private List<RemoteProjectImageEntry> images = new ArrayList<>();

	private String LATEST_VERSION = GeneralTools.getVersion();
	private String version = null;

	private String name;
	private String id;

	private boolean maskNames = false;
	private MetadataMap metadata = null;

	private long creationTimestamp;
	private long modificationTimestamp;

	public RemoteProject(String projectData) throws IOException {
		Gson gson = GsonTools.getInstance();
		JsonObject element = gson.fromJson(projectData, JsonObject.class);

		id = element.get("id").getAsString();
		creationTimestamp = element.get("createTimestamp").getAsLong();
		modificationTimestamp = element.get("modifyTimestamp").getAsLong();

		if (element.has("version")) {
			version = element.get("version").getAsString();
		} else {
			throw new IOException("Older projects are not supported in this version of QuPath, sorry!");
		}

		if (element.has("images")) {
			RemoteProjectImageEntry[] images = gson.fromJson(element.get("images"), RemoteProjectImageEntry[].class);
			addImages(images);
		}

		if (element.has("metadata")) {
			metadata = gson.fromJson(new String(Base64.getDecoder().decode(element.get("metadata").getAsString()), StandardCharsets.UTF_8), MetadataMap.class);
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
		return null;
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
		return null;
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
	public ProjectImageEntry<BufferedImage> addImage(ImageServerBuilder.ServerBuilder<BufferedImage> builder) {
		var entry = new RemoteProjectImageEntry(builder, null, null, null, null);

		images.add(entry);

		return entry;
	}

	public void addImages(RemoteProjectImageEntry... entries) {
		for (RemoteProjectImageEntry entry : entries) {
			addImage(new RemoteProjectImageEntry(entry));
		}
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
	 * @param removeAllData ignored: all data always removed
	 */
	@Override
	public void removeImage(ProjectImageEntry<?> entry, boolean removeAllData) {
		// TODO: Is this an irrelevant check?
		if (entry instanceof RemoteProjectImageEntry) {
			images.remove(entry);
		} else {
			logger.error("Cannot remove image, is not instance of RemoteProjectImageEntry. [{}]", entry.toString());
		}
	}

	@Override
	public void removeAllImages(Collection<ProjectImageEntry<BufferedImage>> projectImageEntries, boolean removeAllData) {
		for (var entry : projectImageEntries) {
			removeImage(entry, removeAllData);
		}
	}

	@Override
	public void syncChanges() throws IOException {
		Gson gson = GsonTools.getInstance(true);

		JsonObject builder = new JsonObject();
		builder.addProperty("id", id);
		builder.addProperty("version", LATEST_VERSION);
		builder.addProperty("createTimestamp", getCreationTimestamp());
		builder.addProperty("modifyTimestamp", getModificationTimestamp());

		if (metadata != null) {
			builder.addProperty("metadata", Base64.getEncoder().encodeToString(gson.toJson(metadata).getBytes(StandardCharsets.UTF_8)));
		}

		builder.add("images", gson.toJsonTree(images));

		syncChangesToServer(gson.toJson(builder));
	}

	private boolean askedToLogin = false;

	// TODO: WIP
	private void syncChangesToServer(String projectData) {
		var hasWriteAccess = false;
		var makeCopy = false;

		try {
			hasWriteAccess = RemoteOpenslide.hasPermission(getId());
		} catch (HttpException e) {
			logger.error("Error while syncing project.", e);

			// TODO: Store a local copy of changes and sync again when connection works again?

			Dialogs.showErrorMessage(
				"Sync error",
				"Error while syncing changes to server. If you exit now your changes will be lost; please retry later."
			);
		}

		if (RemoteOpenslide.hasRole(Roles.MANAGE_PERSONAL_PROJECTS) && !hasWriteAccess) {
			var response = Dialogs.showYesNoDialog("Save changes",
				"You've made changes to this project but you don't have the required permissions to save these changes." +
				"\n\n" +
				"Do you want to make a personal copy of this project which you can edit?"
			);

			if (response) {
				makeCopy = true;
			} else {
				return;
			}
		} else if (!hasWriteAccess && !askedToLogin) {
			var login = Dialogs.showYesNoDialog("Save changes",
				"You've made changes to this project but you're not logged in." +
				"\n\n" +
				"Do you wish to login?"
			);

			askedToLogin = true;

			if (login) {
				RemoteOpenslide.logout();
				EduExtension.showWorkspaceOrLoginDialog();
			}

			return;
		}

		if (makeCopy) {
			// TODO: Reimplement personal projects
		} else if (hasWriteAccess) {
			logger.debug("Uploading project to server");

			RemoteOpenslide.uploadProject(id, projectData);

			Dialogs.showInfoNotification("[Debug]", "Changes synced to server.");
			logger.debug("Uploaded to server");
		}
	}

	@Override
	public List<ProjectImageEntry<BufferedImage>> getImageList() {
		return new ArrayList<>(images);
	}

	@Override
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getId() {
		return id;
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

	public Object storeMetadataValue(String key, String value) {
		if (metadata == null) {
			metadata = new MetadataMap();
		}

		return metadata.put(key, value);
	}

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
		 * ImageData Base64 encoded.
		 */
		private String imageData;

		/**
		 * Thumbnail for this slide.
		 */
		private transient BufferedImage cachedThumbnail;

		/**
		 * Base64 decoded PNG thumbnail
		 */
		private String thumbnail;

		RemoteProjectImageEntry(ImageServerBuilder.ServerBuilder<BufferedImage> builder, Long entryID, String imageName, String description, Map<String, String> metadataMap) {
			this.serverBuilder = builder;

			if (entryID == null) {
				this.entryID = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
			} else {
				this.entryID = entryID;
			}

			if (imageName == null) {
				this.imageName = "Image " + entryID;
			} else {
				this.imageName = imageName;
			}

			if (description != null) {
				setDescription(description);
			}

			if (metadataMap != null) {
				metadata.putAll(metadataMap);
			}
		}

		public RemoteProjectImageEntry(RemoteProjectImageEntry entry) {
			this.serverBuilder = entry.serverBuilder;
			this.entryID = entry.entryID;
			this.imageName = entry.imageName;
			this.description = entry.description;
			this.metadata = entry.metadata;
			this.imageData = entry.imageData;
			this.thumbnail = entry.thumbnail;
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
			if (imageData != null) {
				ByteArrayInputStream is = new ByteArrayInputStream(Base64.getDecoder().decode(imageData));

				ImageData<BufferedImage> imageData = PathIO.readImageData(is, null, null, BufferedImage.class);
				imageData.setChanged(false);

				is.close();

				return imageData;
			}

			// TODO: What if serverBuilder is null? Can it be null?

			try {
				ImageData<BufferedImage> imageData = new ImageData<>(serverBuilder.build(), null, null);
				imageData.setProperty(IMAGE_ID, entryID.toString());
				imageData.setChanged(false);

				saveImageData(imageData);

				return imageData;
			} catch (Exception e) {
				throw new IOException(e);
			}
		}

		@Override
		public void saveImageData(ImageData<BufferedImage> imageData) throws IOException {
			imageData.getHistoryWorkflow().clear();

			ByteArrayOutputStream os = new ByteArrayOutputStream();
			PathIO.writeImageData(os, imageData);

			this.imageData = Base64.getEncoder().encodeToString(os.toByteArray());
		}

		@Override
		public PathObjectHierarchy readHierarchy() throws IOException {
			return new PathObjectHierarchy();
		}

		@Override
		public boolean hasImageData() {
			return imageData != null;
		}

		@Override
		public String getSummary() {
			StringBuilder sb = new StringBuilder();

			sb.append(getImageName()).append("\n");
			sb.append("ID:\t").append(getID()).append("\n\n");

			if (!getMetadataMap().isEmpty()) {
				for (Map.Entry<String, String> mapEntry : getMetadataMap().entrySet()) {
					sb.append(mapEntry.getKey()).append(":\t").append(mapEntry.getValue()).append("\n");
				}

				sb.append("\n");
			}

			return sb.toString();
		}

		@Override
		public BufferedImage getThumbnail() {
			if (cachedThumbnail != null) {
				return cachedThumbnail;
			}

			if (thumbnail != null) {
				byte[] bytes = Base64.getDecoder().decode(thumbnail);

				try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes)) {
					cachedThumbnail = ImageIO.read(bis);
				} catch (IOException e) {
					logger.error("Error while reading thumbnail for {}. Generating a new one...", entryID, e);
					generateThumbnail();
				}
			} else {
				generateThumbnail();
			}

			return cachedThumbnail;
		}

		@Override
		public void setThumbnail(BufferedImage img) {
			this.cachedThumbnail = img;

			try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
				ImageIO.write(cachedThumbnail, "png", os);

				thumbnail = Base64.getEncoder().encodeToString(os.toByteArray());
			} catch (Exception e) {
				logger.error("Unable to generate thumbnail for {}", entryID, e);
			}
		}

		private void generateThumbnail() {
			try {
				setThumbnail(ProjectImportImagesCommand.getThumbnailRGB(serverBuilder.build(), null));
			} catch (Exception e) {
				logger.error("Unable to generate thumbnail for {}", entryID, e);
			}
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

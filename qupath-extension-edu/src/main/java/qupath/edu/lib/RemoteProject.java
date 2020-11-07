package qupath.edu.lib;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.EduExtension;
import qupath.edu.exceptions.HttpException;
import qupath.edu.gui.WorkspaceManager;
import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.common.GeneralTools;
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
	public final static String PROJECT_INFORMATION = "PROJECT_INFORMATION";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private List<RemoteProjectImageEntry> images = new ArrayList<>();

	private String LATEST_VERSION = GeneralTools.getVersion();
	private String version = null;

	private String name;
	private File file;

	private String id;

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

			// todo: temp
			this.id = file.getParentFile().getName();
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

		syncChangesToServer();
	}

	private boolean askedToLogin = false;

	// TODO: WIP
	private void syncChangesToServer() throws IOException {
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

		if (RemoteOpenslide.hasRole("MANAGE_PERSONAL_PROJECTS") && !hasWriteAccess) {
			var response = Dialogs.showYesNoDialog("Save changes",
			"You've made changes to this project but you don't have the required permissions to save these changes." +
				"\n\n" +
				"Do you want to make a personal copy of this project which you can edit?");

			if (response) {
				makeCopy = true;
			} else {
				return;
			}
		} else if (!hasWriteAccess && !askedToLogin) {
			var login = Dialogs.showYesNoDialog("Save changes",
			"You've made changes to this project but you're not logged in." +
				"\n\n" +
				"Do you wish to login?");

			askedToLogin = true;

			if (login) {
				RemoteOpenslide.logout();
				EduExtension.showWorkspaceOrLoginDialog();
			}

			return;
		}

		if (makeCopy) {
			Optional<String> query = RemoteOpenslide.createPersonalProject(getName());

			try {
				String projectId = query.orElseThrow(IOException::new);

				Path dest = Path.of(System.getProperty("java.io.tmpdir"), "qupath-ext-project", projectId, File.separator);
				Path src = file.getParentFile().toPath();

				Files.createDirectory(dest.toAbsolutePath());

				FileUtil.copy(src.toFile(), dest.toFile());

				Path projectZipFile = Files.createTempFile("qupath-project-", ".zip");

				ZipUtil.zip(dest, projectZipFile);

				RemoteOpenslide.uploadProject(projectId, projectZipFile.toFile());
				Files.delete(projectZipFile);

				Platform.runLater(() -> WorkspaceManager.loadProject(projectId, "Copy of " + getName()));
			} catch (IOException e) {
				logger.error("Error while creating personal project", e);
			}
		} else if (hasWriteAccess) {
			logger.debug("Uploading project to server");

			File projectFolder = file.getParentFile();
			Path projectZipFile = Files.createTempFile("qupath-project-", ".zip");

			ZipUtil.zip(projectFolder.toPath(), projectZipFile);

			RemoteOpenslide.uploadProject(file.getParentFile().getName(), projectZipFile.toFile());
			Files.delete(projectZipFile);

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

		private transient final String IMAGE_DATA = "imageData";

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
			if (containsMetadata(IMAGE_DATA)) {
				ByteArrayInputStream is = new ByteArrayInputStream(Base64.getDecoder().decode(getMetadataValue(IMAGE_DATA)));

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

			putMetadataValue(IMAGE_DATA, Base64.getEncoder().encodeToString(os.toByteArray()));
		}

		@Override
		public PathObjectHierarchy readHierarchy() throws IOException {
			return new PathObjectHierarchy();
		}

		@Override
		public boolean hasImageData() {
			return containsMetadata(IMAGE_DATA);
		}

		@Override
		public String getSummary() {
			StringBuilder sb = new StringBuilder();

			sb.append(getImageName()).append("\n");
			sb.append("ID:\t").append(getID()).append("\n\n");

			if (!getMetadataMap().isEmpty()) {
				for (Map.Entry<String, String> mapEntry : getMetadataMap().entrySet()) {
					if (mapEntry.getKey().equals(IMAGE_DATA)) {
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

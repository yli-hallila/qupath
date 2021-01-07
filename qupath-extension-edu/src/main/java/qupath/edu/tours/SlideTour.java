package qupath.edu.tours;

import com.google.common.collect.Lists;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.lib.RemoteOpenslide;
import qupath.edu.lib.Roles;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

public class SlideTour implements QuPathViewerListener {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final QuPathGUI qupath = QuPathGUI.getInstance();
	private QuPathViewer viewer;

	private final SimpleBooleanProperty menuMinimizedProperty = new SimpleBooleanProperty(false);
	private final SimpleBooleanProperty tourActiveProperty = new SimpleBooleanProperty(false);

	private final SimpleIntegerProperty entryCountProperty = new SimpleIntegerProperty();
	private final SimpleIntegerProperty indexProperty = new SimpleIntegerProperty();
	private final SimpleStringProperty textProperty = new SimpleStringProperty();

	private final ObservableList<TourEntry> tourEntries = FXCollections.observableArrayList();
	private final Pane pane = new Pane();

	private ImageData<BufferedImage> imageData;
	private Collection<PathObject> annotations;

	public SlideTour(QuPathViewer viewer) {
		this.viewer = viewer;

		indexProperty.addListener((obs, oldValue, newValue) -> {
			int index = newValue.intValue();

			if (index == -1 || index >= tourEntries.size()) {
				textProperty.set("No tour of this slide available.");
			} else if (isVisible()) {
				TourEntry entry = tourEntries.get(index);

				if (entry.getText() == null) {
					textProperty.set("Description not set");
				} else {
					textProperty.set(entry.getText());
				}

				smoothZoomAndPan(entry.getMagnification(), entry.getX(), entry.getY());

				viewer.getImageData().getHierarchy().getSelectionModel().clearSelection();
				viewer.getImageData().getHierarchy().clearAll();

				for (PathAnnotationObject annotations : entry.getAnnotations()) {
					viewer.getImageData().getHierarchy().addPathObject(annotations);
				}
			}
		});

		entryCountProperty.bind(Bindings.size(tourEntries));
		tourActiveProperty.addListener((observable, oldValue, active) -> {
			initPane();

			if (active) {
				startTour();
			} else {
				endTour();
			}
		});

		viewer.addViewerListener(this);
	}

	public void setVisible(boolean visible) {
		pane.setVisible(visible);
	}

	public boolean isVisible() {
		return pane.isVisible();
	}

	public Node getNode() {
		initPane();

		return pane;
	}

	/**
	 * Has the ImageData been modified. Slide Tours add new annotations & remove, which makes
	 * QuPath think that some modifications were made. This value is restored once the tour is over.
	 *
	 * Editing a tour entry will set this to true to ensure that changes to tours are saved.
	 */
	private boolean changed;

	private void startTour() {
		changed = imageData.isChanged();

		annotations = imageData.getHierarchy().getAnnotationObjects();
		viewer.getImageData().getHierarchy().getSelectionModel().clearSelection();
		viewer.getImageData().getHierarchy().clearAll();

		// Done this way to trigger indexProperty change.
		if (tourEntries.size() == 0) {
			this.indexProperty.set(0); this.indexProperty.set(-1);
		} else {
			this.indexProperty.set(-1); this.indexProperty.set(0);
		}

		qupath.setAnalysisPaneVisible(false);
	}

	private void endTour() {
		stopAnimation();

		viewer.getImageData().getHierarchy().getSelectionModel().clearSelection();
		viewer.getImageData().getHierarchy().clearAll();
		viewer.getImageData().getHierarchy().addPathObjects(annotations);

		imageData.setChanged(changed);
		qupath.setAnalysisPaneVisible(true);
	}

	private synchronized void initPane() {
		BorderPane borderPane = new BorderPane();
		borderPane.setPadding(new Insets(10));
		borderPane.setBackground(new Background(new BackgroundFill(Paint.valueOf("WHITE"), CornerRadii.EMPTY, Insets.EMPTY)));
		borderPane.setBorder(new Border(new BorderStroke(
				Color.LIGHTGRAY, Color.LIGHTGRAY, Color.LIGHTGRAY, Color.LIGHTGRAY,
				BorderStrokeStyle.SOLID, BorderStrokeStyle.SOLID, BorderStrokeStyle.SOLID, BorderStrokeStyle.SOLID,
				CornerRadii.EMPTY, new BorderWidths(1), Insets.EMPTY))
		);

		pane.setVisible(viewer.getImageData() != null);

		if (tourActiveProperty.get()) {
			getTourPane(borderPane);
		} else if (tourEntries.size() > 0 || RemoteOpenslide.hasRole(Roles.MANAGE_PROJECTS)) {
			getTourStartPane(borderPane);
		} else {
			pane.setVisible(false);
		}

		pane.prefWidthProperty().bind(borderPane.widthProperty());
		pane.getChildren().clear();
		pane.getChildren().add(borderPane);
	}

	private void getTourStartPane(BorderPane borderPane) {
		/* Buttons */

		Button btnStartTour = new Button("Start slide tour");
		btnStartTour.setOnAction(e -> tourActiveProperty.set(true));
		btnStartTour.visibleProperty().bind(menuMinimizedProperty.not());
		btnStartTour.managedProperty().bind(menuMinimizedProperty.not());

		Button btnMaximize = new Button("\u2bc6");
		btnMaximize.setTooltip(new Tooltip("Maximize"));
		btnMaximize.visibleProperty().bind(menuMinimizedProperty);
		btnMaximize.managedProperty().bind(menuMinimizedProperty);
		btnMaximize.setOnAction(e -> menuMinimizedProperty.set(false));

		Button btnMinimize = new Button("\u2bc5");
		btnMinimize.setTooltip(new Tooltip("Minimize"));
		btnMinimize.visibleProperty().bind(menuMinimizedProperty.not());
		btnMinimize.managedProperty().bind(menuMinimizedProperty.not());
		btnMinimize.setOnAction(e -> menuMinimizedProperty.set(true));

		GridPane buttons = new GridPane();
		buttons.add(btnMinimize, 0, 0);
		buttons.add(btnMaximize, 0, 0);
		buttons.add(btnStartTour, 1, 0);
		buttons.setHgap(5);

		/* Pane */

		borderPane.setTop(null);
		borderPane.setCenter(null);
		borderPane.setBottom(buttons);
	}

	private void getTourPane(BorderPane borderPane) {
		/* Text */

		Text text = new Text();
		text.textProperty().bind(textProperty);
		text.setWrappingWidth(300);

		/* Buttons */

		Button btnExit = new Button("Exit");
		btnExit.setOnAction(e -> tourActiveProperty.set(false));

		Button btnAdd = new Button("Add New");
		btnAdd.setOnAction(e -> addNewEntry());

		Button btnEdit = new Button("Edit Current");
		btnEdit.setOnAction(e -> editCurrentEntry());
		btnEdit.disableProperty().bind(indexProperty.isEqualTo(-1));

		Button btnDelete = new Button("Delete Current");
		btnDelete.setOnAction(e -> deleteCurrentEntry());
		btnDelete.disableProperty().bind(indexProperty.isEqualTo(-1));

		Button btnNext = new Button("Next");
		btnNext.setOnAction(e -> indexProperty.set(indexProperty.get() + 1));
		btnNext.disableProperty().bind(indexProperty.isEqualTo(entryCountProperty.subtract(1)));

		Button btnPrevious = new Button("Previous");
		btnPrevious.setOnAction(e -> indexProperty.set(indexProperty.get() - 1));
		btnPrevious.disableProperty().bind(indexProperty.lessThanOrEqualTo(0));

		GridPane buttons = PaneTools.createColumnGridControls(btnExit, btnPrevious, btnNext);
		buttons.setHgap(5);

		/* Pane */

		BorderPane.setMargin(text, new Insets(10, 0, 10, 0));
		borderPane.setPrefWidth(300);

		borderPane.setTop(buttons);
		borderPane.setCenter(text);

		if (RemoteOpenslide.hasRole(Roles.MANAGE_PROJECTS)) {
			GridPane adminButtons = PaneTools.createColumnGridControls(btnEdit, btnDelete, btnAdd);
			adminButtons.setHgap(5);

			borderPane.setBottom(adminButtons);
		}
	}

	private void deleteCurrentEntry() {
		tourEntries.remove(indexProperty.get());
		indexProperty.set(tourEntries.size() - 1);

		syncSlideTours();
	}

	private void editCurrentEntry() {
		TourEntry entry = tourEntries.get(indexProperty.get());

		var confirm = entry.getText() == null || Dialogs.showConfirmDialog("Edit entry", "Editing the entry will save your current viewer position to this entry. After that, you'll be prompted to edit the text.");

		if (!confirm) {
			return;
		}

		String text = Dialogs.showInputDialog("Text", "", entry.getText());

		// todo: experiment with textProperty.bind(Bindings.stringValueAt(tourEntries, indexProperty));

		entry.setLocation(viewer.getCenterPixelX(), viewer.getCenterPixelY(), viewer.getMagnification());
		entry.setText(text);
		entry.setAnnotations((Collection<PathAnnotationObject>)(Collection<?>) viewer.getImageData().getHierarchy().getAnnotationObjects());

		syncSlideTours();
	}

	private void addNewEntry() {
		double x = imageData.getServer().getHeight() / 2.0;
		double y = imageData.getServer().getWidth()  / 2.0;
		double magnification = 1;

		tourEntries.add(new TourEntry("Description not set", x, y, magnification, null));

		viewer.setMagnification(1);
		viewer.setCenterPixelLocation(x, y);

		indexProperty.set(tourEntries.size() - 1);

		syncSlideTours();
	}

	/**
	 * Saves the entries in the current images metadata.
	 */
	private void syncSlideTours() {
		try {
			changed = true;
			this.imageData.setProperty("Tour", Lists.newArrayList(this.tourEntries));
		} catch (Exception e) {
			logger.error("Error when serializing slide tour data", e);
		}
	}

	private Timeline timeline;

	private void smoothZoomAndPan(double magnification, double x, double y) {
		double currentMagnification = viewer.getMagnification();
		double currentX = viewer.getCenterPixelX();
		double currentY = viewer.getCenterPixelY();

		double diffMagnification = magnification - currentMagnification;
		double diffX = x - currentX;
		double diffY = y - currentY;
		int diff = (int) Math.hypot(diffX, diffY);

		// 2500 pixels is travelled in 1000 ms, up to maximum of 5000 ms
		int maxSteps = Math.min(5, Math.max(1, (diff / 2500))) * 20;
		AtomicInteger steps = new AtomicInteger(1);

		stopAnimation();

		// TODO: High CPU usage occasionally
		timeline = new Timeline(
				new KeyFrame(
						Duration.millis(50),
						event -> {
							double multiplier = Math.min(1, 1.0 * steps.get() / maxSteps);
							viewer.setMagnification(currentMagnification + diffMagnification * multiplier);
							viewer.setCenterPixelLocation(currentX + diffX * multiplier, currentY + diffY * multiplier);

							steps.getAndIncrement();
						}
				)
		);

		timeline.setCycleCount(maxSteps);
		timeline.play();
	}

	/**
	 * Stops any active panning & zooming animation.
	 */
	public void stopAnimation() {
		if (timeline != null && timeline.getStatus() == Animation.Status.RUNNING) {
			timeline.stop();
		}
	}

	@Override
	public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
		this.viewer = viewer;
		this.imageData = imageDataNew;

		tourActiveProperty.set(false);
		tourEntries.clear();

		if (imageData != null && imageData.getProperty("Tour") != null) {
			try {
				tourEntries.addAll((ArrayList<TourEntry>) imageData.getProperty("Tour"));
			} catch (Exception e) {
				logger.error("Error when deserializing slide tour", e);
			}
		}

		initPane();
	}

	@Override
	public void viewerClosed(QuPathViewer viewer) {
		this.viewer = null;
		setVisible(false);
	}

	@Override
	public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {

	}

	@Override
	public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {

	}

	private static class TourEntry implements Serializable {

		private static final long serialVersionUID = 1L;

		private String text;
		private double x;
		private double y;
		private double magnification;

		private Collection<PathAnnotationObject> annotations = new ArrayList<>();

		public TourEntry(String text, double x, double y, double magnification, Collection<PathAnnotationObject> annotations) {
			this.text = text;
			this.x = x;
			this.y = y;
			this.magnification = magnification;

			if (annotations != null) {
				this.annotations.addAll(annotations);
			}
		}

		public void setText(String text) {
			this.text = text;
		}

		public void setLocation(double x, double y, double magnification) {
			this.x = x;
			this.y = y;
			this.magnification = magnification;
		}

		public void addAnnotation(PathAnnotationObject annotation) {
			this.annotations.add(annotation);
		}

		public void setAnnotations(Collection<PathAnnotationObject> annotations) {
			assert annotations != null;

			this.annotations = annotations;
		}

		public String getText() {
			return text;
		}

		public double getX() {
			return x;
		}

		public double getY() {
			return y;
		}

		public double getMagnification() {
			return magnification;
		}

		public Collection<PathAnnotationObject> getAnnotations() {
			return annotations;
		}

		private void writeObject(ObjectOutputStream out) throws IOException {
			out.writeUTF(text);
			out.writeDouble(x);
			out.writeDouble(y);
			out.writeDouble(magnification);
			out.writeObject(annotations);
		}

		private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
			this.text = in.readUTF();
			this.x = in.readDouble();
			this.y = in.readDouble();
			this.magnification = in.readDouble();
			this.annotations = (ArrayList<PathAnnotationObject>) in.readObject();
		}
	}
}

/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.gui.panes;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.*;
import org.controlsfx.control.MasterDetailPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener.Change;
import javafx.geometry.Side;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import qupath.lib.gui.Browser;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.DefaultPathObjectComparator;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionListener;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.roi.interfaces.ROI;


/**
 * Component for displaying annotations within the active image.
 * <p>
 * Also shows the {@link PathClass} list.
 * 
 * @author Pete Bankhead
 *
 */
public class AnnotationPane implements PathObjectSelectionListener, ChangeListener<ImageData<BufferedImage>>, PathObjectHierarchyListener {

	private final static Logger logger = LoggerFactory.getLogger(AnnotationPane.class);

	private QuPathGUI qupath;
	private ImageData<BufferedImage> imageData;
	private PathObjectHierarchy hierarchy;
	private BooleanProperty hasImageData = new SimpleBooleanProperty(false);
	
	private BorderPane pane = new BorderPane();

	/*
	 * Request that we only synchronize to the primary selection; otherwise synchronizing to 
	 * multiple selections from long lists can be a performance bottleneck
	 */
	private static boolean synchronizePrimarySelectionOnly = true;
	
	private PathClassPane pathClassPane;
	
	/*
	 * List displaying annotations in the current hierarchy
	 */
	private ListView<PathObject> listAnnotations;
		
	/*
	 * Selection being changed by outside forces, i.e. don't fire an event
	 */
	private boolean suppressSelectionChanges = false;

	private Browser browser = new Browser();

	private StringProperty descriptionProperty = new SimpleStringProperty();
	private StringProperty answerProperty = new SimpleStringProperty();
	
	
	/**
	 * Constructor.
	 * @param qupath current QuPath instance.
	 */
	public AnnotationPane(final QuPathGUI qupath) {
		this.qupath = qupath;
		
		pathClassPane = new PathClassPane(qupath);
		setImageData(qupath.getImageData());
		
		Pane paneAnnotations = createAnnotationsPane();

		TextArea textDetail = new TextArea();
		textDetail.setWrapText(true);
		textDetail.textProperty().bind(descriptionProperty);

		MasterDetailPane mdPane = new MasterDetailPane();
		mdPane.setMasterNode(paneAnnotations);
		mdPane.setDetailNode(textDetail);
		mdPane.setDetailSide(Side.BOTTOM);
		mdPane.setShowDetailNode(true);
		mdPane.setDividerPosition(0.9);
		mdPane.showDetailNodeProperty().bind(descriptionProperty.isNotEmpty());

		pane.setCenter(mdPane);

		qupath.imageDataProperty().addListener(this);
	}
	
	
	private Pane createAnnotationsPane() {
		listAnnotations = new ListView<>();
		hierarchyChanged(null); // Force update

		listAnnotations.setCellFactory(v -> new PathObjectListCell());

		listAnnotations.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		listAnnotations.getSelectionModel().getSelectedItems().addListener(
				(Change<? extends PathObject> c) -> synchronizeHierarchySelectionToListSelection()
		);
		listAnnotations.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> synchronizeHierarchySelectionToListSelection());

		listAnnotations.setOnMouseClicked(e -> {
			if (e.getClickCount() > 1) {
				PathObject pathObject = listAnnotations.getSelectionModel().getSelectedItem();
				if (pathObject == null || !pathObject.hasROI())
					return;
				QuPathViewer viewer = qupath.getViewer();
				ROI roi = pathObject.getROI();

				viewer.zoomROI(roi);
				viewer.centerROI(roi);
			}
		});
		
		PathPrefs.colorDefaultObjectsProperty().addListener((v, o, n) -> listAnnotations.refresh());

		ContextMenu menuAnnotations = GuiTools.populateAnnotationsMenu(qupath, new ContextMenu());
		listAnnotations.setContextMenu(menuAnnotations);

		// Add the main annotation list
		BorderPane panelObjects = new BorderPane();

		// Add show answer button
		Button btnShowAnswer = new Button("Show Answer");
		btnShowAnswer.setOnAction(e -> {
			PathObjectHierarchy objectHierarchy = QuPathGUI.getInstance().getImageData().getHierarchy();
			if (objectHierarchy == null)
				return;

			PathObject pathObject = objectHierarchy.getSelectionModel().getSelectedObject();

			if (pathObject.getAnswer() != null) {
				if (isJSON(pathObject.getAnswer())) {
					showQuizDialog(pathObject);
				} else{
					showAnswerDialog(pathObject);
				}
			}
		});

		btnShowAnswer.disableProperty().bind(answerProperty.isNull());
		btnShowAnswer.prefWidthProperty().bind(panelObjects.widthProperty());

		browser.setTextHighlightable(false);
		browser.maxHeightProperty().bind(pane.heightProperty().divide(3).multiply(2));
		ProjectBrowser.descriptionTextProperty().addListener((observable, oldValue, newValue) -> {
			browser.setContent(newValue);
		});


		/*GridPane panelButtons = new GridPane();
		panelButtons.add(btnSelectAll, 0, 0);
		panelButtons.add(btnDelete, 1, 0);
		panelButtons.add(btnMore, 2, 0);
		GridPane.setHgrow(btnSelectAll, Priority.ALWAYS);
		GridPane.setHgrow(btnDelete, Priority.ALWAYS);

		PaneTools.setMaxWidth(Double.MAX_VALUE, btnSelectAll, btnDelete);
		
		BooleanBinding disableButtons = hasImageData.not();
		btnSelectAll.disableProperty().bind(disableButtons);
		btnDelete.disableProperty().bind(disableButtons);
		btnMore.disableProperty().bind(disableButtons);*/

		panelObjects.setTop(browser);
		panelObjects.setCenter(listAnnotations);
		panelObjects.setBottom(btnShowAnswer);
		return panelObjects;
	}
	
	
	/**
	 * Update the selected objects in the hierarchy to match those in the list, 
	 * unless selection changes should be suppressed.
	 */
	void synchronizeHierarchySelectionToListSelection() {
		if (hierarchy == null || suppressSelectionChanges)
			return;
		suppressSelectionChanges = true;
		Set<PathObject> selectedSet = new HashSet<>(listAnnotations.getSelectionModel().getSelectedItems());
		PathObject selectedObject = listAnnotations.getSelectionModel().getSelectedItem();
		if (!selectedSet.contains(selectedObject))
			selectedObject = null;
		hierarchy.getSelectionModel().setSelectedObjects(selectedSet, selectedObject);
		suppressSelectionChanges = false;
	}

	/**
	 * Get the pane for display.
	 * @return
	 */
	public Pane getPane() {
		return pane;
	}


	void setImageData(ImageData<BufferedImage> imageData) {
		if (this.imageData == imageData)
			return;

		// Deal with listeners for the current ImageData
		if (this.hierarchy != null) {
			hierarchy.removePathObjectListener(this);
			hierarchy.getSelectionModel().removePathObjectSelectionListener(this);
		}
		this.imageData = imageData;
		if (this.imageData != null) {
			hierarchy = imageData.getHierarchy();
			hierarchy.getSelectionModel().addPathObjectSelectionListener(this);
			hierarchy.addPathObjectListener(this);
			PathObject selected = hierarchy.getSelectionModel().getSelectedObject();
			listAnnotations.getItems().setAll(hierarchy.getAnnotationObjects());
			hierarchy.getSelectionModel().setSelectedObject(selected);
		} else {
			listAnnotations.getItems().clear();
		}
		hasImageData.set(this.imageData != null);
		pathClassPane.refresh();
	}

	
	
	@Override
	public void selectedPathObjectChanged(final PathObject pathObjectSelected, final PathObject previousObject, Collection<PathObject> allSelected) {
		if (!Platform.isFxApplicationThread()) {
			// Do not synchronize to changes on other threads (since these may interfere with scripts)
//			Platform.runLater(() -> selectedPathObjectChanged(pathObjectSelected, previousObject, allSelected));
			return;
		}

		if (pathObjectSelected instanceof PathAnnotationObject) {
			PathAnnotationObject annotation = (PathAnnotationObject) pathObjectSelected;

			if (annotation.getDescription() != null && annotation.getAnswer() == null) {
				descriptionProperty.set(annotation.getDescription());
				answerProperty.set(null);
			} else {
				descriptionProperty.set(null);
				answerProperty.set(annotation.getAnswer());
			}
		}

		if (suppressSelectionChanges)
			return;
		
		suppressSelectionChanges = true;
		if (synchronizePrimarySelectionOnly) {
			try {
				var listSelectionModel = listAnnotations.getSelectionModel();
				listSelectionModel.clearSelection();
				if (pathObjectSelected != null && pathObjectSelected.isAnnotation()) {
					listSelectionModel.select(pathObjectSelected);
					listAnnotations.scrollTo(pathObjectSelected);
				}
				return;
			} finally {
				suppressSelectionChanges = false;
			}
		}

		try {
			
			var hierarchySelected = new TreeSet<>(DefaultPathObjectComparator.getInstance());
			hierarchySelected.addAll(allSelected);
			
			// Determine the objects to select
			MultipleSelectionModel<PathObject> model = listAnnotations.getSelectionModel();
			List<PathObject> selected = new ArrayList<>();
			for (PathObject pathObject : hierarchySelected) {
				if (pathObject == null)
					logger.warn("Selected object is null!");
				else if (pathObject.isAnnotation())
					selected.add(pathObject);
			}
			if (selected.isEmpty()) {
				if (!model.isEmpty())
					model.clearSelection();
				return;
			}
			// Check if we're making changes
			List<PathObject> currentlySelected = model.getSelectedItems();
			if (selected.size() == currentlySelected.size() && (hierarchySelected.containsAll(currentlySelected))) {
				listAnnotations.refresh();
				return;
			}
			
//			System.err.println("Starting...");
//			System.err.println(hierarchy.getAnnotationObjects().size());
//			System.err.println(hierarchySelected.size());
//			System.err.println(listAnnotations.getItems().size());
			if (hierarchySelected.containsAll(listAnnotations.getItems())) {
				model.selectAll();
				return;
			}
			
	//		System.err.println("Setting " + currentlySelected + " to " + selected);
			int[] inds = new int[selected.size()];
			int i = 0;
			model.clearSelection();
			boolean firstInd = true;
			for (PathObject temp : selected) {
				int idx = listAnnotations.getItems().indexOf(temp);
				if (idx >= 0 && firstInd) {
					Arrays.fill(inds, idx);
					firstInd = false;
				}
				inds[i] = idx;
				i++;
			}
			
			if (inds.length == 1 && pathObjectSelected instanceof PathAnnotationObject)
				listAnnotations.scrollTo(pathObjectSelected);
			
			if (firstInd) {
				suppressSelectionChanges = false;
				return;
			}
			if (inds.length == 1)
				model.select(inds[0]);
			else if (inds.length > 1)
				model.selectIndices(inds[0], inds);
		} finally {
			suppressSelectionChanges = false;			
		}
	}


	/**
	 *   Our quiz JSON strings starts always with [{
	 */
	public static boolean isJSON(String string) {
		return string.startsWith("[{");
	}

	private void showQuizDialog(PathObject pathObject) {
		try {
			List<Option> questions = new ArrayList<>();
			List<String> rightAnswers = new ArrayList<>();

			JsonArray arr = new Gson().fromJson(pathObject.getAnswer(), JsonArray.class);
			for (JsonElement element : arr) {
				JsonObject obj = (JsonObject) element;

				Option option = new Option(
					obj.get("question").getAsString(),
					obj.get("answer").getAsBoolean()
				);

				questions.add(option);

				if (option.isAnswer()) {
					rightAnswers.add(option.getQuestion());
				}
			}

			Option result = (Option) Dialogs.showChoiceDialog("Select correct choice", pathObject.getName(), questions.toArray(), questions.get(0));

			if (result != null) {
				String message = result.isAnswer() ? "Right answer!" : "Wrong answer!";

				if (rightAnswers.size() > 1) {
					message += "\n\n";
					message += "All the right answers are: " + rightAnswers.toString().replaceAll("\\[|\\]", "");
				}

				String description = ((PathAnnotationObject) pathObject).getDescription();
				if (description != null) {
					message += "\n\n";
					message += description;
				}

				Dialogs.showPlainMessage("Answer", message);
			}
		} catch (JsonSyntaxException ex) {
			logger.error("Error while parsing answer JSON", ex);
			showAnswerDialog(pathObject);
		}
	}

	private void showAnswerDialog(PathObject pathObject) {
		Dialogs.showMessageDialog(pathObject.getName(), pathObject.getAnswer());
	}

	public static class Option {

		private final SimpleStringProperty question;
		private final SimpleBooleanProperty answer;

		public Option() {
			this("", false);
		}

		public Option(String question) {
			this(question, false);
		}

		public Option(String question, boolean answer) {
			this.question = new SimpleStringProperty(question);
			this.answer = new SimpleBooleanProperty(answer);
		}

		public String getQuestion() {
			return question.get();
		}

		public SimpleStringProperty questionProperty() {
			return question;
		}

		public void setQuestion(String question) {
			this.question.set(question);
		}

		public boolean isAnswer() {
			return answer.get();
		}

		public SimpleBooleanProperty answerProperty() {
			return answer;
		}

		public void setAnswer(boolean answer) {
			this.answer.set(answer);
		}

		@Override
		public String toString() {
			return question.get();
		}
	}

	
	@Override
	public void changed(ObservableValue<? extends ImageData<BufferedImage>> source, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
		setImageData(imageDataNew);
	}



	@Override
	public void hierarchyChanged(PathObjectHierarchyEvent event) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> hierarchyChanged(event));
			return;
		}

		if (hierarchy == null) {
			listAnnotations.getItems().clear();
			return;
		}

		Collection<PathObject> newList = hierarchy.getObjects(new HashSet<>(), PathAnnotationObject.class);
		pathClassPane.getListView().refresh();
		// If the lists are the same, we just need to refresh the appearance (because e.g. classifications or measurements now differ)
		// For some reason, 'equals' alone wasn't behaving nicely (perhaps due to ordering?)... so try a more manual test instead
//		if (newList.equals(listAnnotations.getItems())) {
		if (newList.size() == listAnnotations.getItems().size() && newList.containsAll(listAnnotations.getItems())) {
			// Don't refresh unless there is good reason to believe the list should appear different now
			// This was introduced due to flickering as annotations were dragged
			// TODO: Reconsider when annotation list is refreshed
			
//			listAnnotations.setStyle(".list-cell:empty {-fx-background-color: white;}");
			
//			if (event.getEventType() == HierarchyEventType.CHANGE_CLASSIFICATION || event.getEventType() == HierarchyEventType.CHANGE_MEASUREMENTS || (event.getStructureChangeBase() != null && event.getStructureChangeBase().isPoint()) || PathObjectTools.containsPointObject(event.getChangedObjects()))
			if (!event.isChanging())
				listAnnotations.refresh();
			return;
		}
		// If the lists are different, we need to update accordingly - but we don't want to trigger accidental selection updates
//		listAnnotations.getSelectionModel().clearSelection(); // Clearing the selection would cause annotations to disappear when interactively training a classifier!
		boolean lastChanging = suppressSelectionChanges;
		suppressSelectionChanges = true;
		listAnnotations.getItems().setAll(newList);
		suppressSelectionChanges = lastChanging;
	}
	
}
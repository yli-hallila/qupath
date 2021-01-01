package qupath.edu.gui;

import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import org.controlsfx.control.GridCell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.lib.RemoteOpenslide;
import qupath.edu.models.ExternalProject;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static qupath.edu.lib.RemoteOpenslide.Result;

public class WorkspaceProjectListCell extends GridCell<ExternalProject> {

    private final QuPathGUI qupath = QuPathGUI.getInstance();
    private final WorkspaceManager manager;
    private ExternalProject project;

    private boolean hasWriteAccess = false;

    private GridCell<ExternalProject> thisCell;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public WorkspaceProjectListCell(WorkspaceManager manager) {
        this.manager = manager;
        thisCell = this;

        setPrefWidth(0);
    }

    private void enableReordering() {
        setOnDragDetected(event -> {
            if (getItem() == null) {
                return;
            }

            Dragboard dragboard = startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(getItem().getId());

            dragboard.setDragView(getGraphic().snapshot(null, null));
            dragboard.setContent(content);

            event.consume();
        });

        setOnDragOver(event -> {
            if (event.getGestureSource() != thisCell && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }

            event.consume();
        });

        setOnDragEntered(event -> {
            if (event.getGestureSource() != thisCell && event.getDragboard().hasString()) {
                setOpacity(0.3);
            }
        });

        setOnDragExited(event -> {
            if (event.getGestureSource() != thisCell && event.getDragboard().hasString()) {
                setOpacity(1);
            }
        });

        setOnDragDropped(event -> {
            if (getItem() == null) {
                return;
            }

            Dragboard db = event.getDragboard();
            boolean success = false;

            if (db.hasString() && getProjectFromListView(db.getString()).isPresent()) {
                ObservableList<ExternalProject> items = getGridView().getItems();
                ExternalProject clipboardProject = getProjectFromListView(db.getString()).get();

                int draggedIdx = items.indexOf(clipboardProject);
                int thisIdx = items.indexOf(getItem());

                items.set(draggedIdx, getItem());
                items.set(thisIdx, clipboardProject);

                List<ExternalProject> itemsTemp = new ArrayList<>(getGridView().getItems());
                getGridView().getItems().setAll(itemsTemp);

                success = true;
            }

            event.setDropCompleted(success);
            event.consume();
        });

        setOnDragDone(DragEvent::consume);
    }

    private Optional<ExternalProject> getProjectFromListView(String id) {
        return getGridView().getItems().stream().filter(
            p -> p.getId().equalsIgnoreCase(id)
        ).findFirst();
    }

    @Override
    protected void updateItem(ExternalProject project, boolean empty) {
        super.updateItem(project, empty);
        this.project = project;

        if (empty || project.getName().isEmpty()) {
            setText(null);
            setGraphic(null);
            return;
        }

        this.hasWriteAccess = RemoteOpenslide.isOwner(project.getOwner());

        if (!hasWriteAccess && project.isHidden()) {
            setText(null);
            setGraphic(null);
            return;
        }

//        if (hasWriteAccess) {
//            enableReordering();
//        }

        GridPane pane = new GridPane();
        pane.setPadding(new Insets(5));
        pane.setHgap(5);
        pane.setPrefWidth(getGridView().getPrefWidth());

        if (project.isHidden()) {
            pane.setBorder(new Border(new BorderStroke(
                Color.DARKGRAY,           Color.DARKGRAY,           Color.DARKGRAY,           Color.DARKGRAY,
                BorderStrokeStyle.DASHED, BorderStrokeStyle.DASHED, BorderStrokeStyle.DASHED, BorderStrokeStyle.DASHED,
                CornerRadii.EMPTY, new BorderWidths(1), Insets.EMPTY
            )));

            pane.setBackground(new Background(new BackgroundFill(Color.LIGHTGRAY, null, null)));
        } else {
            pane.setBorder(new Border(new BorderStroke(
                null, null, Color.DARKGRAY,          null,
                null, null, BorderStrokeStyle.SOLID, null,
                CornerRadii.EMPTY, new BorderWidths(1), Insets.EMPTY
            )));
        }

        /* Constraints */
        ColumnConstraints logoColumnConstraint = new ColumnConstraints(48);
        ColumnConstraints textColumnConstraint = new ColumnConstraints();
        textColumnConstraint.setHgrow(Priority.ALWAYS);

        pane.getColumnConstraints().addAll(logoColumnConstraint, textColumnConstraint);

        RowConstraints headerRowConstraints = new RowConstraints(12, 24, 24);
        headerRowConstraints.setValignment(VPos.BOTTOM);

        RowConstraints descriptionRowConstraints = new RowConstraints(24, 24, 36);
        descriptionRowConstraints.setValignment(VPos.TOP);
        descriptionRowConstraints.setVgrow(Priority.ALWAYS);

        pane.getRowConstraints().addAll(headerRowConstraints, descriptionRowConstraints);

        /* Content */
        Text name = new Text(project.getName());
        name.setFont(Font.font("Calibri", FontWeight.BOLD, FontPosture.REGULAR, 15));

        Label description = new Label(project.getDescription());
        description.setWrapText(true);

//        ImageView icon;
//        try {
//            String base64decoded = "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";
//
//            byte[] img = Base64.getDecoder().decode(base64decoded.getBytes());
//            icon = new ImageView(new Image(new ByteArrayInputStream(img)));
//            icon.setFitWidth(48);
//            icon.setFitHeight(48);
//        } catch (IllegalArgumentException e) {
//            logger.error("Couldn't create thumbnail for {}", project.getId());
//            icon = new ImageView(QuPathGUI.getInstance().loadIcon(48));
//        }

        ImageView icon = new ImageView(QuPathGUI.getInstance().loadIcon(48));

        /* Construct GridPane */
        pane.add(icon, 0, 0);
        pane.add(name, 1, 0);
        pane.add(description, 1, 1);

        GridPane.setRowSpan(icon, 2);

        pane.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                WorkspaceManager.loadProject(project, manager);
            } else if (event.getButton() == MouseButton.SECONDARY) {
                ContextMenu menu = new ContextMenu();

                if (hasWriteAccess) {
                    MenuItem miRename = new MenuItem("Rename");
                    miRename.setOnAction(action -> renameProject());

                    MenuItem miEditDescription = new MenuItem("Edit description");
                    miEditDescription.setOnAction(action -> editDescription());

                    MenuItem miDelete = new MenuItem("Delete");
                    miDelete.setOnAction(action -> deleteProject());

                    MenuItem miToggleVisibility = new MenuItem(project.isHidden() ? "Reveal project" : "Hide project");
                    miToggleVisibility.setOnAction(action -> toggleVisibility());

                    menu.getItems().addAll(miRename, miEditDescription, miDelete, miToggleVisibility, new SeparatorMenuItem());
                }

                MenuItem miShare = new MenuItem("Share");
                miShare.setOnAction(action -> Dialogs.showInputDialog(
                    "Project ID",
                    "You can enter this ID to: Remote Openslide > Open project by ID",
                    project.getId()
                ));

                menu.getItems().add(miShare);
                menu.show(pane, event.getScreenX(), event.getScreenY());
            }
        });

        setGraphic(pane);
        setPadding(new Insets(0));
    }

    private void editDescription() {
        String newDescription = Dialogs.showInputDialog(
        "New description", "", project.getDescription()
        );

        if (newDescription == null) {
            return;
        }

        Result result = RemoteOpenslide.editProject(project.getId(), project.getName(), newDescription);
        if (result == Result.OK) {
            manager.refreshDialog();
        } else {
            Dialogs.showErrorNotification(
            "Error when editing project description",
            "See log for more details"
            );
        }
    }

    private void renameProject() {
        String newName = Dialogs.showInputDialog(
        "New name", "", project.getName()
        );

        if (newName == null) {
            return;
        }

        Result result = RemoteOpenslide.editProject(project.getId(), newName, project.getDescription());
        TitledPane expanded = manager.getAccordion().getExpandedPane();

        if (result == Result.OK) {
            manager.refreshDialog();
            manager.getAccordion().setExpandedPane(expanded);
        } else {
            Dialogs.showErrorNotification(
            "Error when editing project name",
            "See log for more details"
            );
        }
    }

    private void deleteProject() {
        boolean confirm = Dialogs.showConfirmDialog(
        "Are you sure?",
        "Do you wish to delete this project? This action is irreversible."
        );

        if (confirm) {
            Result result = RemoteOpenslide.deleteProject(project.getId());
            TitledPane expanded = manager.getAccordion().getExpandedPane();

            if (result == Result.OK) {
                manager.refreshDialog();
                manager.getAccordion().setExpandedPane(expanded);
            } else {
                Dialogs.showErrorNotification(
                "Error when deleting project",
                "See log for more details"
                );
            }
        }
    }

    private void toggleVisibility() {
        boolean confirm = Dialogs.showConfirmDialog(
            "Are you sure?",
            "Do you wish to set this project " + (project.isHidden() ? "visible" : "hidden")
        );

        if (confirm) {
            Result result = RemoteOpenslide.setProjectHidden(project.getId(), !project.isHidden());
            TitledPane expanded = manager.getAccordion().getExpandedPane();

            if (result == Result.OK) {
                manager.refreshDialog();
                manager.getAccordion().setExpandedPane(expanded);
            } else {
                Dialogs.showErrorNotification(
                    "Error while setting project visibility",
                    "See log for more details"
                );
            }
        }
    }
}

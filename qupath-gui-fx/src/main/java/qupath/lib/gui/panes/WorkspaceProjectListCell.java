package qupath.lib.gui.panes;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.RemoteOpenslide;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.WorkspaceManager;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.objects.remoteopenslide.ExternalProject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static qupath.lib.common.RemoteOpenslide.Result;

public class WorkspaceProjectListCell extends ListCell<ExternalProject> {

    private final QuPathGUI qupath = QuPathGUI.getInstance();
    private final WorkspaceManager manager;
    private ExternalProject project;

    private boolean hasWriteAccess = false;

    private ListCell<ExternalProject> thisCell;

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
                ObservableList<ExternalProject> items = getListView().getItems();
                ExternalProject clipboardProject = getProjectFromListView(db.getString()).get();

                int draggedIdx = items.indexOf(clipboardProject);
                int thisIdx = items.indexOf(getItem());

                items.set(draggedIdx, getItem());
                items.set(thisIdx, clipboardProject);

                List<ExternalProject> itemsTemp = new ArrayList<>(getListView().getItems());
                getListView().getItems().setAll(itemsTemp);

                success = true;
            }

            event.setDropCompleted(success);
            event.consume();
        });

        setOnDragDone(DragEvent::consume);
    }

    private Optional<ExternalProject> getProjectFromListView(String id) {
        AtomicReference<ExternalProject> toReturn = new AtomicReference<>(null);

        getListView().getItems().forEach(project -> {
            if (project.getId().equals(id)) {
                toReturn.set(project);
            }
        });

        return Optional.ofNullable(toReturn.get());
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

        if (hasWriteAccess) {
            enableReordering();
        }

        GridPane pane = new GridPane();
        pane.setPadding(new Insets(5));
        pane.setHgap(5);
        pane.setPrefWidth(getListView().getPrefWidth());
        pane.setBorder(new Border(
            new BorderStroke(null, null, Color.LIGHTGRAY, null,
            null, null, BorderStrokeStyle.SOLID, null,
            CornerRadii.EMPTY, new BorderWidths(1), Insets.EMPTY))
        );

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

                    MenuItem miDelete = new MenuItem("Delete");
                    miDelete.setOnAction(action -> deleteProject());

                    MenuItem miEdit = new MenuItem("Edit description");
                    miEdit.setOnAction(action -> editDescription());

                    menu.getItems().addAll(miRename, miDelete, miEdit);
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
        int currentIndex = manager.getTabPane().getSelectionModel().getSelectedIndex();

        if (result == Result.OK) {
            manager.refreshDialog();
            manager.getTabPane().getSelectionModel().select(currentIndex);
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
            int currentIndex = manager.getTabPane().getSelectionModel().getSelectedIndex();

            if (result == Result.OK) {
                manager.refreshDialog();
                manager.getTabPane().getSelectionModel().select(currentIndex);
            } else {
                Dialogs.showErrorNotification(
                "Error when deleting project",
                "See log for more details"
                );
            }
        }
    }

}

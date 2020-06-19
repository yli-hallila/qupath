package qupath.lib.gui.panes;

import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.RemoteOpenslide;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.WorkspaceManager;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.io.ZipUtil;
import qupath.lib.objects.remoteopenslide.ExternalProject;
import qupath.lib.projects.ProjectIO;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static qupath.lib.common.RemoteOpenslide.*;

public class ProjectListCell extends ListCell<ExternalProject> {

    private final QuPathGUI qupath = QuPathGUI.getInstance();
    private final WorkspaceManager manager;
    private ExternalProject project;

    private ListCell<ExternalProject> thisCell;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public ProjectListCell(WorkspaceManager manager) {
        this.manager = manager;
        thisCell = this;

        if (RemoteOpenslide.hasWriteAccess()) {
            enableReordering();
        }
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
                loadProject();
            } else if (event.getButton() == MouseButton.SECONDARY) {
                ContextMenu menu = new ContextMenu();
                MenuItem rename = new MenuItem("Rename");
                rename.setOnAction(action -> renameProject());

                MenuItem delete = new MenuItem("Delete");
                delete.setOnAction(action -> deleteProject());

                MenuItem editDescription = new MenuItem("Edit description");
                editDescription.setOnAction(action -> editProjectDescription());

                menu.getItems().addAll(rename, delete, editDescription);
                menu.show(pane, event.getScreenX(), event.getScreenY());
            }
        });

        setGraphic(pane);
        setPadding(new Insets(0));
    }

    private void loadProject() {
        try {
            Path tempPath = Path.of(System.getProperty("java.io.tmpdir"), "qupath-ext-project");
            String tempPathStr = tempPath.toAbsolutePath().toString();
            Files.createDirectories(tempPath);

            Task<Boolean> worker = new Task<>() {
                @Override
                protected Boolean call() throws Exception {
                    Optional<InputStream> projectInputStream = RemoteOpenslide.downloadProject(ProjectListCell.this.project.getId());

                    if (projectInputStream.isEmpty()) {
                        updateMessage("Error when downloading project, see log.");
                        return false;
                    }

                    updateMessage("Downloading project");
                    ZipUtil.unzip(projectInputStream.get(), tempPathStr);
                    updateMessage("Downloaded. Opening project...");

                    projectInputStream.get().close();

                    return true;
                }
            };

            ProgressDialog progress = new ProgressDialog(worker);
            progress.setTitle("Project import");
            qupath.submitShortTask(worker);
            progress.showAndWait();

            var success = worker.getValue();

            if (success) {
                File projectFile = new File(tempPathStr + "/" + project.getId() + "/project.qpproj");
                qupath.lib.projects.Project<BufferedImage> project = ProjectIO.loadProject(projectFile, BufferedImage.class);
                project.setName(this.project.getName());

                manager.closeDialog();
                qupath.getTabbedPanel().getSelectionModel().select(0);
                qupath.setProject(project);
            }
        } catch (IOException e) {
            Dialogs.showErrorMessage(
            "Error when trying to load project. ",
            "See log for more information."
            );

            logger.error("Error when loading external project", e);
        }
    }

    private void editProjectDescription() {
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
        if (result == Result.OK) {
            manager.refreshDialog();
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
        "Do you wish to delete this project? This action is un-reversible."
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

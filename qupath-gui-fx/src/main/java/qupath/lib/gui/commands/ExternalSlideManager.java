package qupath.lib.gui.commands;

import com.google.common.collect.MoreCollectors;
import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;
import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.RemoteOpenslide;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.objects.remoteopenslide.ExternalSlide;

import java.awt.*;
import java.io.*;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static qupath.lib.common.RemoteOpenslide.*;

public class ExternalSlideManager {

    private final QuPathGUI qupath = QuPathGUI.getInstance();

    private SimpleBooleanProperty writeAccessProperty;
    private final static Logger logger = LoggerFactory.getLogger(ExternalSlideManager.class);

    private static Dialog dialog;

    private BorderPane pane;
    private TableView<ExternalSlide> table;

    public static void showExternalSlideManager() {
        ExternalSlideManager manager = new ExternalSlideManager();

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        dialog = Dialogs.builder()
                .title("External Slide Manager")
                .content(manager.getPane())
                .buttons(ButtonType.CLOSE)
                .width(Math.min(800, screenSize.getWidth() / 2))
                .resizable()
                .build();

        dialog.showAndWait();
    }

    public ExternalSlideManager() {
        this.writeAccessProperty = new SimpleBooleanProperty(RemoteOpenslide.hasRole("MANAGE_PROJECTS"));
    }

    public BorderPane getPane() {
        if (pane == null) {
            initializePane();
        }

        return pane;
    }

    private synchronized void initializePane() {
        /* Table */

        table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Text("No slides or none match search criteria"));

        TableColumn<ExternalSlide, String> slideNameColumn = new TableColumn<>("Name");
        slideNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        slideNameColumn.setReorderable(false);

        TableColumn<ExternalSlide, String> ownerColumn = new TableColumn<>("Owner");
        ownerColumn.setCellValueFactory(new PropertyValueFactory<>("owner"));
        ownerColumn.setReorderable(false);

        TableColumn<ExternalSlide, String> uuidColumn = new TableColumn<>("UUID");
        uuidColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        uuidColumn.setReorderable(false);

        table.getColumns().addAll(slideNameColumn, ownerColumn, uuidColumn);

        ExternalSlide[] slides = new Gson().fromJson(
            RemoteOpenslide.getSlidesV1().get(),
            ExternalSlide[].class
        );

        /* Filter / Search */

        TextField filterTextField = new TextField();
        filterTextField.setPromptText("Search by slide name, organization or ID");

        FilteredList<ExternalSlide> filteredData = new FilteredList<>(FXCollections.observableArrayList(slides), data -> true);

        filterTextField.textProperty().addListener(((observable, oldValue, newValue) -> {
            filteredData.setPredicate(data -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }

                String lowerCaseSearch = newValue.toLowerCase();
                return data.toString().replaceAll("[-+.^:,']", "").toLowerCase().contains(lowerCaseSearch);
            });
        }));

        SortedList<ExternalSlide> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(table.comparatorProperty());

        table.setItems(sortedData);

        /* Buttons */

        Button btnDelete = new Button("Delete");
        btnDelete.setOnAction(e -> deleteSlide());
        btnDelete.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull().or(writeAccessProperty.not()));

        Button btnRename = new Button("Rename");
        btnRename.setOnAction(e -> renameSlide());
        btnRename.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull().or(writeAccessProperty.not()));

        Button btnOpen = new Button("Open slide");
        btnOpen.setOnAction(e -> openSlide());
        btnOpen.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        Button btnProperties = new Button("View Properties");
        btnProperties.setOnAction(e -> viewProperties());
        btnProperties.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        Button btnUpload = new Button("Upload new slide");
        btnUpload.setOnAction(e -> uploadSlide());
        btnUpload.disableProperty().bind(writeAccessProperty.not());

        GridPane paneButtons = PaneTools.createColumnGridControls(btnDelete, btnRename, btnOpen, btnProperties, btnUpload);

        /* Pane */

        BorderPane.setMargin(table, new Insets(10, 0, 10, 0));

        pane = new BorderPane();
        pane.setPrefWidth(800);
        pane.setPrefHeight(400);
        pane.setTop(filterTextField);
        pane.setCenter(table);
        pane.setBottom(paneButtons);
        pane.setPadding(new Insets(10));
    }

    public synchronized void refreshDialog() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::refreshDialog);
        }

        initializePane();
        dialog.getDialogPane().setContent(pane);
    }

    private void openSlide() {
        ExternalSlide slide = table.getSelectionModel().getSelectedItem();

        if (qupath.getProject() != null) {
            ButtonType closeProject = new ButtonType("Close project and continue", ButtonBar.ButtonData.APPLY);

            Optional<ButtonType> confirm = Dialogs.builder()
                .title("Proceed with adding slide to project")
                .contentText("Opening an slide with a project open will try to add that slide to the current project."
                    + "\n" +
                    "Close your project first if you want to only view the slide."
                    + "\n\n" +
                    "Do you wish to continue?")
                .buttons(ButtonType.OK, closeProject, ButtonType.CANCEL)
                .build()
                .showAndWait();

            if (confirm.isEmpty() || confirm.get().getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE) {
                return;
            }

            if (confirm.get().getButtonData() == ButtonBar.ButtonData.APPLY) {
                qupath.setProject(null);
            }
        }

        Dialog<ButtonType> message = Dialogs.builder()
            .title("Please wait")
            .contentText("Loading slide ...")
            .build();

        message.setResult(ButtonType.CLOSE);
        message.show();

        dialog.close();

        Platform.runLater(() -> {
            qupath.openImage(RemoteOpenslide.getHost() + "/" + slide.getId(), true, true);
            qupath.getTabbedPanel().getSelectionModel().select(1);

            message.close();
        });
    }

    private void viewProperties() {
        ExternalSlide slide = table.getSelectionModel().getSelectedItem();

        TableView<Map.Entry<String, String>> propertiesTable = new TableView<>();
        propertiesTable.setPrefWidth(800);
        propertiesTable.setPrefHeight(500);
        propertiesTable.setPadding(new Insets(0));
        propertiesTable.setEditable(true);

        TableColumn<Map.Entry<String, String>, String> keyColumn = new TableColumn<>("Key");
        keyColumn.setCellValueFactory(v -> new ReadOnlyStringWrapper(v.getValue().getKey()));
        keyColumn.setSortType(TableColumn.SortType.ASCENDING);

        TableColumn<Map.Entry<String, String>, String> valueColumn = new TableColumn<>("Value");
        valueColumn.setCellValueFactory(v -> new ReadOnlyStringWrapper(v.getValue().getValue()));
        valueColumn.setEditable(true);

        propertiesTable.getColumns().addAll(keyColumn, valueColumn);
        propertiesTable.getItems().addAll(slide.getParameters().entrySet());
        propertiesTable.getSortOrder().add(keyColumn);

        Dialogs.builder()
                .buttons(ButtonType.CLOSE)
                .content(propertiesTable)
                .resizable()
                .build()
                .showAndWait();
    }

    private static final int CHUNK_BUFFER_SIZE = 1024 * 1024;

    private void uploadSlide() {
        File file = Dialogs.promptForFile("Select slide", null, null);

        if (file == null) {
            return;
        }

        try {
            ImageServerBuilder<?> openSlideBuilder = ImageServerProvider.getInstalledImageServerBuilders().stream().filter(
                imageServerBuilder -> imageServerBuilder.getName().equals("OpenSlide Builder")
            ).collect(MoreCollectors.onlyElement());

            ImageServerBuilder.UriImageSupport<?> support = openSlideBuilder.checkImageSupport(file.toURI());

            if (support.getSupportLevel() == 0) {
                Dialogs.showWarningNotification("Error uploading slide", "Given file is not supported by OpenSlide.");
                return;
            }

            Task<Void> task = new UploadSlideTask(file);
            ProgressDialog progress = new ProgressDialog(task);
            progress.setTitle("Uploading slide");
            progress.getDialogPane().setGraphic(null);
            progress.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
            progress.getDialogPane().lookupButton(ButtonType.CANCEL).addEventFilter(ActionEvent.ACTION, e -> {
                if (Dialogs.showYesNoDialog("Cancel upload", "Are you sure you want to stop uploading this slide?")) {
                    task.cancel();
                    progress.setHeaderText("Cancelling...");
                    progress.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);
                }
                e.consume();
            });

            QuPathGUI.getInstance().submitShortTask(task);
            progress.showAndWait();

            Dialogs.showMessageDialog(
            "Successfully uploaded slide",
            "The slide was successfully uploaded but is pending processing. Processing can take up to 30 minutes." + "\n\n" +
                    "You can view your slide in a few minutes but it is missing higher magnifications until the processing is complete. "
            );

            refreshDialog();
        } catch (IOException e) {
            logger.error("Error while reading file", e);
        }
    }

    private void deleteSlide() {
        boolean confirm = Dialogs.showConfirmDialog(
        "Delete slide",
        "Are you sure you wish to delete this slide? This is irreversible."
        );

        if (!confirm) {
            return;
        }

        ExternalSlide slide = table.getSelectionModel().getSelectedItem();
        Result result = RemoteOpenslide.deleteSlide(slide.getId());

        if (result == Result.OK) {
            Dialogs.showInfoNotification("Success", "Successfully deleted slide.");
            refreshDialog();
        } else {
            Dialogs.showErrorNotification("Error", "Error while deleting slide. See log for possibly additional information.");
        }
    }

    private void renameSlide() {
        ExternalSlide slide = table.getSelectionModel().getSelectedItem();
        String name = Dialogs.showInputDialog("New slide name", "", slide.getName());

        if (name == null) {
            return;
        }

        Result result = RemoteOpenslide.editSlide(slide.getId(), name);

        if (result == Result.OK) {
            Dialogs.showInfoNotification("Success", "Successfully renamed slide.");
            refreshDialog();
        } else {
            Dialogs.showErrorNotification("Error", "Error while renaming slide. See log for possibly additional information.");
        }
    }

    private class UploadSlideTask extends Task<Void> {

        private File file;
        private String filename;
        private long fileSize;

        private int chunkIndex = 0;
        private long chunks;

        public UploadSlideTask(File file) {
            this.file = file;
            this.filename = file.getName();
            this.fileSize = file.length();
            this.chunks = Math.floorDiv(fileSize, CHUNK_BUFFER_SIZE);
        }

        @Override
        protected Void call() throws Exception {
            try (FileInputStream is = new FileInputStream(file)) {
                byte[] buffer = new byte[CHUNK_BUFFER_SIZE];
                int read;
                while ((read = is.read(buffer)) > 0) {
                    RemoteOpenslide.uploadSlideChunk(
                        filename,
                        fileSize,
                        Arrays.copyOf(buffer, read),
                        CHUNK_BUFFER_SIZE,
                        chunkIndex
                    );

                    updateMessage(String.format("Uploading chunk %s out of %s", chunkIndex, chunks));
                    updateProgress(chunkIndex, chunks);
                    chunkIndex++;
                }
            } catch (IOException e) {
                logger.error("Error while uploading slide", e);
            }

            return null;
        }
    }
}

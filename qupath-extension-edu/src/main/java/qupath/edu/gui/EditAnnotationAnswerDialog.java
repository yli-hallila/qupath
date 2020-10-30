package qupath.edu.gui;

import com.google.gson.Gson;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import qupath.edu.lib.RemoteOpenslide;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.objects.PathObject;

import java.util.Collections;
import java.util.List;

public class EditAnnotationAnswerDialog {

    public static boolean openDialog(SimpleAnnotationPane annotationPane) {
        if (!RemoteOpenslide.hasRole(fi.ylihallila.remote.commons.Roles.MANAGE_PROJECTS)) {
            Dialogs.showErrorMessage("No permission", "You don't have permissions to edit answers.");
            return true;
        }

        PathObject annotation = annotationPane.getListAnnotations().getSelectionModel().getSelectedItem();

        /* Text answer */

        TextArea textAreaAnswer = new TextArea(getAnswer(annotation));
        textAreaAnswer.setPrefRowCount(2);
        textAreaAnswer.setPrefColumnCount(25);

        /* Multi-choice answer */

        TableView<SimpleAnnotationPane.MultichoiceOption> table = createMultiChoiceTable(annotation);

        Button newEntryButton = new Button("Add new");
        newEntryButton.setOnMouseClicked(e -> addRowToTable(table));

        Button deleteEntryButton = new Button("Remove selected");
        deleteEntryButton.setOnMouseClicked(e -> removeRowFromTable(table));

        GridPane controlButtons = PaneTools.createColumnGridControls(newEntryButton, deleteEntryButton);

        /* Tabs */

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getStyleClass().add("floating");

        tabPane.getTabs().addAll(
            new Tab("Multiple choice", new VBox(table, controlButtons)),
            new Tab("Text answer", textAreaAnswer)
        );

        if (!Dialogs.showConfirmDialog("Set answer properties", tabPane))
            return false;

        /* Save changes */

        ObservableList<SimpleAnnotationPane.MultichoiceOption> quizItems = table.getItems();
        if (quizItems == null || quizItems.isEmpty()) {
            setAnswer(annotation, textAreaAnswer.getText());
        } else {
            String json = new Gson().toJson(table.getItems().toArray(new SimpleAnnotationPane.MultichoiceOption[] {} ));
            setAnswer(annotation, json);
        }

        annotationPane.getHierarchy().fireObjectsChangedEvent(null, Collections.singleton(annotation));
        annotationPane.getHierarchy().getSelectionModel().setSelectedObject(annotation);

        return true;
    }

    private static TableView<SimpleAnnotationPane.MultichoiceOption> createMultiChoiceTable(PathObject annotation) {
        /* Table */

        TableView<SimpleAnnotationPane.MultichoiceOption> table = new TableView<>();
        table.setPlaceholder(new Text("No data"));
        table.setEditable(true);

        TableColumn<SimpleAnnotationPane.MultichoiceOption, String> choicesColumn = new TableColumn<>("Choice");
        choicesColumn.setEditable(true);
        choicesColumn.prefWidthProperty().bind(table.widthProperty().multiply(0.70));
        choicesColumn.setCellValueFactory(new PropertyValueFactory<>("choice"));
        choicesColumn.setCellFactory(tc -> new FocusingTextFieldTableCell<>());

        TableColumn<SimpleAnnotationPane.MultichoiceOption, Boolean> answersColumn = new TableColumn<>("Answer(s)");
        answersColumn.setEditable(true);
        answersColumn.prefWidthProperty().bind(table.widthProperty().multiply(0.25));
        answersColumn.setCellValueFactory(new PropertyValueFactory<>("isAnswer"));
        answersColumn.setCellFactory(col -> new CheckBoxTableCell<>(index -> {
            BooleanProperty active = new SimpleBooleanProperty(table.getItems().get(index).getIsAnswer());

            active.addListener((obs, wasActive, isNowActive) -> {
                SimpleAnnotationPane.MultichoiceOption item = table.getItems().get(index);
                item.setIsAnswer(isNowActive);
            });

            return active;
        }));

        choicesColumn.setOnEditCommit(event -> {
            if (event.getNewValue() == null || event.getNewValue().isEmpty()) {
                table.getItems().remove(table.getSelectionModel().getFocusedIndex());
            } else {
                event.getRowValue().setChoice(event.getNewValue());
            }
        });

        table.getColumns().addAll(choicesColumn, answersColumn);

        /* Populate Table */

        if (getAnswer(annotation) != null && SimpleAnnotationPane.isJSON(getAnswer(annotation))) {
            List<SimpleAnnotationPane.MultichoiceOption> options = List.of(new Gson().fromJson(getAnswer(annotation), SimpleAnnotationPane.MultichoiceOption[].class));

            table.getItems().addAll(options);
        }

        return table;
    }

    private static void addRowToTable(TableView<SimpleAnnotationPane.MultichoiceOption> table) {
        table.getItems().add(new SimpleAnnotationPane.MultichoiceOption());
        table.layout();
        table.getSelectionModel().selectLast();
        table.edit(table.getItems().size() - 1, table.getColumns().get(0));
    }

    private static void removeRowFromTable(TableView<SimpleAnnotationPane.MultichoiceOption> table) {
        table.getItems().remove(table.getSelectionModel().getFocusedIndex());
    }

    private static void setAnswer(PathObject annotation, String value) {
        annotation.storeMetadataValue(SimpleAnnotationPane.ANSWER_KEY, value);
    }

    private static String getAnswer(PathObject annotation) {
        return (String) annotation.retrieveMetadataValue(SimpleAnnotationPane.ANSWER_KEY);
    }
}

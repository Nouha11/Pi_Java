package controllers.forum;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.stage.Modality;
import javafx.stage.Stage;
import services.api.JDoodleService;

import java.util.HashMap;
import java.util.Map;

public class CodeSandboxController {

    @FXML private ComboBox<String> languageCombo;
    @FXML private TextArea codeArea;
    @FXML private TextArea outputArea;
    @FXML private Button runButton;

    private final JDoodleService jdoodleService = new JDoodleService();
    private final Map<String, String[]> languageMap = new HashMap<>();

    @FXML
    public void initialize() {
        languageMap.put("Java", new String[]{"java", "4"});
        languageMap.put("Python", new String[]{"python3", "3"});

        languageCombo.getItems().addAll(languageMap.keySet());
        languageCombo.setValue("Java");

        codeArea.setText("public class MyClass {\n    public static void main(String[] args) {\n        System.out.println(\"Hello, NOVA!\");\n    }\n}");

        languageCombo.setOnAction(e -> {
            String selected = languageCombo.getValue();
            if (selected.equals("Python")) {
                codeArea.setText("print(\"Hello, NOVA!\")");
            } else {
                codeArea.setText("public class MyClass {\n    public static void main(String[] args) {\n        System.out.println(\"Hello, NOVA!\");\n    }\n}");
            }
        });
    }

    @FXML
    void handleRunCode(ActionEvent event) {
        String code = codeArea.getText();
        if (code == null || code.trim().isEmpty()) return;

        String selectedLang = languageCombo.getValue();
        String[] jdoodleData = languageMap.get(selectedLang);
        String langCode = jdoodleData[0];
        String versionIndex = jdoodleData[1];

        runButton.setDisable(true);
        runButton.setText("Compiling...");
        outputArea.setText("> Executing code on JDoodle Remote Server...\n");

        new Thread(() -> {
            String result = jdoodleService.executeCode(code, langCode, versionIndex);
            Platform.runLater(() -> {
                runButton.setDisable(false);
                runButton.setText("▶ Run Code");
                outputArea.setText("> Execution Finished.\n\n" + result);
            });
        }).start();
    }

    @FXML
    void handleShareToForum(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/forum/student/add_post.fxml"));
            Parent root = loader.load();

            Object controller = loader.getController();
            try {
                java.lang.reflect.Method method = controller.getClass().getMethod("setPrefilledContent", String.class);
                String snippet = "I need help with this " + languageCombo.getValue() + " code:\n\n```" + languageCombo.getValue().toLowerCase() + "\n" + codeArea.getText() + "\n```";
                method.invoke(controller, snippet);
            } catch (Exception ignored) {}

            Stage popupStage = new Stage();
            popupStage.setTitle("Create a New Post");
            popupStage.setScene(new Scene(root, 700, 600));
            popupStage.initModality(Modality.APPLICATION_MODAL);
            popupStage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
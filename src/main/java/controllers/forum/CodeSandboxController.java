package controllers.forum;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import services.api.JDoodleService;

import java.util.HashMap;
import java.util.Map;

public class CodeSandboxController {

    @FXML private ComboBox<String> languageCombo;
    @FXML private TextArea codeArea;
    @FXML private TextArea outputArea;
    @FXML private Button runButton;

    // 🔥 Switch to JDoodle API
    private final JDoodleService jdoodleService = new JDoodleService();

    // We map UI names to a string array containing [JDoodleLanguageCode, VersionIndex]
    private final Map<String, String[]> languageMap = new HashMap<>();

    @FXML
    public void initialize() {
        // JDoodle requires specific Language Codes and Version Indexes
        // 'java' uses version '4' (JDK 17), 'python3' uses version '3'
        languageMap.put("Java", new String[]{"java", "4"});
        languageMap.put("Python", new String[]{"python3", "3"});

        languageCombo.getItems().addAll(languageMap.keySet());
        languageCombo.setValue("Java"); // Default

        // Default boilerplate code for Java
        codeArea.setText("public class MyClass {\n    public static void main(String[] args) {\n        System.out.println(\"Hello, NOVA!\");\n    }\n}");

        // Change boilerplate if they select a different language
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

        // Run the API call on a background thread
        new Thread(() -> {
            String result = jdoodleService.executeCode(code, langCode, versionIndex);

            Platform.runLater(() -> {
                runButton.setDisable(false);
                runButton.setText("▶ Run Code");

                // Print the result to the black terminal box
                outputArea.setText("> Execution Finished.\n\n" + result);
            });
        }).start();
    }
}
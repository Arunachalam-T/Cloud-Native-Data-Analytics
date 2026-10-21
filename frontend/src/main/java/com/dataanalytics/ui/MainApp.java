package com.dataanalytics.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApp extends Application {

    public static final String APP_TITLE = "Cloud-Native Data Analytics Terminal";
    public static final String BASE_URL = System.getenv().getOrDefault("API_BASE_URL", "http://localhost:8080");
    public static final String API_KEY  = System.getenv().getOrDefault("API_KEY", "admin");

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/fxml/MainView.fxml")
        );
        Scene scene = new Scene(loader.load(), 1400, 900);
        scene.getStylesheets().add(
            getClass().getResource("/css/dark-theme.css").toExternalForm()
        );

        stage.setTitle(APP_TITLE);
        stage.setScene(scene);
        stage.setMinWidth(1100);
        stage.setMinHeight(700);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

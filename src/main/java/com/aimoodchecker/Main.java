package com.aimoodchecker;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import com.aimoodchecker.dao.DBConnection;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        try {
            System.out.println("Starting application...");
            
            // Initialize database
            System.out.println("Initializing database...");
            DBConnection.initDatabase();

            System.out.println("Loading FXML...");
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/App.fxml"));

            Parent root = loader.load();
            
            Scene scene = new Scene(root,960,600);
            
            // Load CSS
            var css = getClass().getResource("/styles.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
                System.out.println("CSS loaded successfully");
            } else {
                System.err.println("CSS file not found!");
            }
            
            // Set up the stage
            stage.setTitle("AI Mood Checker");
            stage.setScene(scene);
            stage.show();
            System.out.println("Application started successfully");
        } catch (Exception e) {
            System.err.println("Error starting application: " + e.getMessage());
            e.printStackTrace();
            throw e;
                  
            
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

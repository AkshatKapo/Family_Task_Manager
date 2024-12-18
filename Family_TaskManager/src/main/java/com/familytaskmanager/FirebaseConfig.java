package com.familytaskmanager;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.FileInputStream;
import java.io.IOException;


public class FirebaseConfig {

    public static void initializeFirebase() {
        try {
            // Load the service account key from the specified file path
            FileInputStream serviceAccount = new FileInputStream(
                    "C:\\Users\\aksha\\projects\\Family_TaskManager_Project\\Family_TaskManager\\src\\serviceAccountKey.json"
            );

            // Create Firebase options with the credentials and database URL
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount)) // Authenticate using the service account key
                    .setDatabaseUrl("https://family-task-manager-5cbc2-default-rtdb.firebaseio.com/") // Replace with your Firebase Realtime Database URL
                    .build();

            // Initialize the Firebase application with the specified options
            FirebaseApp.initializeApp(options);
            System.out.println("Firebase Initialized!"); // Print confirmation message on successful initialization
        } catch (IOException e) {
            // Handle errors during initialization, such as file not found or invalid credentials
            System.err.println("Failed to initialize Firebase: " + e.getMessage());
        }
    }
}

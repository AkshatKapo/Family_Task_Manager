package com.familytaskmanager;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;



import java.util.HashMap;
import java.util.Map;

public class Userifo {
    public static void main(String[] args) {
        // Initialize Firebase
        com.familytaskmanager.FirebaseConfig.initializeFirebase();
        DatabaseReference database = FirebaseDatabase.getInstance().getReference();

        // Create users data
        Map<String, Object> users = new HashMap<>();
        users.put("user1", createUser("Parent User", "secure123", "parent"));
        users.put("user2", createUser("Kid User", "fun123", "kid"));

        // Add the users node to Firebase
        database.child("users").setValue(users, (databaseError, databaseReference) -> {
            if (databaseError != null) {
                System.err.println("Error adding users: " + databaseError.getMessage());
            } else {
                System.out.println("Users successfully added to Firebase.");
            }
        });
    }

    private static Map<String, String> createUser(String name, String password, String role) {
        Map<String, String> user = new HashMap<>();
        user.put("name", name);
        user.put("password", password); // Consider hashing passwords
        user.put("role", role);
        return user;
    }
}

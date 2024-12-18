package com.familytaskmanager.server;

import com.familytaskmanager.FirebaseConfig;
import com.familytaskmanager.model.Task;
import com.google.firebase.database.*;

import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Reminder {
    private static final int PORT = 12346; // Port for the server
    private static final Map<Socket, String> client_Connections = new HashMap<>(); // tracks connected clients
    private static DatabaseReference database; // Firebase database information

    public static void main(String[] args) {
        // Initialize Firebase
        FirebaseConfig.initializeFirebase();
        database = FirebaseDatabase.getInstance().getReference();

        Reminder_Service();// Begin the reminder service

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {   // Start listening for client connections

            System.out.println("Task Reminder Server is running...");

            while (true) {
                Socket clientSocket = serverSocket.accept();// creates the socket and ready to accept the request
                System.out.println("Client connected for reminders: " + clientSocket.getInetAddress());

               // Run the thread
                new Thread(new Reminder_ClientHandler(clientSocket)).start();// Handles client connection in a new thread
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void Reminder_Service() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);// Creates a ScheduledExecutorService with a single-threaded thread pool

        // Schedule a periodic task to check for tasks due soon
        scheduler.scheduleAtFixedRate(() -> {
            System.out.println("Checking for tasks due in the next 2 minutes...");

            // Check the Firebase database for all tasks
            database.child("tasks").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    // Checks if there are any tasks in the database
                    if (snapshot.exists()) {
                        // Iterate through each task in the database
                        for (DataSnapshot taskSnapshot : snapshot.getChildren()) {
                            Task task = taskSnapshot.getValue(Task.class);
                            // Checks if the task is valid, status is pending, if it is then send reminder
                            if (task != null && "pending".equals(task.getStatus())) {
                                Send_Reminder(task);
                            }
                        }
                    }
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    System.err.println("Error checking tasks: " + error.getMessage());
                }
            });
        }, 0, 1, TimeUnit.MINUTES); // Check every minute
    }

    public static void Send_Reminder(Task task) {
        try {
            // Validate task fields before proceeding
            if (task.getDueDate() == null || task.getDueDate().isEmpty()) {
                System.err.println("Skipping task due to missing due date: " + task.getId());
                return;
            }
               // Checks if the user name is empty
            if (task.getName() == null || task.getName().isEmpty()) {
                System.err.println("Skipping task due to missing name: " + task.getId());
                return;
            }
            // check if the assignedTo field is  empty
            if (task.getAssignedTo() == null || task.getAssignedTo().isEmpty()) {
                System.err.println("Skipping task due to missing assignedTo: " + task.getId());
                return;
            }

            // Parse the task's due date
            LocalDateTime dueDate = LocalDateTime.parse(task.getDueDate()); // Ensure dueDate is in ISO_LOCAL_DATE_TIME format
            LocalDateTime now = LocalDateTime.now();

            // Calculate time difference
            long minutesUntilDue = ChronoUnit.MINUTES.between(now, dueDate);

            // Send reminder to the client if the task is due in the next 2 minutes
            if (minutesUntilDue == 2) {
                Remind_Client(task);
            }
        } catch (Exception e) {
            System.err.println("Error processing task reminder for task: " + task.getId() + " - " + e.getMessage());
        }
    }


    private static void Remind_Client(Task task) {
        synchronized (client_Connections) {
            // iterate over the entries in the client_connections map

            for (Map.Entry<Socket, String> entry : client_Connections.entrySet()) {
               // Check if the current client is the one who assiged the task
                if (entry.getValue().equals(task.getAssignedTo())) {
                    try {
                        // Send message to the client
                        PrintWriter clientOut = new PrintWriter(entry.getKey().getOutputStream(), true);
                        // Send the reminder message to the client
                        clientOut.println("Reminder: Task \"" + task.getName() + "\" is due in 2 minutes. Please complete it.");
                    } catch (Exception e) {
                        System.err.println("Failed to send reminder to client: " + e.getMessage());
                    }
                }
            }
        }
    }

    static class Reminder_ClientHandler implements Runnable {
        private Socket clientSocket;

        public Reminder_ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {

            // Read the input from the clients socket
            try (Scanner in = new Scanner(clientSocket.getInputStream())) {
                // Send output to the clients socket
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

                // Read the username sent by the client
                String username = in.nextLine().trim();
                System.out.println("User connected to reminder server: " + username);

                // Map the socket to the username
                synchronized (client_Connections) {
                    client_Connections.put(clientSocket, username);
                }

                // Notify the user that they are subscribed to reminders
                out.println("You are now subscribed to task reminders.");

                // Keep the connection open for reminders
                while (true) {
                    // checks if the client is still conected
                    if (!clientSocket.isConnected()) {
                        break;
                    }
                }
            } catch (Exception e) {
                System.err.println("Client disconnected: " + e.getMessage());
            } finally {
                synchronized (client_Connections) {
                    client_Connections.remove(clientSocket);
                }
                try {
                    clientSocket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

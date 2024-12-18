package com.familytaskmanager.server;

import com.familytaskmanager.FirebaseConfig;
import com.familytaskmanager.model.Task;
import com.google.firebase.database.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.*;

public class Backup {
    private static final int PORT = 11100; // Port for backup server
    private static final String MAIN_SERVER_HOST = "127.0.0.1"; // Main server host
    private static final int MAIN_SERVER_PORT = 12345; // Main server port

    private static final List<PrintWriter> clientHandlers = new ArrayList<>();// store PrintWriter objects for each connected client
    private static final Map<String, String> loggedInUsers = new HashMap<>();//store logged-in users' information
    private static final Map<Socket, String> socketToUsernameMap = new HashMap<>();// Map to link each connected socket to its corresponding username
    private static DatabaseReference database;

    public static void main(String[] args) {
        // Initialize Firebase
        FirebaseConfig.initializeFirebase();
        database = FirebaseDatabase.getInstance().getReference();

        // Begin monitoring the main server
        new Thread(Backup::monitorMainServerAndAct).start();

        System.out.println("Backup server is initialized.");// Print the  status to the terminal
    }

    private static void monitorMainServerAndAct() {
        boolean mainServerOnline = true;

        while (true) {
            try (Socket socket = new Socket(MAIN_SERVER_HOST, MAIN_SERVER_PORT)) {
                // Main server is online, keep backup server in passive mode
                System.out.println("Main server is online. Backup server in passive mode.");
                Thread.sleep(5000); // Check every 5 seconds

            } catch (IOException e) {
                if (mainServerOnline) {
                    // Transition to active mode only once
                    System.out.println("Main server is offline. Backup server is now active.");
                    activateBackupServer();// Activates the backup server to handle clients
                    mainServerOnline = false;// Makes the mainServeronline false because main server is ot running anymore
                }
            } catch (InterruptedException e) {
                System.err.println("Error in monitoring thread: " + e.getMessage());// Logs an error if thread interruption occurs
                Thread.currentThread().interrupt();// Restore the interrupted status
                break;
            }
        }
    }


    private static void activateBackupServer() {
        System.out.println("Backup server is now handling client requests.");// Logs the activation of backup server to let user knows the backup server is running
        try (ServerSocket backupServerSocket = new ServerSocket(PORT)) {// Creates a server socket for backup

            while (true) {
                try {
                    Socket clientSocket = backupServerSocket.accept();// Accepts the incoming client connections
                    System.out.println("Client connected to Backup Server: " + clientSocket.getInetAddress());
                    new Thread(new ClientHandler(clientSocket)).start();//Begin a new thread for each client
                } catch (IOException e) {
                    System.err.println("Error accepting client connection: " + e.getMessage());// Logs error during client connection, if the client connection was not successful
                }
            }
        } catch (IOException e) {
            System.err.println("Error starting backup server: " + e.getMessage());// Logs error during server start
            e.printStackTrace();
        }
    }


    static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private PrintWriter out;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));// Input Stream to read the input from the client
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)// Output Stream to send the output to the client socket
            ) {
                this.out = out;
                synchronized (clientHandlers) { // Adds the current client output stream to list of client handlers to process each client request
                    clientHandlers.add(out);
                }

                out.println("Welcome to the Backup Server!");//sends the welcome message to the client to print it on the console
                String message;// Stores the message/commands from the Clients



                while ((message = in.readLine()) != null) { // Read the message from the client continuously until they disconnect
                    System.out.println("Received: " + message);// Prints the message received from the server

                    // Processes the client message by sending them to their appropriate functions to get processed
                    if (message.toUpperCase().startsWith("LOGIN ")) {
                        handleLogin(message.substring(6).trim(), out);
                    } else if (message.toUpperCase().startsWith("ADD ")) {
                        handleAddTask(message.substring(4).trim(), out);
                    } else if (message.equalsIgnoreCase("LIST_TASKS")) {
                        handleListTasks(out);
                    } else if (message.toUpperCase().startsWith("DELETE ")) {
                        Delete_Task(message.substring(7).trim(), out);
                    } else if (message.toUpperCase().startsWith("UPDATE ")) {
                        Update_Task(message.substring(7).trim(), out);
                    } else if (message.toUpperCase().startsWith("COMPLETE ")) {
                        Complete_Task(message.substring(9).trim(), out);
                    } else {
                        out.println("Error: Incorrect command entered.");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // Ensure resources are cleaned up when the client disconnects
                synchronized (clientHandlers) {
                    // Removes the clients output stream from list
                    clientHandlers.remove(out);
                }
                synchronized (socketToUsernameMap) {
                    // Remove the client socket from the username map
                    socketToUsernameMap.remove(clientSocket);
                }
                try {
                    clientSocket.close();// Close the client socket
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        private void handleLogin(String loginDetails, PrintWriter out) {
            // Split the login information string into username and password parts
            String[] parts = loginDetails.split(" ");
            // Check if the format is valid
            if (parts.length != 2) {
                // Sends the error message to the client if the format is not correct
                out.println("Error: Invalid login format. Use LOGIN <username> <password>");
                return;
            }
            String username = parts[0];// Stores the username
            String password = parts[1];// Stores the password

            // Query the Firebase database gets the user's data
            database.child("users").child(username).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    // checks if the user data is in the database
                    if (snapshot.exists()) {
                        // Retrieve the stored password and username
                        String storedPassword = snapshot.child("password").getValue(String.class);
                        String role = snapshot.child("role").getValue(String.class);
                        // Checks if the provided password matches the stored password
                        if (storedPassword != null && storedPassword.equals(password)) {
                            synchronized (loggedInUsers) { // Stores logged-in user's role in the loggedInUsers map
                                loggedInUsers.put(username, role);// Adds it to the map
                            }
                            // Maps the client socket to the username for tracking
                            synchronized (socketToUsernameMap) {
                                socketToUsernameMap.put(clientSocket, username);// Adds it to the map
                            }
                            out.println("Login successful! Role: " + role);// Notify the client that login is successful
                        } else {
                            // Sends the error message if the password is wrong
                            out.println("Error: Invalid username or password.");
                        }
                    } else {
                        //Sends the error message if the user does not exist in the database
                        out.println("Error: User does not exist.");
                    }
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    // Send an error message if there was a problem querying the database
                    out.println("Error: Unable to authenticate. Please try again.");
                }
            });
        }

//        private void handleAddTask(String taskDetails, PrintWriter out) {
//            String username = getUsernameFromSocket(clientSocket);
//            if (username == null) {
//                out.println("Error: You must be logged in to add tasks.");
//                return;
//            }
//
//            if (!validateUserRole(username, "parent", out)) {
//                return;
//            }
//
//            String[] parts = taskDetails.split(";");
//            if (parts.length != 4) {
//                out.println("Error: Invalid task format. Use ADD <name>;<description>;<assignedTo>;<dueDate>");
//                return;
//            }
//
//            String name = parts[0].trim();
//            String description = parts[1].trim();
//            String assignedTo = parts[2].trim();
//            String dueDate = parts[3].trim();
//
//            if (!isValidISODateTime(dueDate)) {
//                out.println("Error: Invalid due date format. Use ISO format (YYYY-MM-DDTHH:mm:ss).");
//                return;
//            }
//
//            String taskId = database.child("tasks").push().getKey();
//            if (taskId == null) {
//                out.println("Error: Could not generate a unique task ID.");
//                return;
//            }
//
//            Task task = new Task(taskId, name, description, assignedTo, "pending", dueDate);
//            database.child("tasks").child(taskId).setValue(task, (error, ref) -> {
//                if (error != null) {
//                    out.println("Error saving task: " + error.getMessage());
//                } else {
//                    out.println("Task added successfully: " + name + " for " + assignedTo + ", due at " + dueDate);
//                }
//            });
//        }
private void handleAddTask(String taskDetails, PrintWriter out) {
    // Retrieve the username associated with the client
    String username = Username_Socket(clientSocket);
    // If the client is not logged in, send an error message
    if (username == null) {
        out.println("Error: You must be logged in to add tasks.");
        return;// Exit the method
    }
    // Check if the user has the required role(parent) to perform the operation
    if (!check_UserRole(username, "parent", out)) {
        return;// Exit the method if the user dont have the permission
    }
    //Split the task details into its components (name, description, assignedTo, optional dueDate)
    String[] parts = taskDetails.split(";");
    // Checks if the task details contain the required fields
    if (parts.length != 4) {
        // Send an error message to the client if the format is invalid
        out.println("Error: Invalid task format. Use ADD <name>;<description>;<assignedTo>;<dueDate>");
        return;// Exit the method
    }
    // Extract the components like name, description, assignedTo from the user input
    String name = parts[0].trim();
    String description = parts[1].trim();
    String assignedTo = parts[2].trim();
    String dueDate = (parts.length == 4) ? parts[3].trim() : ""; // Optional dueDate

    // Validate dueDate if provided
    if (!dueDate.isEmpty() && !Valid_DateTime(dueDate)) {
        out.println("Error: Invalid due date format. Use ISO format (YYYY-MM-DDTHH:mm:ss).");
        return;
    }
    // Add task to Firebase
    Task_Firebase(name, description, assignedTo, dueDate);

}


        private void handleListTasks(PrintWriter out) {
            // Gets the username associated with the client socket
            String username = Username_Socket(clientSocket);
            // Check if the client is logged in
            if (username == null) {
                // Send an error message to the client if they are not logged in
                out.println("Error: You must be logged in to list tasks.");
                out.flush();// Ensure the message is sent to the client
                return;// Exit the method because user is authenticated
            }

            // Fetch tasks assigned to the user from the database
            database.child("tasks").orderByChild("assignedTo").equalTo(username)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot snapshot) {
                            // Check if tasks exist in the database for the given user
                            if (snapshot.exists()) {
                                // Iterate through the tasks retrieved from the database
                                for (DataSnapshot taskSnapshot : snapshot.getChildren()) {
                                    // converts each task snapshot into a task object
                                    Task task = taskSnapshot.getValue(Task.class);
                                    // Checks if the task is valid,if it is then send to the client
                                    if (task != null) {
                                        out.println(task); // Send each task to the client
                                    }
                                }
                            } else {
                                // if no task are foud with the associated user, notify the client
                                out.println("No tasks assigned to you.");
                            }
                            out.println("END"); // Indicate end of task list
                            out.flush();// make sure all the messages are send to client
                        }

                        @Override
                        public void onCancelled(DatabaseError error) {
                            // Handle errors that occur while retrieving tasks
                            out.println("Error retrieving tasks: " + error.getMessage());
                            // Indicate the end of the task list, even if an error occurred
                            out.println("END");
                            out.flush();// Make sure all messages are sent to the client
                        }
                    });
        }

        private void Delete_Task(String taskId, PrintWriter out) {
            // Retrieve the username associated with the client socket
            String username = Username_Socket(clientSocket);
            // Check if the client is logged in
            if (username == null) {
                // Send an error message to the client if they are not logged in
                out.println("Error: You must be logged in to delete tasks.");
                return;// Exit the method
            }
            // Check if the user has a parent role because only parent can perform this task
            if (!check_UserRole(username, "parent", out)) {
                return;// Exit the method
            }
            // Access the firebase and remove the task wth the given taskId
            database.child("tasks").child(taskId).removeValue((error, ref) -> {
                // If there was an error deleting the task send the error message
                if (error != null) {
                    out.println("Error deleting task: " + error.getMessage());
                } else {
                    // Check if the task was successfully deleted, if it was then notify the client
                    out.println("Task deleted successfully.");

                }
            });
        }
        private void Update_Task(String updateDetails, PrintWriter out) {
            // Retrieve the username associated with the client socket
            String username = Username_Socket(clientSocket);
            // Check if the client is logged in
            if (username == null) {
                // Send an error message to the client if they are not logged in
                out.println("Error: You must be logged in to update tasks.");
                return;// Exit the method
            }
            // Check if the user has the paren role because only parent can delete the task
            if (!check_UserRole(username, "parent", out)) {
                return;// Exit the method
            }
            // Split the update details into task ID and update fields
            String[] parts = updateDetails.split(";", 2);
            // Checks if the update format includes a task ID and update field
            if (parts.length != 2) {
                // Send an error message to the client if the format is invalid
                out.println("Error: Invalid update format. Use UPDATE <taskId>;<field1=value1;field2=value2>");
                return;// Exit the method
            }
            // Get the task ID and update the fields from the input
            String taskId = parts[0].trim();// Task ID
            String[] updates = parts[1].split(";"); // Update the Fields
            // Create a map to store the updates as key-value pairs
            Map<String, Object> map_update = new HashMap<>();
            // Iterate over each update field and parse it into a key-value pair
            for (String update : updates) {
                String[] fieldValue = update.split("=");// Split the field and value
                if (fieldValue.length == 2) {
                    // Add the field and its new value to the update map
                    map_update.put(fieldValue[0].trim(), fieldValue[1].trim());
                }
            }
            // Check if there are valid updates to process
            if (!map_update.isEmpty()) {
                // Perform the update operation on the Firebase database
                database.child("tasks").child(taskId).updateChildren(map_update, (error, ref) -> {
                    if (error != null) {
                        // Checks if there is an error during the update if there was sent an error message
                        out.println("Error updating task: " + error.getMessage());
                    } else {
                        // Check if the update is successful,if it is notified the client
                        out.println("Task updated successfully: " + taskId);

                    }
                });
            } else {
                // Send an error message if no valid updates were provided
                out.println("Error: No valid updates provided.");
            }
        }

        private void Complete_Task(String taskId, PrintWriter out) {
            // Access the Firebase database to update the status of the specified task
            database.child("tasks").// Go to the "tasks" node in the database
                    child(taskId)// Locate the specific task by its ID
                    .child("status")// Target the "status" field of the task
                    .setValue("completed", (error, ref) -> {// Update the status to be completed from pending
                        if (error != null) {
                            // Checks if there is an error during the update if there was send an error message
                            out.println("Error marking task as completed: " + error.getMessage());
                        } else {
                            // Send an error message if no valid updates were provided
                            out.println("Task marked as completed successfully.");
                        }
                    });
        }
        private void Task_Firebase(String name, String description, String assignedTo, String dueDate) {
            // Reference to the lastId field in Firebase
            DatabaseReference lastIdRef = database.child("lastId");

            // Use a listener to get the current lastId value
            lastIdRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    // Check if lastId exists
                    long lastId = snapshot.exists() ? snapshot.getValue(Long.class) : 0;

                    // Increments the ID
                    long newId = lastId + 1;

                    // Create the task
                    Task task = new Task(String.valueOf(newId), name, description, assignedTo, "pending", dueDate);

                    // Add the task to the "tasks" node with the new ID
                    database.child("tasks").child(String.valueOf(newId)).setValue(task, (error, ref) -> {
                        if (error != null) {
                            System.err.println("Failed to add task to Firebase: " + error.getMessage());
                        } else {
                            System.out.println("Task added successfully with ID: " + newId);

                            out.println("Task added successfully: " + name + " (ID: " + newId + "), assigned to " + assignedTo + ", due at " + dueDate);

                            // Update the lastId value in Firebase
                            lastIdRef.setValue(newId, (error1, ref1) -> {
                                if (error1 != null) {
                                    // Logs an error message if the lastId update operation fails
                                    System.err.println("Failed to update lastId: " + error1.getMessage());
                                } else {
                                    // Logs the success message, if the operation was successfully updated in the database
                                    System.out.println("lastId updated to: " + newId);
                                }
                            });

                        }
                    });
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    System.err.println("Failed to read lastId: " + error.getMessage());
                }
            });
        }
        // Method to validate a date-time string
        private boolean Valid_DateTime(String dateTime) {
            try {
                // Attempt to parse the date-time string into a LocalDateTime object
                LocalDateTime.parse(dateTime);
                return true;// Return true if the parsing operation is successful
            } catch (Exception e) {
                // Return false if parsing fails, indicating an invalid date-time format
                return false;
            }
        }
        // Method to retrieve the username associated with a client socket
        private String Username_Socket(Socket clientSocket) {
            synchronized (socketToUsernameMap) {// Use a synchronized block ensure thread safe access
                return socketToUsernameMap.get(clientSocket);// Gets and return the username related to the give client socket
            }
        }

        // Method to check if a user has the required role for an action
        private boolean check_UserRole(String username, String requiredRole, PrintWriter out) {
            // Get the user's role from the logged_Users map
            String userRole = loggedInUsers.get(username);
            // Checks if the users role is same as the required role
            if (userRole != null && userRole.equals(requiredRole)) {
                return true;// if it matches then return true
            } else {
                // If the user does not have the required role, send an error message to the client
                out.println("Error: You do not have permission to perform this action.");
                return false;// Returns false to indicate the client has does not have the required permission
            }
        }
        }

    }


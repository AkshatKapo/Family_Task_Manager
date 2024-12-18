package com.familytaskmanager.client;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Client {
    private static final String SERVER_ADDRESS = "127.0.0.1"; // Localhost
    private static final int MAIN_SERVER_PORT = 12345;
    private static final int BACKUP_SERVER_PORT = 11100;
    private static final int REMINDER_SERVER_PORT = 12346;

    private static String username;// Stores the logged-in user's username
    private static String password;// Stores the logged-in user's password


    public static void main(String[] args) {
        Socket serverSocket = null;// Socket for server communication
        BufferedReader serverIn = null; // Input stream to receive data from the server
        PrintWriter serverOut = null;// Output stream to send data to the server

        Socket reminderSocket = null;// Socket for connecting to the reminder server
        PrintWriter reminderOut = null;// Output stream to send data to the reminder server
        BufferedReader reminderIn = null; // Input stream to receive reminders

        boolean usingBackupServer = false;// Tracks if the backup server is currently being used

        try {
            // Attempt connection to main server
            try {
                serverSocket = new Socket(SERVER_ADDRESS, MAIN_SERVER_PORT);
                serverIn = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
                serverOut = new PrintWriter(serverSocket.getOutputStream(), true);
                System.out.println("Connected to the main server.");
            } catch (IOException e) {
                // If the main server is unavailable, try connecting to the backup server
                System.out.println("Main server unavailable. Attempting to connect to the backup server...");
                serverSocket = new Socket(SERVER_ADDRESS, BACKUP_SERVER_PORT);
                serverIn = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
                serverOut = new PrintWriter(serverSocket.getOutputStream(), true);
                usingBackupServer = true;
                System.out.println("Connected to the backup server.");
            }

            // Read and display the server's welcome message
            System.out.println("Server says: " + serverIn.readLine()); // Read welcome message

            // Connect to Reminder Server
            reminderSocket = connectToReminderServer();
            if (reminderSocket != null) {
                reminderOut = new PrintWriter(reminderSocket.getOutputStream(), true);
                reminderIn = new BufferedReader(new InputStreamReader(reminderSocket.getInputStream()));

                // Start a thread to handle reminders
                startReminderListener(reminderIn);
            }

            // Login
            BufferedReader consoleInput = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                System.out.print("Enter username: ");// Prompt for username
                username = consoleInput.readLine();

                System.out.print("Enter password: ");// Prompt for password
                password = consoleInput.readLine();

                // Send login credentials to the server

                serverOut.println("LOGIN " + username + " " + password);

                // Read the server's response to the login attempt
                String serverResponse = serverIn.readLine();
                // Notify the reminder server of the logged-in user
                if (serverResponse != null && serverResponse.contains("Login successful")) {
                    System.out.println(serverResponse);

                    // Notify reminder server
                    if (reminderOut != null) {
                        reminderOut.println(username);
                        System.out.println("Subscribed to reminders.");
                    }
                    break;// Exit the login loop on successful login
                } else {
                    System.out.println(serverResponse); // Display error message if login fails
                }
            }

            // Command Loop
            String userInput;
            while (true) {
                // Process the user's input
                System.out.println("\n===== Menu =====");
                System.out.println("1. Add Task");
                System.out.println("2. Delete Task");
                System.out.println("3. Update Task");
                System.out.println("4. Mark Task as Complete");
                System.out.println("5. List Tasks");
                System.out.println("6. Exit");
                System.out.print("Enter your choice: ");
                userInput = consoleInput.readLine();

                try {
                    switch (userInput.trim()) {
                        case "1":
                            handleAddTask(consoleInput, serverOut, serverIn);
                            break;
                        case "2":
                            handleDeleteTask(consoleInput, serverOut, serverIn);
                            break;
                        case "3":
                            handleUpdateTask(consoleInput, serverOut, serverIn);
                            break;
                        case "4":
                            handleCompleteTask(consoleInput, serverOut, serverIn);
                            break;
                        case "5":
                            handleListTasks(consoleInput, serverOut, serverIn);
                            break;
                        case "6":
                            System.out.println("Disconnecting from servers...");
                            return;// Exit the program
                        default:
                            System.out.println("Invalid choice. Please try again.");
                    }
                } catch (IOException e) {
                    // Handle server disconnection
                    System.out.println("Connection to the " + (usingBackupServer ? "backup" : "main") + " server lost.");
                    // Reconnect
                    try {
                        reconnectToServer(usingBackupServer);
                        usingBackupServer = !usingBackupServer; // Toggle between main and backup server
                    } catch (IOException ex) {
                        System.out.println("Failed to reconnect. Exiting...");
                        break;// Exit the program if reconnection fails
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Client exception: " + e.getMessage());
        } finally {
            try {
                if (reminderSocket != null) reminderSocket.close();
                if (serverSocket != null) serverSocket.close();
            } catch (IOException e) {
                // General exception handler for the client
                System.out.println("Error closing sockets: " + e.getMessage());
            }
        }
    }

    private static void reconnectToServer(boolean usingBackup) throws IOException {
        // Check if the current connection is to the backup server
        if (usingBackup) {
            System.out.println("Attempting to reconnect to the main server...");
            // Create a new socket connection to the main server
            Socket serverSocket = new Socket(SERVER_ADDRESS, MAIN_SERVER_PORT);
            // Call the reconnect method to handle re-authentication with the main server
            reconnect(serverSocket, "Main server");
        } else {
            System.out.println("Attempting to reconnect to the backup server...");
            // Create a new socket connection to the backup server
            Socket serverSocket = new Socket(SERVER_ADDRESS, BACKUP_SERVER_PORT);
            // Call the reconnect method to handle re-authentication with the backup server
            reconnect(serverSocket, "Backup server");
        }
    }
    private static void reconnect(Socket serverSocket, String serverType) throws IOException {
        // Create a BufferedReader to read incoming messages from the server
        BufferedReader serverIn = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
        // Create a PrintWriter to send outgoing messages to the server
        PrintWriter serverOut = new PrintWriter(serverSocket.getOutputStream(), true);

        // Notify the user that the connection to the specified server is successful
        System.out.println("Connected to the " + serverType + ".");

        // Send a login command with the user's credentials to re-authenticate
        serverOut.println("LOGIN " + username + " " + password);

        // Read the server's response to the login command
        String serverResponse = serverIn.readLine();

        // Check if the login was successful based on the server's response
        if (serverResponse != null && serverResponse.contains("Login successful")) {
            // Notify the user that re-authentication to the server was successful
            System.out.println("Automatically re-logged in to the " + serverType + ".");
        } else {
            // If the login fails, throw an IOException with an appropriate message
            throw new IOException("Failed to log in to the " + serverType + ".");
        }
    }


    private static void handleAddTask(BufferedReader consoleInput, PrintWriter serverOut, BufferedReader serverIn) throws IOException {
        // Prompt the user to enter the task name
        System.out.print("Enter task name: ");
        String taskName = consoleInput.readLine();

        // Prompt the user to enter the task description
        System.out.print("Enter task description: ");
        String taskDescription = consoleInput.readLine();

        // Prompt the user to specify who the task is assigned to
        System.out.print("Enter who this task is assigned to: ");
        String assignedTo = consoleInput.readLine();

        // Ask the user if they want to add a due date to the task
        System.out.print("Do you want to add a due date? (yes/no): ");
        String addDueDate = consoleInput.readLine().trim().toLowerCase();

        // Initialize the due date as an empty string
        String dueDate = "";
        if (addDueDate.equals("yes")) { // If the user wants to add a due date
            // Prompt the user to enter the date in the specified format
            System.out.print("Enter due date (YYYY-MM-DD): ");
            String date = consoleInput.readLine();

            // Ask if the user wants to include a specific time for the task
            System.out.print("Do you want to add a due time? (yes/no): ");
            String addDueTime = consoleInput.readLine().trim().toLowerCase();

            if (addDueTime.equals("yes")) { // If the user wants to add a specific time
                // Prompt the user to enter the time in the specified format
                System.out.print("Enter due time (HH:mm:ss): ");
                String time = consoleInput.readLine();
                // Combine the date and time into ISO 8601 format (e.g., YYYY-MM-DDTHH:mm:ss)
                dueDate = date + "T" + time;
            } else {
                // If no time is provided, set the default time to midnight (00:00:00)
                dueDate = date + "T00:00:00";
            }
        }

        // Build the ADD command with the task details
        String command = "ADD " + taskName + ";" + taskDescription + ";" + assignedTo;
        if (!dueDate.isEmpty()) {
            // Include the due date if it was provided
            command += ";" + dueDate;
        }

        // Send the ADD command to the server
        serverOut.println(command);

        // Read and display the server's response to the command
        System.out.println("Server response: " + serverIn.readLine());
    }


    private static void handleDeleteTask(BufferedReader consoleInput, PrintWriter serverOut, BufferedReader serverIn) throws IOException {
        // Prompt the user to enter the ID of the task they wish to delete
        System.out.print("Enter task ID to delete: ");
        String taskId = consoleInput.readLine(); // Read the task ID from the console

        // Send the DELETE command along with the task ID to the server
        serverOut.println("DELETE " + taskId);

        // Read and display the server's response to the delete command
        System.out.println("Server response: " + serverIn.readLine());
    }
    private static void handleUpdateTask(BufferedReader consoleInput, PrintWriter serverOut, BufferedReader serverIn) throws IOException {
        // Prompt the user to enter the ID of the task they want to update
        System.out.print("Enter task ID to update: ");
        String taskId = consoleInput.readLine(); // Read the task ID from the console

        // Display available fields that can be updated
        System.out.println("Fields available for update:");
        System.out.println(" - name: Task name");
        System.out.println(" - description: Task description");
        System.out.println(" - assignedTo: Assigned user");
        System.out.println(" - status: Task status (e.g., pending, completed)");
        System.out.println(" - dueDate: Due date and time in ISO format (YYYY-MM-DDTHH:mm:ss)");

        // Prompt the user to specify the fields they want to update in key-value format
        System.out.print("Enter updated fields in format (field=value;field=value): ");
        String updates = consoleInput.readLine(); // Read the updates from the console

        // Construct the UPDATE command with the task ID and updated fields
        String command = "UPDATE " + taskId + ";" + updates;

        // Send the UPDATE command to the server
        serverOut.println(command);

        // Read and display the server's response to the update command
        System.out.println("Server response: " + serverIn.readLine());
    }
    private static void handleCompleteTask(BufferedReader consoleInput, PrintWriter serverOut, BufferedReader serverIn) throws IOException {
        // Prompt the user to enter the ID of the task they want to mark as complete
        System.out.print("Enter task ID to mark as complete: ");
        String taskId = consoleInput.readLine(); // Read the task ID from the console

        // Send the COMPLETE command along with the task ID to the server
        serverOut.println("COMPLETE " + taskId);

        // Read and display the server's response to the complete command
        System.out.println("Server response: " + serverIn.readLine());
    }


    private static void handleListTasks(BufferedReader consoleInput, PrintWriter serverOut, BufferedReader serverIn) throws IOException {
        // Send the LIST_TASKS command to the server
        serverOut.println("LIST_TASKS");
        serverOut.flush();

        List<String> tasks = new ArrayList<>();
        String response;

        // Read tasks sent by the server
        while ((response = serverIn.readLine()) != null && !response.equals("END")) {
            tasks.add(response);
        }

        // If no tasks are found, notify the user
        if (tasks.isEmpty()) {
            System.out.println("No tasks found assigned to you.");
            return;
        }

        // Display tasks with pagination
        int pageSize = 5; // Number of tasks per page
        int currentPage = 0;

        while (true) {
            int start = currentPage * pageSize;
            int end = Math.min(start + pageSize, tasks.size());

            // Display the current page of tasks
            System.out.println("\n===== Task List (Page " + (currentPage + 1) + ") =====");
            for (int i = start; i < end; i++) {
                System.out.println((i + 1) + ". " + tasks.get(i));
            }
            System.out.println("=====================");

            // Pagination controls
            if (end == tasks.size()) {
                System.out.println("[P] Previous | [E] Exit");
            } else if (currentPage == 0) {
                System.out.println("[N] Next | [E] Exit");
            } else {
                System.out.println("[N] Next | [P] Previous | [E] Exit");
            }

            // Get user's input for navigation
            System.out.print("Enter your choice: ");
            String input = consoleInput.readLine().toUpperCase();

            if (input.equals("N") && end < tasks.size()) {
                currentPage++;
            } else if (input.equals("P") && currentPage > 0) {
                currentPage--;
            } else if (input.equals("E")) {
                break;
            } else {
                System.out.println("Invalid choice. Please try again.");
            }
        }
    }



    private static boolean isValidISODateTime(String dateTime) {
        try {
            java.time.LocalDateTime.parse(dateTime);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Socket connectToReminderServer() {
        // Infinite loop to continuously attempt a connection to the reminder server
        while (true) {
            try {
                // Attempt to establish a connection to the reminder server
                Socket socket = new Socket(SERVER_ADDRESS, REMINDER_SERVER_PORT);
                System.out.println("Connected to the reminder server.");
                return socket; // Return the connected socket upon success
            } catch (IOException e) {
                // If the connection fails, print an error message and retry after 5 seconds
                System.out.println("Failed to connect to the reminder server. Retrying in 5 seconds...");
                try {
                    // Pause execution for 5 seconds before retrying
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {
                    // If the sleep is interrupted, ignore the exception and retry immediately
                }
            }
        }
    }

    private static void startReminderListener(BufferedReader reminderIn) {
        // Create and start a new thread to handle reminders from the server
        new Thread(() -> {
            try {
                String reminderMessage; // Variable to store each reminder message received
                // Continuously read messages from the reminder server
                while ((reminderMessage = reminderIn.readLine()) != null) {
                    // Print each reminder message to the console with a label
                    System.out.println("[Reminder] " + reminderMessage);
                }
            } catch (IOException e) {
                // Handle exceptions that occur when the reminder server connection is lost
                System.err.println("Reminder server connection lost: " + e.getMessage());
            }
        }).start(); // Start the thread to begin listening for reminders
    }
    }


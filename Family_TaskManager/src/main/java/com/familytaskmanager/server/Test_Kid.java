package com.familytaskmanager.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Test_Kid {
    private static final String HOST = "localhost";
    private static final int PORT = 12345;

    public static void main(String[] args) {
        try {
            // Start a client connection to the server
            Socket clientSocket = new Socket(HOST, PORT);

            // Set up input and output streams for communication with the server
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            // Read and print the server's welcome message
            System.out.println("Server: " + in.readLine());

            // Test: Login command
            out.println("LOGIN KidUser1 fun124");
            System.out.println("Server: " + in.readLine());

            // Test: Add task command
            out.println("ADD Homework;Complete math exercises;child_user;2024-12-01T18:00:00");
            System.out.println("Server: " + in.readLine());

            // Test: List tasks command
            out.println("LIST_TASKS");
            String response;
            while (!(response = in.readLine()).equals("END")) {
                System.out.println("Server: " + response);
            }

            // Test: Update task command
            out.println("UPDATE 1;description=Complete all exercises;status=in-progress");
            System.out.println("Server: " + in.readLine());

            // Test: Complete task command
            out.println("COMPLETE 1");
            System.out.println("Server: " + in.readLine());

            // Test: Delete task command
            out.println("DELETE 1");
            System.out.println("Server: " + in.readLine());

            // Close the connection
            clientSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}



package com.familytaskmanager.model;

public class Task {
    private String id;
    private String name;
    private String description;
    private String assignedTo;
    private String status; // Task status (e.g., "pending" or "completed")
    private String dueDate; // Due date in ISO format (YYYY-MM-DDTHH:mm:ss)

    public Task() {
        // Default constructor for Firebase
    }

    public Task(String id, String name, String description, String assignedTo, String status, String dueDate) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.assignedTo = assignedTo;
        this.status = status;
        this.dueDate = dueDate;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDueDate() {
        return dueDate;
    }

    public void setDueDate(String dueDate) {
        this.dueDate = dueDate;
    }

    @Override
    public String toString() {
        return "Name: " + name + ", Description: " + description + ", Assigned To: " + assignedTo +
                ", Status: " + status + ", Due Date: " + dueDate;
    }
}

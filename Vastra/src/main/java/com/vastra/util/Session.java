package com.vastra.util;

import com.vastra.model.User;
import javafx.scene.control.Alert;

/** Holds who's currently logged in, for the lifetime of the running app. */
public class Session {
    private static User currentUser;

    public static void setCurrentUser(User user) { currentUser = user; }
    public static User getCurrentUser() { return currentUser; }
    public static boolean isLoggedIn() { return currentUser != null; }
    public static boolean isAdmin() { return currentUser != null && currentUser.isAdmin(); }
    public static void logout() { currentUser = null; }

    /** Call at the top of any admin-only action. Shows a warning and returns false if the current user isn't an admin. */
    public static boolean requireAdmin() {
        if (!isAdmin()) {
            new Alert(Alert.AlertType.WARNING, "This action is only available to admins.").showAndWait();
            return false;
        }
        return true;
    }
}
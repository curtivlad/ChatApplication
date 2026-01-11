package org.chatapp;

import javafx.application.Application;

/**
 * Launcher pentru aplicația LAN Chat.
 * Această clasă este entry point-ul aplicației.
 */
public class Launcher {
    public static void main(String[] args) {
        // Setări opționale pentru JavaFX
        System.setProperty("prism.lcdtext", "false");
        System.setProperty("prism.text", "t2k");

        // Lansează aplicația JavaFX
        Application.launch(ChatApplication.class, args);
    }
}
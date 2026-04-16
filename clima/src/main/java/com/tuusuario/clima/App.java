package com.tuusuario.clima;

import javax.swing.SwingUtilities;

public class App {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            WeatherFrame frame = new WeatherFrame();
            frame.setVisible(true);
        });
    }
}
 

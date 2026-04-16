package com.tuusuario.clima;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class WeatherFrame extends JFrame {
    private static final Color BACKGROUND_COLOR = new Color(245, 248, 252);
    private static final Color CARD_COLOR = Color.WHITE;
    private static final Color TEXT_PRIMARY = new Color(33, 43, 54);
    private static final Color TEXT_SECONDARY = new Color(92, 107, 121);
    private static final Color STATUS_OK = new Color(28, 126, 88);
    private static final Color STATUS_LOADING = new Color(21, 101, 192);
    private static final Color STATUS_ERROR = new Color(198, 40, 40);

    private final JTextField cityField;
    private final JButton consultButton;
    private final JLabel locationValue;
    private final JLabel temperatureValue;
    private final JLabel windValue;
    private final JLabel descriptionValue;
    private final JLabel statusLabel;
    private final JTextArea forecastArea;
    private final WeatherService weatherService;

    public WeatherFrame() {
        super("Clima por Ciudad - Open-Meteo");
        this.weatherService = new WeatherService();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(780, 580);
        setMinimumSize(new Dimension(700, 520));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(14, 14));

        JPanel rootPanel = new JPanel(new BorderLayout(14, 14));
        rootPanel.setBackground(BACKGROUND_COLOR);
        rootPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel headerPanel = new JPanel(new BorderLayout(6, 4));
        headerPanel.setOpaque(false);

        JLabel titleLabel = new JLabel("Panel de Clima");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));
        titleLabel.setForeground(TEXT_PRIMARY);
        headerPanel.add(titleLabel, BorderLayout.NORTH);

        JLabel subtitleLabel = new JLabel("Consulta el estado actual y el pronostico de los proximos 5 dias");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        subtitleLabel.setForeground(TEXT_SECONDARY);
        headerPanel.add(subtitleLabel, BorderLayout.SOUTH);

        rootPanel.add(headerPanel, BorderLayout.NORTH);

        JPanel contentPanel = new JPanel(new BorderLayout(12, 12));
        contentPanel.setOpaque(false);

        JPanel inputPanel = new JPanel(new GridLayout(2, 2, 8, 8));
        inputPanel.setBackground(CARD_COLOR);
        inputPanel.setBorder(BorderFactory.createTitledBorder("Ingresa ciudad"));

        JLabel cityLabel = new JLabel("Ciudad:");
        cityLabel.setForeground(TEXT_PRIMARY);
        inputPanel.add(cityLabel);

        cityField = new JTextField("Ciudad de Mexico");
        cityField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        inputPanel.add(cityField);

        inputPanel.add(new JLabel());
        consultButton = new JButton("Consultar clima");
        consultButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        inputPanel.add(consultButton);
        contentPanel.add(inputPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 12, 12));
        centerPanel.setOpaque(false);

        JPanel resultPanel = new JPanel(new GridLayout(5, 2, 8, 8));
        resultPanel.setBackground(CARD_COLOR);
        resultPanel.setBorder(BorderFactory.createTitledBorder("Clima actual"));

        Font valueFont = new Font("Segoe UI", Font.BOLD, 16);
        Font labelFont = new Font("Segoe UI", Font.PLAIN, 14);

        resultPanel.add(new JLabel("Ubicacion:", SwingConstants.RIGHT));
        locationValue = new JLabel("--");
        locationValue.setFont(valueFont);
        resultPanel.add(locationValue);

        resultPanel.add(new JLabel("Temperatura:", SwingConstants.RIGHT));
        resultPanel.getComponent(resultPanel.getComponentCount() - 1).setFont(labelFont);
        temperatureValue = new JLabel("--");
        temperatureValue.setFont(valueFont);
        resultPanel.add(temperatureValue);

        resultPanel.add(new JLabel("Viento:", SwingConstants.RIGHT));
        resultPanel.getComponent(resultPanel.getComponentCount() - 1).setFont(labelFont);
        windValue = new JLabel("--");
        windValue.setFont(valueFont);
        resultPanel.add(windValue);

        resultPanel.add(new JLabel("Descripcion:", SwingConstants.RIGHT));
        resultPanel.getComponent(resultPanel.getComponentCount() - 1).setFont(labelFont);
        descriptionValue = new JLabel("--");
        descriptionValue.setFont(valueFont);
        resultPanel.add(descriptionValue);

        resultPanel.add(new JLabel("Estado:", SwingConstants.RIGHT));
        resultPanel.getComponent(resultPanel.getComponentCount() - 1).setFont(labelFont);
        statusLabel = new JLabel("Listo para consultar");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        statusLabel.setForeground(STATUS_OK);
        resultPanel.add(statusLabel);

        JPanel forecastPanel = new JPanel(new BorderLayout(8, 8));
        forecastPanel.setBackground(CARD_COLOR);
        forecastPanel.setBorder(BorderFactory.createTitledBorder("Pronostico de 5 dias"));

        forecastArea = new JTextArea();
        forecastArea.setEditable(false);
        forecastArea.setLineWrap(true);
        forecastArea.setWrapStyleWord(true);
        forecastArea.setFont(new Font("Consolas", Font.PLAIN, 14));
        forecastArea.setText("Consulta una ciudad para ver el pronostico detallado aqui.");

        JScrollPane forecastScrollPane = new JScrollPane(forecastArea);
        forecastScrollPane.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        forecastPanel.add(forecastScrollPane, BorderLayout.CENTER);

        centerPanel.add(resultPanel);
        centerPanel.add(forecastPanel);
        contentPanel.add(centerPanel, BorderLayout.CENTER);
        rootPanel.add(contentPanel, BorderLayout.CENTER);

        add(rootPanel, BorderLayout.CENTER);

        consultButton.addActionListener(event -> onConsultWeather());
    }

    private void onConsultWeather() {
        String city;

        try {
            city = parseCity(cityField.getText());
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
            return;
        }

        consultButton.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        setStatus("Consultando API...", STATUS_LOADING);

        SwingWorker<WeatherViewData, Void> worker = new SwingWorker<>() {
            @Override
            protected WeatherViewData doInBackground() throws Exception {
                WeatherData current = weatherService.fetchCurrentWeather(city);
                String forecast = weatherService.fetchFiveDayForecast(city);
                return new WeatherViewData(current, forecast);
            }

            @Override
            protected void done() {
                consultButton.setEnabled(true);
                setCursor(Cursor.getDefaultCursor());

                try {
                    WeatherViewData viewData = get();
                    WeatherData data = viewData.currentWeather();
                    locationValue.setText(data.locationName());
                    temperatureValue.setText(String.format(Locale.US, "%.1f C", data.temperatureC()));
                    windValue.setText(String.format(Locale.US, "%.1f km/h", data.windSpeedKmh()));
                    descriptionValue.setText(data.description());
                    forecastArea.setText(viewData.fiveDayForecast());
                    forecastArea.setCaretPosition(0);
                    setStatus(String.format(Locale.US, "Actualizado %s", data.time()), STATUS_OK);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    setStatus("Consulta interrumpida", STATUS_ERROR);
                    showError("La consulta fue interrumpida.");
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    setStatus("Error al consultar", STATUS_ERROR);
                    showError(cause.getMessage() == null ? "Error inesperado en la consulta." : cause.getMessage());
                }
            }
        };

        worker.execute();
    }

    private void setStatus(String message, Color color) {
        statusLabel.setText(message);
        statusLabel.setForeground(color);
    }

    private String parseCity(String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Debes ingresar una ciudad.");
        }
        return normalized;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(
                this,
                message,
                "Error",
                JOptionPane.ERROR_MESSAGE
        );
    }

    private record WeatherViewData(WeatherData currentWeather, String fiveDayForecast) {
    }
}

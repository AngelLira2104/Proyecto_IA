package com.tuusuario.clima;

public record WeatherData(
        String locationName,
        double latitude,
        double longitude,
        String time,
        double temperatureC,
        double windSpeedKmh,
        int weatherCode,
        String description
) {
}

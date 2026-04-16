package com.tuusuario.clima;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WeatherService {
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private static final String API_URL_TEMPLATE =
            "https://api.open-meteo.com/v1/forecast"
                    + "?latitude=%.6f"
                    + "&longitude=%.6f"
                    + "&current=temperature_2m,wind_speed_10m,weather_code"
                    + "&timezone=auto";

    private static final String DAILY_FORECAST_URL_TEMPLATE =
            "https://api.open-meteo.com/v1/forecast"
                    + "?latitude=%.6f"
                    + "&longitude=%.6f"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                    + "&forecast_days=5"
                    + "&timezone=auto";

    private static final String GEOCODING_URL_TEMPLATE =
            "https://geocoding-api.open-meteo.com/v1/search?name=%s&count=1&language=es&format=json";

    private static final Pattern CURRENT_OBJECT_PATTERN =
            Pattern.compile("\"current\"\\s*:\\s*\\{(.*?)\\}", Pattern.DOTALL);
    private static final Pattern DAILY_OBJECT_PATTERN =
            Pattern.compile("\"daily\"\\s*:\\s*\\{(.*?)\\}", Pattern.DOTALL);
    private static final Pattern GEOCODING_RESULT_PATTERN =
            Pattern.compile("\"results\"\\s*:\\s*\\[(\\{.*?\\})", Pattern.DOTALL);

    private static final Pattern CITY_NAME_PATTERN =
            Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ADMIN1_PATTERN =
            Pattern.compile("\"admin1\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern COUNTRY_PATTERN =
            Pattern.compile("\"country\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern LOCATION_LATITUDE_PATTERN =
            Pattern.compile("\"latitude\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern LOCATION_LONGITUDE_PATTERN =
            Pattern.compile("\"longitude\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");

    private static final Pattern TIME_PATTERN =
            Pattern.compile("\"time\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern TEMPERATURE_PATTERN =
            Pattern.compile("\"temperature_2m\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern WIND_PATTERN =
            Pattern.compile("\"wind_speed_10m\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern WEATHER_CODE_PATTERN =
            Pattern.compile("\"weather_code\"\\s*:\\s*(-?\\d+)");

    private static final Pattern DAILY_TIME_ARRAY_PATTERN =
            Pattern.compile("\"time\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
    private static final Pattern DAILY_TEMP_MAX_ARRAY_PATTERN =
            Pattern.compile("\"temperature_2m_max\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
    private static final Pattern DAILY_TEMP_MIN_ARRAY_PATTERN =
            Pattern.compile("\"temperature_2m_min\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
    private static final Pattern DAILY_WEATHER_CODE_ARRAY_PATTERN =
            Pattern.compile("\"weather_code\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);

    private final HttpClient httpClient;
    private final Map<String, CachedWeatherEntry> weatherCache;
    private final Map<String, CachedForecastEntry> forecastCache;

    public WeatherService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.weatherCache = new ConcurrentHashMap<>();
        this.forecastCache = new ConcurrentHashMap<>();
    }

    public WeatherData fetchCurrentWeather(String city) throws IOException, InterruptedException {
        String normalizedCity = normalizeCity(city);
        String cacheKey = buildCityCacheKey(normalizedCity);

        WeatherData cachedData = getValidCachedWeather(cacheKey);
        if (cachedData != null) {
            return cachedData;
        }

        CityCoordinates coordinates = resolveCityCoordinates(normalizedCity);
        WeatherData freshData = fetchCurrentWeather(coordinates.latitude(), coordinates.longitude(), coordinates.displayName());
        putWeatherInCache(cacheKey, freshData);
        return freshData;
    }

    public String fetchFiveDayForecast(String city) throws IOException, InterruptedException {
        String normalizedCity = normalizeCity(city);
        String cacheKey = buildCityCacheKey(normalizedCity);

        String cachedForecast = getValidCachedForecast(cacheKey);
        if (cachedForecast != null) {
            return cachedForecast;
        }

        CityCoordinates coordinates = resolveCityCoordinates(normalizedCity);
        String freshForecast = fetchFiveDayForecast(
                coordinates.latitude(),
                coordinates.longitude(),
                coordinates.displayName()
        );
        putForecastInCache(cacheKey, freshForecast);
        return freshForecast;
    }

    public WeatherData fetchCurrentWeather(double latitude, double longitude) throws IOException, InterruptedException {
        return fetchCurrentWeather(latitude, longitude, String.format(Locale.US, "%.4f, %.4f", latitude, longitude));
    }

    private WeatherData fetchCurrentWeather(double latitude, double longitude, String locationName)
            throws IOException, InterruptedException {
        String url = String.format(Locale.US, API_URL_TEMPLATE, latitude, longitude);
        HttpResponse<String> response = sendGetRequest(url);
        validateApiResponse(response, "Error de API Open-Meteo");

        String currentBlock = extractCurrentBlock(response.body());
        String time = extractString(currentBlock, TIME_PATTERN, "time");
        double temperature = extractDouble(currentBlock, TEMPERATURE_PATTERN, "temperature_2m");
        double wind = extractDouble(currentBlock, WIND_PATTERN, "wind_speed_10m");
        int weatherCode = extractInt(currentBlock, WEATHER_CODE_PATTERN, "weather_code");

        return new WeatherData(
                locationName,
                latitude,
                longitude,
                time,
                temperature,
                wind,
                weatherCode,
                mapWeatherCode(weatherCode)
        );
    }

    private String fetchFiveDayForecast(double latitude, double longitude, String locationName)
            throws IOException, InterruptedException {
        String url = String.format(Locale.US, DAILY_FORECAST_URL_TEMPLATE, latitude, longitude);
        HttpResponse<String> response = sendGetRequest(url);
        validateApiResponse(response, "Error de API Open-Meteo pronostico");

        String dailyBlock = extractDailyBlock(response.body());
        String[] dates = extractStringArray(dailyBlock, DAILY_TIME_ARRAY_PATTERN, "time");
        double[] maxTemps = extractDoubleArray(dailyBlock, DAILY_TEMP_MAX_ARRAY_PATTERN, "temperature_2m_max");
        double[] minTemps = extractDoubleArray(dailyBlock, DAILY_TEMP_MIN_ARRAY_PATTERN, "temperature_2m_min");
        int[] weatherCodes = extractIntArray(dailyBlock, DAILY_WEATHER_CODE_ARRAY_PATTERN, "weather_code");

        int days = Math.min(dates.length, Math.min(maxTemps.length, Math.min(minTemps.length, weatherCodes.length)));
        if (days == 0) {
            throw new IOException("Respuesta invalida: no se encontraron datos diarios para 5 dias.");
        }

        // Genera una salida legible para mostrar directamente en UI.
        StringBuilder formatted = new StringBuilder();
        formatted.append("Pronostico de 5 dias para ").append(locationName).append("\n\n");
        for (int i = 0; i < days; i++) {
            formatted.append(String.format(
                    Locale.US,
                    "%s | Min: %.1f C | Max: %.1f C | %s",
                    dates[i],
                    minTemps[i],
                    maxTemps[i],
                    mapWeatherCode(weatherCodes[i])
            ));
            if (i < days - 1) {
                formatted.append("\n");
            }
        }

        return formatted.toString();
    }

    private HttpResponse<String> sendGetRequest(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void validateApiResponse(HttpResponse<String> response, String messagePrefix) throws IOException {
        if (response.statusCode() == 200) {
            return;
        }

        String body = response.body() == null ? "" : response.body().trim();
        if (body.length() > 220) {
            body = body.substring(0, 220) + "...";
        }
        throw new IOException(messagePrefix + " (" + response.statusCode() + "). " + body);
    }

    private CityCoordinates resolveCityCoordinates(String city) throws IOException, InterruptedException {
        String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
        String url = String.format(Locale.US, GEOCODING_URL_TEMPLATE, encodedCity);
        HttpResponse<String> response = sendGetRequest(url);
        validateApiResponse(response, "Error de geocodificacion Open-Meteo");

        String resultBlock = extractGeocodingResult(response.body(), city);
        double latitude = extractDouble(resultBlock, LOCATION_LATITUDE_PATTERN, "latitude");
        double longitude = extractDouble(resultBlock, LOCATION_LONGITUDE_PATTERN, "longitude");
        String displayName = buildDisplayName(resultBlock, city);

        return new CityCoordinates(latitude, longitude, displayName);
    }

    private String normalizeCity(String city) {
        String normalizedCity = city == null ? "" : city.trim();
        if (normalizedCity.isEmpty()) {
            throw new IllegalArgumentException("Debes ingresar una ciudad.");
        }
        return normalizedCity;
    }

    private String extractGeocodingResult(String responseBody, String city) throws IOException {
        Matcher matcher = GEOCODING_RESULT_PATTERN.matcher(responseBody);
        if (!matcher.find()) {
            throw new IOException("No se encontro la ciudad '" + city + "'. Prueba con otro nombre.");
        }
        return matcher.group(1);
    }

    private String buildDisplayName(String block, String fallbackCity) {
        String name = extractOptionalString(block, CITY_NAME_PATTERN);
        String admin1 = extractOptionalString(block, ADMIN1_PATTERN);
        String country = extractOptionalString(block, COUNTRY_PATTERN);

        StringBuilder displayName = new StringBuilder();
        if (name != null && !name.isBlank()) {
            displayName.append(name);
        } else {
            displayName.append(fallbackCity);
        }
        if (admin1 != null && !admin1.isBlank()) {
            displayName.append(", ").append(admin1);
        }
        if (country != null && !country.isBlank()) {
            displayName.append(", ").append(country);
        }
        return displayName.toString();
    }

    private String extractOptionalString(String block, Pattern pattern) {
        Matcher matcher = pattern.matcher(block);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String buildCityCacheKey(String city) {
        return city.trim().toLowerCase(Locale.ROOT);
    }

    private WeatherData getValidCachedWeather(String cacheKey) {
        CachedWeatherEntry entry = weatherCache.get(cacheKey);
        if (entry == null) {
            return null;
        }

        Duration age = Duration.between(entry.cachedAt(), Instant.now());
        if (age.compareTo(CACHE_TTL) < 0) {
            return entry.data();
        }

        weatherCache.remove(cacheKey);
        return null;
    }

    private void putWeatherInCache(String cacheKey, WeatherData data) {
        weatherCache.put(cacheKey, new CachedWeatherEntry(data, Instant.now()));
    }

    private String getValidCachedForecast(String cacheKey) {
        CachedForecastEntry entry = forecastCache.get(cacheKey);
        if (entry == null) {
            return null;
        }

        Duration age = Duration.between(entry.cachedAt(), Instant.now());
        if (age.compareTo(CACHE_TTL) < 0) {
            return entry.forecast();
        }

        forecastCache.remove(cacheKey);
        return null;
    }

    private void putForecastInCache(String cacheKey, String forecast) {
        forecastCache.put(cacheKey, new CachedForecastEntry(forecast, Instant.now()));
    }

    private String extractCurrentBlock(String responseBody) throws IOException {
        Matcher matcher = CURRENT_OBJECT_PATTERN.matcher(responseBody);
        if (!matcher.find()) {
            throw new IOException("Respuesta invalida: no se encontro el bloque 'current'.");
        }
        return matcher.group(1);
    }

    private String extractDailyBlock(String responseBody) throws IOException {
        Matcher matcher = DAILY_OBJECT_PATTERN.matcher(responseBody);
        if (!matcher.find()) {
            throw new IOException("Respuesta invalida: no se encontro el bloque 'daily'.");
        }
        return matcher.group(1);
    }

    private String extractString(String block, Pattern pattern, String fieldName) throws IOException {
        Matcher matcher = pattern.matcher(block);
        if (!matcher.find()) {
            throw new IOException("Respuesta invalida: falta el campo '" + fieldName + "'.");
        }
        return matcher.group(1);
    }

    private double extractDouble(String block, Pattern pattern, String fieldName) throws IOException {
        Matcher matcher = pattern.matcher(block);
        if (!matcher.find()) {
            throw new IOException("Respuesta invalida: falta el campo '" + fieldName + "'.");
        }
        return Double.parseDouble(matcher.group(1));
    }

    private int extractInt(String block, Pattern pattern, String fieldName) throws IOException {
        Matcher matcher = pattern.matcher(block);
        if (!matcher.find()) {
            throw new IOException("Respuesta invalida: falta el campo '" + fieldName + "'.");
        }
        return Integer.parseInt(matcher.group(1));
    }

    private String[] extractStringArray(String block, Pattern pattern, String fieldName) throws IOException {
        Matcher matcher = pattern.matcher(block);
        if (!matcher.find()) {
            throw new IOException("Respuesta invalida: falta el campo '" + fieldName + "'.");
        }

        String rawArray = matcher.group(1).trim();
        if (rawArray.isEmpty()) {
            return new String[0];
        }

        String[] rawValues = rawArray.split(",");
        String[] values = new String[rawValues.length];
        for (int i = 0; i < rawValues.length; i++) {
            values[i] = stripQuotes(rawValues[i].trim());
        }
        return values;
    }

    private double[] extractDoubleArray(String block, Pattern pattern, String fieldName) throws IOException {
        Matcher matcher = pattern.matcher(block);
        if (!matcher.find()) {
            throw new IOException("Respuesta invalida: falta el campo '" + fieldName + "'.");
        }

        String rawArray = matcher.group(1).trim();
        if (rawArray.isEmpty()) {
            return new double[0];
        }

        String[] rawValues = rawArray.split(",");
        double[] values = new double[rawValues.length];
        for (int i = 0; i < rawValues.length; i++) {
            values[i] = Double.parseDouble(rawValues[i].trim());
        }
        return values;
    }

    private int[] extractIntArray(String block, Pattern pattern, String fieldName) throws IOException {
        Matcher matcher = pattern.matcher(block);
        if (!matcher.find()) {
            throw new IOException("Respuesta invalida: falta el campo '" + fieldName + "'.");
        }

        String rawArray = matcher.group(1).trim();
        if (rawArray.isEmpty()) {
            return new int[0];
        }

        String[] rawValues = rawArray.split(",");
        int[] values = new int[rawValues.length];
        for (int i = 0; i < rawValues.length; i++) {
            values[i] = Integer.parseInt(rawValues[i].trim());
        }
        return values;
    }

    private String stripQuotes(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String mapWeatherCode(int code) {
        return switch (code) {
            case 0 -> "Cielo despejado";
            case 1 -> "Mayormente despejado";
            case 2 -> "Parcialmente nublado";
            case 3 -> "Nublado";
            case 45, 48 -> "Niebla";
            case 51, 53, 55 -> "Llovizna";
            case 56, 57 -> "Llovizna helada";
            case 61, 63, 65 -> "Lluvia";
            case 66, 67 -> "Lluvia helada";
            case 71, 73, 75, 77 -> "Nieve";
            case 80, 81, 82 -> "Chubascos";
            case 85, 86 -> "Chubascos de nieve";
            case 95 -> "Tormenta electrica";
            case 96, 99 -> "Tormenta con granizo";
            default -> "Condicion no disponible (codigo " + code + ")";
        };
    }

    private record CityCoordinates(double latitude, double longitude, String displayName) {
    }

    private record CachedWeatherEntry(WeatherData data, Instant cachedAt) {
    }

    private record CachedForecastEntry(String forecast, Instant cachedAt) {
    }
}

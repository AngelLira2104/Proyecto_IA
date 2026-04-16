# Clima por Ciudad

## Resumen del proyecto

Aplicacion de escritorio en Java con Swing para consultar el clima actual de una ciudad usando la API de Open-Meteo.

La app toma el nombre de una ciudad, lo convierte automaticamente a coordenadas con el servicio de geocodificacion de Open-Meteo y despues consulta el pronostico actual. Muestra temperatura, velocidad del viento, descripcion del estado del tiempo y la ubicacion resuelta.

## Instrucciones de instalacion

### Requisitos

- JDK 17 instalado.
- Maven instalado si quieres ejecutar con el comando del plugin `exec`.
- Conexion a internet para consultar Open-Meteo.

### Con Maven

Desde la raiz del proyecto:

```powershell
mvn clean compile exec:java
```

### Sin Maven

Si no tienes Maven disponible, puedes compilar y ejecutar con el JDK:

```powershell
$files = Get-ChildItem -Path src/main/java -Recurse -Filter *.java | ForEach-Object { $_.FullName }
New-Item -ItemType Directory -Force -Path target/classes | Out-Null
& 'C:\Program Files\Java\jdk-17\bin\javac.exe' -d target/classes $files
& 'C:\Program Files\Java\jdk-17\bin\java.exe' -cp target/classes com.tuusuario.clima.App
```

## Guia de uso

1. Abre la aplicacion.
2. Escribe una ciudad, por ejemplo: Ciudad de Mexico, Madrid o Buenos Aires.
3. Haz clic en Consultar clima.
4. Revisa la temperatura, el viento, la descripcion y el nombre de la ubicacion resuelta.

## Ejemplo de resultados

Entrada:

```text
Ciudad de Mexico
```

Salida de ejemplo:

```text
Actualizado (2026-04-15T12:00, Ciudad de Mexico, Mexico)
Temperatura: 24.3 C
Viento: 11.2 km/h
Descripcion: Parcialmente nublado
```

No se incluyeron capturas de pantalla en este repositorio, pero el resultado anterior refleja el formato que muestra la interfaz.

## Funcionalidades

- Busqueda por nombre de ciudad.
- Geocodificacion automatica antes de consultar el clima.
- Consulta asincrona para no bloquear la interfaz grafica.
- Visualizacion de temperatura, viento, descripcion y ubicacion resuelta.
- Mensajes de error claros cuando la ciudad no existe o la API responde con un problema.
- Interfaz sencilla en Swing con un flujo de consulta directo.

## Calidad del codigo

- Nombres claros de variables y funciones: se usan nombres descriptivos como `cityField`, `fetchCurrentWeather` y `resolveCityCoordinates` para que el flujo se entienda rapido.
- Estructura organizada: la logica se separa en clases y funciones concretas, dejando la interfaz, la consulta a la API y el modelo de datos en responsabilidades distintas.
- Manejo adecuado de errores: se valida la entrada del usuario, se controlan fallos de red, respuestas invalidas de la API y errores de geocodificacion.
- Comentarios utiles: el codigo se apoya principalmente en nombres expresivos y estructura clara; se reservarian comentarios solo para casos complejos o poco obvios.
- Rendimiento: la consulta se ejecuta en segundo plano con `SwingWorker` para no bloquear la UI, aunque aun se podria mejorar con cache de resultados, reutilizacion de respuestas frecuentes y seleccion mas eficiente cuando una ciudad tiene varias coincidencias.

## Manejo de errores

- Si el campo de ciudad esta vacio, la app muestra un mensaje indicando que debes ingresar una ciudad.
- Si la geocodificacion no encuentra resultados, la app informa que la ciudad no existe o sugiere probar con otro nombre.
- Si la API de Open-Meteo responde con error o datos incompletos, la app muestra un mensaje claro en pantalla.
- La consulta se ejecuta en segundo plano para evitar que la interfaz se congele durante la peticion.

## Informacion de la API

La aplicacion usa dos endpoints de Open-Meteo:

- Geocodificacion: `https://geocoding-api.open-meteo.com/v1/search`
- Clima actual: `https://api.open-meteo.com/v1/forecast`

Primero se busca la ciudad por nombre y se obtienen sus coordenadas. Luego se consulta el pronostico actual con esos valores.

## Mejoras futuras

- Mostrar mas detalles del pronostico, como humedad, sensacion termica o precipitacion.
- Agregar iconos o una vista visual mas rica para el clima.
- Incluir historial de consultas recientes.
- Permitir seleccionar entre varias coincidencias cuando una ciudad tenga nombres repetidos.
- Agregar pruebas automatizadas para el servicio y la validacion de entrada.
- Incluir capturas de pantalla del resultado final.

## Estructura del proyecto

- src/main/java/com/tuusuario/clima/App.java: punto de entrada de la aplicacion.
- src/main/java/com/tuusuario/clima/WeatherFrame.java: ventana principal Swing.
- src/main/java/com/tuusuario/clima/WeatherService.java: logica de geocodificacion y consulta del clima.
- src/main/java/com/tuusuario/clima/WeatherData.java: modelo con los datos del clima.

## Notas

- La aplicacion resuelve la primera coincidencia encontrada para la ciudad ingresada.
- Si escribes una ciudad ambigua, puedes probar con mas contexto, por ejemplo: Guadalajara, Mexico o Valencia, Espana.
- Actualmente no hay pruebas automatizadas en el proyecto.

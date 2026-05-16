// Path: app/src/main/java/com/example/lab12_gpstracker/MainTrackerActivity.java
// Purpose: Main activity - Handles GPS tracking and sends location to PHP server
// This is the entry point of the app when user opens it

package com.example.lab12_gpstracker;

// Android core imports
import android.Manifest;                          // For permission constants
import android.content.Context;                   // For accessing system services
import android.content.Intent;                    // For navigating between activities
import android.content.pm.PackageManager;         // For checking permissions
import android.location.Location;                 // Represents a GPS location
import android.location.LocationListener;         // Listens for GPS updates
import android.location.LocationManager;          // Manages GPS provider
import android.os.Bundle;                         // For saving activity state
import android.provider.Settings;                 // To get device ID
import android.widget.Button;                     // UI button component
import android.widget.TextView;                   // UI text component
import android.widget.Toast;                      // For showing popup messages

// AndroidX imports for backward compatibility
import androidx.annotation.NonNull;                // For non-null annotations
import androidx.appcompat.app.AppCompatActivity;  // Base activity class
import androidx.core.app.ActivityCompat;           // For runtime permissions

// Volley library imports - handles HTTP network requests
import com.android.volley.Request;                // HTTP request types (GET, POST)
import com.android.volley.RequestQueue;           // Manages request queue
import com.android.volley.toolbox.StringRequest;  // For POST requests
import com.android.volley.toolbox.Volley;         // Creates request queue

// Java utility imports
import java.text.SimpleDateFormat;                // For formatting date/time
import java.util.Date;                            // Current date/time
import java.util.HashMap;                         // Key-value pairs for POST data
import java.util.Locale;                          // For consistent formatting
import java.util.Map;                             // Map interface for parameters

/**
 * MainTrackerActivity - Core activity for GPS tracking
 *
 * This activity:
 * 1. Requests GPS permissions from the user
 * 2. Starts listening for GPS location updates
 * 3. Displays current coordinates (latitude, longitude, accuracy)
 * 4. Sends each location to the PHP server via HTTP POST
 * 5. Provides a button to open the map showing all tracked locations
 */
public class MainTrackerActivity extends AppCompatActivity {

    // Request code used to identify our permission request
    // Any number works, but we use 200 to be unique
    private static final int LOCATION_PERMISSION_REQUEST = 200;

    // UI components - display GPS data on screen
    private TextView latitudeDisplay;      // Shows latitude value
    private TextView longitudeDisplay;     // Shows longitude value
    private TextView accuracyDisplay;      // Shows GPS accuracy in meters
    private TextView statusDisplay;        // Shows connection/message status
    private Button openMapButton;          // Button to open map activity

    // Network components - send data to server
    // RequestQueue manages all HTTP requests in a queue
    private RequestQueue networkRequestQueue;

    // GPS components - get location from device
    // LocationManager is the system service for GPS
    private LocationManager gpsLocationManager;

    // Server URL where we send GPS data
    // IMPORTANT: Change this IP to match your computer's current IP address
    // Find IP by opening Command Prompt and typing: ipconfig
    // Look for "Wireless LAN adapter Wi-Fi" -> "IPv4 Address"
    private static final String SERVER_SAVE_URL = "http://192.168.110.210:8080/tracker_api/api/save_location.php";

    /**
     * onCreate - Called when the activity is first created
     * This is where we set up everything the app needs
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Set the layout XML file for this activity
        setContentView(R.layout.activity_main_tracker);

        // Step 1: Find all UI elements from the layout
        initializeUserInterface();

        // Step 2: Create Volley request queue for HTTP requests
        // Volley handles all network operations efficiently
        networkRequestQueue = Volley.newRequestQueue(this);

        // Step 3: Get the GPS location manager from Android system
        // LOCATION_SERVICE gives us access to the device's GPS
        gpsLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // Step 4: Set what happens when user clicks "Open Map" button
        // It navigates from MainTrackerActivity to LocationMapActivity
        openMapButton.setOnClickListener(view -> {
            // Create intent to switch activities
            Intent mapIntent = new Intent(MainTrackerActivity.this, LocationMapActivity.class);
            startActivity(mapIntent);  // Start the map activity
        });

        // Step 5: Check if we have GPS permission, if not, request it
        requestLocationPermissions();
    }

    /**
     * Initialize all UI elements by finding them from the layout
     * findViewById connects Java code to XML layout elements using their IDs
     */
    private void initializeUserInterface() {
        latitudeDisplay = findViewById(R.id.displayLatitude);
        longitudeDisplay = findViewById(R.id.displayLongitude);
        accuracyDisplay = findViewById(R.id.displayAccuracy);
        statusDisplay = findViewById(R.id.connectionStatus);
        openMapButton = findViewById(R.id.openMapButton);
    }

    /**
     * Check if the app has GPS permission
     * On Android 6.0+, we must request permission at runtime
     * If already granted, start tracking immediately
     */
    private void requestLocationPermissions() {
        // Check if FINE_LOCATION permission is not yet granted
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Request both fine and coarse location permissions
            // A dialog will appear asking the user to allow or deny
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,    // Precise GPS
                            Manifest.permission.ACCESS_COARSE_LOCATION   // Network-based
                    },
                    LOCATION_PERMISSION_REQUEST  // Our request code for identification
            );
        } else {
            // Permission already granted, start GPS tracking now
            startGpsTracking();
        }
    }

    /**
     * Start listening for GPS location updates
     * Configures how often to check and minimum distance for updates
     */
    private void startGpsTracking() {
        // Double-check permission before accessing GPS
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            // Create a listener that reacts to GPS events
            LocationListener gpsListener = new LocationListener() {

                /**
                 * Called every time the GPS detects a new location
                 * This is the main callback we use
                 */
                @Override
                public void onLocationChanged(@NonNull Location newLocation) {
                    // Update the coordinates displayed on screen
                    updateLocationDisplay(newLocation);
                    // Send the new location to our PHP server
                    transmitLocationToServer(newLocation);
                }

                /**
                 * Called when GPS provider status changes (available, unavailable, etc.)
                 */
                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                    updateStatusDisplay("GPS Status: " + status);
                }

                /**
                 * Called when the user enables GPS on their device
                 */
                @Override
                public void onProviderEnabled(@NonNull String provider) {
                    updateStatusDisplay("GPS is now active");
                    showToastMessage("GPS tracking enabled");
                }

                /**
                 * Called when the user disables GPS on their device
                 */
                @Override
                public void onProviderDisabled(@NonNull String provider) {
                    updateStatusDisplay("GPS disabled - Please enable");
                    showToastMessage("Please enable GPS in settings");
                }
            };

            // Request GPS updates with these parameters:
            // - LocationManager.GPS_PROVIDER: Use the GPS hardware (not network)
            // - 10000: Update at minimum every 10 seconds (10000 milliseconds)
            // - 50: Or when the device moves at least 50 meters
            // - gpsListener: The listener we created above
            gpsLocationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    10000,      // 10 seconds minimum interval
                    50,         // 50 meters minimum distance
                    gpsListener
            );

            // Show that tracking has started
            updateStatusDisplay("GPS tracking active");
        }
    }

    /**
     * Update the UI text fields with new GPS coordinates
     * Formats the numbers to look clean (6 decimal places)
     */
    private void updateLocationDisplay(Location currentLocation) {
        // Extract location data from the Location object
        double latitude = currentLocation.getLatitude();
        double longitude = currentLocation.getLongitude();
        float accuracy = currentLocation.getAccuracy();  // Accuracy in meters

        // Update text fields with formatted coordinates
        // %.6f means: show 6 decimal places for floating point numbers
        latitudeDisplay.setText(String.format(Locale.US, "Latitude: %.6f°", latitude));
        longitudeDisplay.setText(String.format(Locale.US, "Longitude: %.6f°", longitude));
        accuracyDisplay.setText(String.format(Locale.US, "Accuracy: %.1f meters", accuracy));

        // Show a short toast message with the current coordinates
        String locationToast = String.format(Locale.US, "Location: %.4f, %.4f", latitude, longitude);
        showToastMessage(locationToast);
    }

    /**
     * Send the current location to our PHP backend server
     * Uses Volley to make an HTTP POST request with location data
     */
    private void transmitLocationToServer(Location locationData) {
        // Create a POST request using Volley's StringRequest
        StringRequest postRequest = new StringRequest(
                Request.Method.POST,    // HTTP method
                SERVER_SAVE_URL,        // URL of our PHP API

                // SUCCESS CALLBACK - called when server responds successfully
                serverResponse -> {
                    updateStatusDisplay("Location saved successfully");
                },

                // ERROR CALLBACK - called when network fails
                networkError -> {
                    // Get the error message and show it
                    String errorMessage = "Network error: " + networkError.getMessage();
                    updateStatusDisplay(errorMessage);
                    showToastMessage("Failed to save location");
                }
        ) {
            /**
             * This method provides the POST parameters
             * It's called automatically by Volley before sending the request
             */
            @Override
            protected Map<String, String> getParams() {
                // Create a map to hold key-value pairs for POST data
                Map<String, String> requestParams = new HashMap<>();

                // Format the current date and time for MySQL database
                // MySQL expects format: "YYYY-MM-DD HH:mm:ss"
                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                String currentTime = dateFormatter.format(new Date());

                // Get a unique identifier for this device
                // ANDROID_ID is a unique 64-bit hex string for each device
                String deviceId = Settings.Secure.getString(
                        getContentResolver(),
                        Settings.Secure.ANDROID_ID
                );

                // Add all parameters to the request
                // These match what our PHP API expects
                requestParams.put("user_latitude", String.valueOf(locationData.getLatitude()));
                requestParams.put("user_longitude", String.valueOf(locationData.getLongitude()));
                requestParams.put("recorded_timestamp", currentTime);
                requestParams.put("device_unique_id", deviceId);

                return requestParams;
            }
        };

        // Add the request to Volley's queue for execution
        // Volley will handle sending it asynchronously
        networkRequestQueue.add(postRequest);
    }

    /**
     * Update the status text at the bottom of the screen
     * Shows what's happening (tracking, saved, errors, etc.)
     */
    private void updateStatusDisplay(String statusMessage) {
        statusDisplay.setText("Status: " + statusMessage);
    }

    /**
     * Display a short popup message to the user
     * Toast messages appear briefly and then disappear
     */
    private void showToastMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Called after the user responds to the permission request dialog
     * This is where we handle Allow or Deny responses
     */
    @Override
    public void onRequestPermissionsResult(
            int requestCode,                    // Which permission request this is
            @NonNull String[] permissions,      // The permissions requested
            @NonNull int[] grantResults) {      // User's responses (granted/denied)

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Check if this is our location permission request
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            // Check if the user granted permission
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted! Start tracking GPS
                startGpsTracking();
                showToastMessage("Location permission granted");
            } else {
                // Permission denied - show error message
                showToastMessage("Location permission is required for tracking");
                updateStatusDisplay("Permission denied");
            }
        }
    }
}
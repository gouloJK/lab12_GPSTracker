// Path: app/src/main/java/com/example/lab12_gpstracker/LocationMapActivity.java
// Purpose: Map activity - Displays all tracked GPS locations on OpenStreetMap
// Shows markers for each location and draws connecting paths between them

package com.example.lab12_gpstracker;

// Android core imports
import android.graphics.Color;              // For setting colors on map elements
import android.os.Bundle;                   // For saving activity state
import android.view.View;                   // For view visibility settings
import android.widget.ProgressBar;          // Loading spinner indicator
import android.widget.TextView;             // Text display component
import android.widget.Toast;                // Popup messages

import androidx.appcompat.app.AppCompatActivity;

// Material Design components
import com.google.android.material.appbar.MaterialToolbar;           // Top toolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton; // FAB button

// Volley library for HTTP requests
import com.android.volley.Request;          // HTTP request types
import com.android.volley.RequestQueue;     // Request manager
import com.android.volley.toolbox.JsonObjectRequest;  // For JSON responses
import com.android.volley.toolbox.Volley;   // Creates request queue

// JSON parsing imports - to process server response
import org.json.JSONArray;                  // Array of JSON objects
import org.json.JSONException;              // JSON parsing errors
import org.json.JSONObject;                 // Single JSON object

// OSMDroid imports - FREE OpenStreetMap library (no API key needed!)
import org.osmdroid.config.Configuration;   // Map configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;  // Map tile styles
import org.osmdroid.util.GeoPoint;          // Represents a lat/lon point
import org.osmdroid.views.MapView;          // The map view component
import org.osmdroid.views.overlay.Marker;   // Map markers (pins)
import org.osmdroid.views.overlay.Polyline; // Lines connecting points

// Java utility imports
import java.util.ArrayList;                 // Dynamic list for storing points
import java.util.List;                      // List interface

/**
 * LocationMapActivity - Displays tracked GPS positions on a free OpenStreetMap
 *
 * This activity:
 * 1. Initializes an OpenStreetMap view (completely free, no API key)
 * 2. Fetches all stored GPS locations from the PHP server via GET request
 * 3. Places blue markers on the map for each location
 * 4. Draws connecting lines between consecutive points to show the route
 * 5. Provides a refresh button to reload data from the server
 */
public class LocationMapActivity extends AppCompatActivity {

    // Map components
    private MapView openStreetMapView;           // The main map view
    private FloatingActionButton refreshDataButton;  // Button to reload locations
    private ProgressBar loadingIndicator;        // Shows while data is loading
    private TextView infoTextOverlay;             // Text overlay showing status/info
    private RequestQueue networkRequestQueue;     // Volley queue for HTTP requests

    // Server URL to fetch all stored locations
    // IMPORTANT: Change this IP to your computer's current IP address
    private static final String SERVER_FETCH_URL = "http://192.168.110.210:8080/tracker_api/api/get_locations.php";

    // List to store all GPS points - used for drawing the path
    // GeoPoint is OSMDroid's representation of a latitude/longitude coordinate
    private List<GeoPoint> trackedPointsList = new ArrayList<>();

    /**
     * onCreate - Called when the map activity first opens
     * Sets up the map, loads data from server, and displays markers
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Configure OSMDroid settings
        // setUserAgentValue identifies our app to the tile server
        Configuration.getInstance().setUserAgentValue(getPackageName());
        // Load saved map preferences (zoom level, center position, etc.)
        Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE));

        // Set the layout for this activity
        setContentView(R.layout.activity_location_map);

        // Step 1: Initialize the map and UI elements
        initializeMapInterface();

        // Step 2: Create Volley request queue for fetching data
        networkRequestQueue = Volley.newRequestQueue(this);

        // Step 3: Configure the toolbar back button
        setupToolbarNavigation();

        // Step 4: Fetch location data from server and display on map
        fetchAndDisplayLocations();

        // Step 5: Set refresh button to reload data when clicked
        refreshDataButton.setOnClickListener(view -> fetchAndDisplayLocations());
    }

    /**
     * Initialize all map components and UI elements
     * Finds views from layout and configures the map
     */
    private void initializeMapInterface() {
        // Find UI elements by their IDs
        openStreetMapView = findViewById(R.id.osmMapView);
        refreshDataButton = findViewById(R.id.refreshMapFab);
        loadingIndicator = findViewById(R.id.mapLoadingIndicator);
        infoTextOverlay = findViewById(R.id.mapInfoText);

        // Configure map appearance and behavior
        configureMapSettings();
    }

    /**
     * Configure the OpenStreetMap settings
     * Sets tile source, zoom levels, touch controls, etc.
     */
    private void configureMapSettings() {
        // Use MAPNIK tiles - the standard OpenStreetMap style
        // This is a clean, detailed map style (like the main OSM website)
        openStreetMapView.setTileSource(TileSourceFactory.MAPNIK);

        // Enable pinch-to-zoom and other multi-touch gestures
        openStreetMapView.setMultiTouchControls(true);

        // Enable hardware acceleration for smoother map rendering
        // This uses the GPU instead of CPU for graphics
        openStreetMapView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // Show built-in zoom controls (+ and - buttons)
        openStreetMapView.setBuiltInZoomControls(true);

        // Set zoom limits
        // 3.0 = zoomed out (continental view)
        // 19.0 = zoomed in (building level detail)
        openStreetMapView.setMinZoomLevel(3.0);
        openStreetMapView.setMaxZoomLevel(19.0);

        // Set default zoom level (15 shows street-level detail)
        openStreetMapView.getController().setZoom(15.0);

        // Allow map to download tiles from internet
        openStreetMapView.setUseDataConnection(true);

        // Scale tiles to device DPI for crisp display
        openStreetMapView.setTilesScaledToDpi(true);

        // Set a default center point (Paris, France)
        // This will be updated when location data loads
        // Latitude: 48.8566, Longitude: 2.3522
        GeoPoint defaultCenter = new GeoPoint(48.8566, 2.3522);
        openStreetMapView.getController().setCenter(defaultCenter);
    }

    /**
     * Configure the toolbar's back button
     * When clicked, it goes back to the main activity
     */
    private void setupToolbarNavigation() {
        MaterialToolbar toolbar = findViewById(R.id.mapToolbar);
        if (toolbar != null) {
            // Set click listener on navigation icon (back arrow)
            // finish() closes this activity and returns to previous one
            toolbar.setNavigationOnClickListener(view -> finish());
        }
    }

    /**
     * Fetch all GPS locations from the PHP server
     * Makes a GET request to get_location.php endpoint
     * Response is JSON format: {"success": true, "location_data": [...]}
     */
    private void fetchAndDisplayLocations() {
        // Show loading spinner while data is being fetched
        loadingIndicator.setVisibility(View.VISIBLE);
        infoTextOverlay.setText("Loading locations...");

        // Clear previous points before loading new data
        trackedPointsList.clear();

        // Create a GET request expecting JSON response
        JsonObjectRequest fetchRequest = new JsonObjectRequest(
                Request.Method.GET,      // HTTP GET method
                SERVER_FETCH_URL,        // Our PHP API URL
                null,                    // No request body for GET

                // SUCCESS CALLBACK - called when server responds with data
                jsonResponse -> {
                    try {
                        // Check if the response contains success flag
                        if (jsonResponse.getBoolean("success")) {
                            // Extract the array of location data
                            JSONArray locationsArray = jsonResponse.getJSONArray("location_data");

                            // Process and display all locations on the map
                            processLocationData(locationsArray);

                            // Update info text with count
                            int locationCount = locationsArray.length();
                            infoTextOverlay.setText("Showing " + locationCount + " locations");
                        } else {
                            showErrorMessage("Server returned error");
                        }
                    } catch (JSONException jsonError) {
                        // Handle JSON parsing errors
                        showErrorMessage("Failed to parse data");
                        jsonError.printStackTrace();
                    }
                    // Hide loading spinner when done
                    loadingIndicator.setVisibility(View.GONE);
                },

                // ERROR CALLBACK - called when network request fails
                networkError -> {
                    showErrorMessage("Network error: " + networkError.getMessage());
                    loadingIndicator.setVisibility(View.GONE);
                }
        );

        // Add request to Volley queue for execution
        networkRequestQueue.add(fetchRequest);
    }

    /**
     * Process the JSON location data and add markers to the map
     * Each location becomes a blue marker with timestamp information
     *
     * @param locations JSONArray containing all location objects
     */
    private void processLocationData(JSONArray locations) throws JSONException {
        // Clear all existing markers and overlays from the map
        // This prevents duplicate markers when refreshing
        openStreetMapView.getOverlays().clear();

        // Loop through each location in the array
        for (int index = 0; index < locations.length(); index++) {
            // Get one location object from the array
            JSONObject locationData = locations.getJSONObject(index);

            // Extract coordinates and timestamp from JSON
            double latitude = locationData.getDouble("gps_latitude");
            double longitude = locationData.getDouble("gps_longitude");
            String timestamp = locationData.getString("timestamp_recorded");

            // Create a GeoPoint (lat/lon coordinate) for this location
            GeoPoint mapPoint = new GeoPoint(latitude, longitude);

            // Add to our list for drawing the path later
            trackedPointsList.add(mapPoint);

            // Create a marker (pin) for this location
            Marker locationMarker = new Marker(openStreetMapView);
            locationMarker.setPosition(mapPoint);                        // Where to place it
            locationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);  // Anchor point
            locationMarker.setTitle("Location #" + (index + 1));        // Title when tapped
            locationMarker.setSnippet("Time: " + timestamp);            // Subtitle when tapped

            // Add the marker to the map
            openStreetMapView.getOverlays().add(locationMarker);
        }

        // If we have at least 2 points, draw a line connecting them
        // This shows the route/path the device traveled
        if (trackedPointsList.size() > 1) {
            drawConnectionPath();
        }

        // If we have at least one location, center the map on it
        if (!trackedPointsList.isEmpty()) {
            // Center on the first (newest) location
            openStreetMapView.getController().setCenter(trackedPointsList.get(0));

            // Refresh the map to show changes
            openStreetMapView.invalidate();

            // Show success message
            showToastMessage("Loaded " + locations.length() + " positions");
        } else {
            // No data available
            infoTextOverlay.setText("No location data available");
        }
    }

    /**
     * Draw a connecting path between all tracked points
     * This creates a visual route showing where the device traveled
     * Uses a semi-transparent blue line
     */
    private void drawConnectionPath() {
        // Create a polyline (line made of multiple points)
        Polyline routePath = new Polyline();

        // Set all the points that make up the path
        routePath.setPoints(trackedPointsList);

        // Set the line color: B2 = 70% opacity, 1565C0 = blue
        // Alpha values: FF=100%, B2=70%, 80=50%, 33=20%
        routePath.setColor(Color.parseColor("#B21565C0"));

        // Set line width in pixels
        routePath.setWidth(8.0f);

        // Add the path to the map
        openStreetMapView.getOverlays().add(routePath);
    }

    /**
     * Show error message on the info overlay and as a toast
     * @param message The error message to display
     */
    private void showErrorMessage(String message) {
        infoTextOverlay.setText("Error: " + message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /**
     * Show a short toast message
     * @param message The message to display
     */
    private void showToastMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * onResume - Called when activity becomes visible
     * Required for OSMDroid to work properly
     */
    @Override
    protected void onResume() {
        super.onResume();
        openStreetMapView.onResume();  // Must call this for map to work
    }

    /**
     * onPause - Called when activity is no longer visible
     * Required for OSMDroid to work properly
     */
    @Override
    protected void onPause() {
        super.onPause();
        openStreetMapView.onPause();  // Must call this to release resources
    }
}
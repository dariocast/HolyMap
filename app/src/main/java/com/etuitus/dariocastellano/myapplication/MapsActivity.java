package com.etuitus.dariocastellano.myapplication;

import android.graphics.Color;
import android.os.AsyncTask;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.res.ResourcesCompat;
import android.util.Log;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.google.android.gms.maps.model.SquareCap;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {
    public static final String PROTOCOL = "http";
    public static final String HOST_IP = "192.168.50.28";
    public static final String HOST_PORT = "8080";
    private OkHttpClient client;
    private JSONArray resultJson = null;


    private GoogleMap mMap;
    private double latTesta, longTesta, latCoda, longCoda;
    private String testaMarkerName, codaMarkerName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(100, TimeUnit.SECONDS)
                .readTimeout(100, TimeUnit.SECONDS)
                .build();

        RequestBody bodyTesta = new FormBody.Builder()
                .add("tipo", "testa")
                .build();
        Request requestTesta = new Request.Builder()
                .url(PROTOCOL+"://"+HOST_IP+"/CoordPost/coords_getter.php")
                .post(bodyTesta)
                .addHeader("cache-control", "no-cache")
                .build();

        RequestBody bodyCoda = new FormBody.Builder()
                .add("tipo", "coda")
                .build();
        Request requestCoda = new Request.Builder()
                .url(PROTOCOL+"://"+HOST_IP+"/CoordPost/coords_getter.php")
                .post(bodyCoda)
                .addHeader("cache-control", "no-cache")
                .build();

        try {
            resultJson = new Task().execute(requestTesta).get();
            this.latTesta = resultJson.getJSONObject(0).getDouble("lat");
            this.longTesta = resultJson.getJSONObject(0).getDouble("long");
            this.testaMarkerName = resultJson.getJSONObject(0).getString("location_name");
            resultJson = new Task().execute(requestCoda).get();
            this.latCoda = resultJson.getJSONObject(0).getDouble("lat");
            this.longCoda = resultJson.getJSONObject(0).getDouble("long");
            this.codaMarkerName = resultJson.getJSONObject(0).getString("location_name");

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        // Add a marker in server's coords and move the camera
        LatLng coordsTesta = new LatLng(this.latTesta, this.longTesta);
        mMap.addMarker(new MarkerOptions().position(coordsTesta).title(this.testaMarkerName));
        LatLng coordsCoda = new LatLng(this.latCoda, this.longCoda);
        mMap.addMarker(new MarkerOptions().position(coordsCoda).title(this.codaMarkerName));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(coordsTesta));
        mMap.moveCamera(CameraUpdateFactory.zoomTo(17.0f));

        // Getting URL to the Google Directions API
        String url = getDirectionsUrl(coordsCoda, coordsTesta);

        DownloadTask downloadTask = new DownloadTask();

        // Start downloading json data from Google Directions API
        downloadTask.execute(url);
    }

    private class DownloadTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... url) {

            String data = "";

            try {
                data = downloadUrl(url[0]);
            } catch (Exception e) {
                Log.d("Background Task", e.toString());
            }
            return data;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            ParserTask parserTask = new ParserTask();


            parserTask.execute(result);

        }
    }

    private class ParserTask extends AsyncTask<String, Integer, List<List<HashMap<String, String>>>> {

        // Parsing the data in non-ui thread
        @Override
        protected List<List<HashMap<String, String>>> doInBackground(String... jsonData) {

            JSONObject jObject;
            List<List<HashMap<String, String>>> routes = null;

            try {
                jObject = new JSONObject(jsonData[0]);
                DirectionsJSONParser parser = new DirectionsJSONParser();

                routes = parser.parse(jObject);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return routes;
        }

        @Override
        protected void onPostExecute(List<List<HashMap<String, String>>> result) {
            ArrayList points = null;
            PolylineOptions lineOptions = null;
            MarkerOptions markerOptions = new MarkerOptions();

            for (int i = 0; i < result.size(); i++) {
                points = new ArrayList();
                lineOptions = new PolylineOptions();

                List<HashMap<String, String>> path = result.get(i);

                for (int j = 0; j < path.size(); j++) {
                    HashMap<String, String> point = path.get(j);

                    double lat = Double.parseDouble(point.get("lat"));
                    double lng = Double.parseDouble(point.get("lng"));
                    LatLng position = new LatLng(lat, lng);

                    points.add(position);
                }

                lineOptions.addAll(points);
                lineOptions.width(18);
                lineOptions.color(Color.parseColor("#55FFFF00"));
                lineOptions.geodesic(true);
                lineOptions.endCap(new RoundCap());
            }

// Drawing polyline in the Google Map for the i-th route
            mMap.addPolyline(lineOptions);
        }
    }

    private String getDirectionsUrl(LatLng origin, LatLng dest) {

        // Origin of route
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;

        // Destination of route
        String str_dest = "destination=" + dest.latitude + "," + dest.longitude;

        // Sensor enabled
        String sensor = "sensor=false";
        String mode = "mode=driving";
        // Building the parameters to the web service
        String parameters = str_origin + "&" + str_dest + "&" + sensor + "&" + mode;

        // Output format
        String output = "json";

        // Building the url to the web service
        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters;


        return url;
    }

    private String downloadUrl(String strUrl) throws IOException {
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(strUrl);

            urlConnection = (HttpURLConnection) url.openConnection();

            urlConnection.connect();

            iStream = urlConnection.getInputStream();

            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));

            StringBuffer sb = new StringBuffer();

            String line = "";
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

            data = sb.toString();

            br.close();

        } catch (Exception e) {
            Log.d("Exception", e.toString());
        } finally {
            iStream.close();
            urlConnection.disconnect();
        }
        return data;
    }



    private class Task extends AsyncTask<Request, Void, JSONArray> {

        @Override
        protected JSONArray doInBackground(Request... request) {
            Response response = null;
            JSONArray newjson = null;
            if (!isCancelled() && request != null) {
                try {
                    response = client.newCall(request[0]).execute();
                    if (response == null) {
                        throw new IOException("No response received.");
                    }
                    newjson = new JSONArray(response.body().string().toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return newjson;
        }
    }
}

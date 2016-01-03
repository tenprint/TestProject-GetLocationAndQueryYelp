package com.lipata.whatsforlunch;

import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.gson.Gson;
import com.lipata.whatsforlunch.api.yelp.YelpAPI;
import com.lipata.whatsforlunch.data.yelppojo.Business;
import com.lipata.whatsforlunch.data.yelppojo.YelpResponse;

import java.util.List;

/**
 *  Quick and dirty test project that gets device location, queries the Yelp API, and uses
 *  GSON to parse and display the response.
 */

public class MainActivity extends AppCompatActivity
        implements ConnectionCallbacks, OnConnectionFailedListener, LocationListener {

    // Constants
    static final String SEARCH_TERM = "restaurants";
    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    static final double LOCATION_LIFESPAN = 10000; // "Age" of location data in milliseconds before it becomes "stale"
    static final String LOCATION_UPDATE_TIMESTAMP_KEY = "mLocationUpdateTimestamp";

    // Location stuff
    protected GoogleApiClient mGoogleApiClient;
    protected Location mLastLocation;
    private LocationRequest mLocationRequest;
    long mLocationUpdateTimestamp; // in milliseconds

    // Views
    protected TextView mTextView_Latitude;
    protected TextView mTextView_Longitude;
    protected TextView mTextView_Accuracy;
    //protected TextView mTextView_Other;
    protected RecyclerView mRecyclerView_suggestionList;
    private RecyclerView.LayoutManager mSuggestionListLayoutManager;
    private RecyclerView.Adapter mSuggestionListAdapter;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mTextView_Latitude = (TextView) findViewById((R.id.latitude_text));
        mTextView_Longitude = (TextView) findViewById((R.id.longitude_text));
        mTextView_Accuracy = (TextView) findViewById(R.id.accuracy_text);

        // RecyclerView
        mRecyclerView_suggestionList = (RecyclerView) findViewById(R.id.suggestion_list);
        mRecyclerView_suggestionList.setHasFixedSize(true);
        mSuggestionListLayoutManager = new LinearLayoutManager(this);
        mRecyclerView_suggestionList.setLayoutManager(mSuggestionListLayoutManager);

       // mTextView_Other = (TextView) findViewById((R.id.otherlocationdata_text));

        if (savedInstanceState!=null){
            mLocationUpdateTimestamp = savedInstanceState.getLong(LOCATION_UPDATE_TIMESTAMP_KEY);
        }

        buildGoogleApiClient();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(LOG_TAG, "isLocationStale() " + isLocationStale()); // testing
            }
        });
    }

    // Activity lifecycle
    @Override
    protected void onStart() {
        super.onStart();
        boolean isLocationStale = isLocationStale();
        Log.d(LOG_TAG, "onStart()... isLocationStale() = " + isLocationStale);
        if(isLocationStale) {
            mGoogleApiClient.connect(); // Calling this at onStart() as per Google API documentation
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(LOG_TAG, "onPause");
        if (mGoogleApiClient.isConnected()) {
            stopLocationUpdates();
            mGoogleApiClient.disconnect();
        }
    }

    // Is this redundant? I'm already doing this in onPause().  However the API doc says to always
    // call disconnect() in onStop()
    @Override
    protected void onStop() {
        super.onStop();
        Log.d(LOG_TAG, "onStop()");
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }


    // Callback method for Google Play Services
    @Override
    public void onConnected(Bundle connectionHint) {
        Log.d(LOG_TAG, "onConnected()");

        updateLocationData();

        // If getLastLocation() returned null, start a Location Request to get device location
        // Otherwise, query yelp with existing location arguments
        if (mLastLocation == null) {
            Log.d(LOG_TAG, "Creating LocationRequest...");
            Toast.makeText(this, "Getting location...", Toast.LENGTH_SHORT).show();

            mLocationRequest = LocationRequest.create()
                        .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                        .setInterval(10 * 1000)        // 10 seconds, in milliseconds
                        .setFastestInterval(1 * 1000); // 1 second, in milliseconds

            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);

        } else {
            String ll = mLastLocation.getLatitude() + "," + mLastLocation.getLongitude() + "," + mLastLocation.getAccuracy();
            Log.d(LOG_TAG, "Querying Yelp... ll = " + ll + " Search term: " + SEARCH_TERM);
            new YelpAsyncTask(ll, SEARCH_TERM).execute();
        }
    }

    // Callback method for Google Play Services
    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Refer to the javadoc for ConnectionResult to see what error codes might be returned in
        // onConnectionFailed.

        int errorCode = result.getErrorCode();

        Log.i(LOG_TAG, "Connection failed: ConnectionResult.getErrorCode() = " + errorCode);

        switch (errorCode){
            case 2:
                Toast.makeText(MainActivity.this,
                        "ERROR: The installed version of Google Play services is out of date.",
                        Toast.LENGTH_LONG).show();
                break;

        }

    }

    // Callback method for Google Play Services
    @Override
    public void onConnectionSuspended(int cause) {
        // The connection to Google Play services was lost for some reason. We call connect() to
        // attempt to re-establish the connection.
        Log.i(LOG_TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }

    // Callback method for LocationRequest
    @Override
    public void onLocationChanged(Location location) {
        Log.d(LOG_TAG, "Location Changed");
        updateLocationData();
    }

    // Yelp stuff
    private class YelpAsyncTask extends AsyncTask<String, Void, String> {

        YelpAPI yelpApi = new YelpAPI(ApiKeys.CONSUMER_KEY, ApiKeys.CONSUMER_SECRET, ApiKeys.TOKEN, ApiKeys.TOKEN_SECRET);
        String userLocation;
        String userSearch;

        public YelpAsyncTask(String userLocation, String userSearch) {
            this.userLocation = userLocation;
            this.userSearch = userSearch;
        }

        @Override
        protected String doInBackground(String... strings) {
            return yelpApi.searchForBusinessesByLocation(userSearch, userLocation);
        }

        @Override
        protected void onPostExecute(String yelpResponse_Json) {
            super.onPostExecute(yelpResponse_Json);
            Log.d(LOG_TAG, yelpResponse_Json);
            parseYelpResponse(yelpResponse_Json);
        }
    }

    // Helper methods
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    private boolean isLocationStale(){
        long currentTime = SystemClock.elapsedRealtime();
        Log.d(LOG_TAG, "currentTime = " + currentTime);
        Log.d(LOG_TAG, "mLocationUpdateTimestamp = " + mLocationUpdateTimestamp);

        if ((currentTime - mLocationUpdateTimestamp) > LOCATION_LIFESPAN){
            return true;
        } else {
            return false;}
    }

    private void updateLocationData(){
        Log.d(LOG_TAG, "updateLocationData()...");

        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        mLocationUpdateTimestamp = SystemClock.elapsedRealtime();
        Log.d(LOG_TAG, "mLocationUpdateTimestamp = " + mLocationUpdateTimestamp);

        if (mLastLocation != null) {
            double latitude = mLastLocation.getLatitude();
            double longitude = mLastLocation.getLongitude();
            float accuracy = mLastLocation.getAccuracy();
            Log.d(LOG_TAG, "Success " + latitude + ", " + longitude + ", " + accuracy);
            updateLocationViews(latitude, longitude, accuracy);
            stopLocationUpdates();
        } else {
            Log.d(LOG_TAG, "mLastLocation = null");
        }
    }

    private void updateLocationViews(double latitude, double longitude, float accuracy){
        mTextView_Latitude.setText(Double.toString(latitude));
        mTextView_Longitude.setText(Double.toString(longitude));
        mTextView_Accuracy.setText(Float.toString(accuracy) + " meters");
        Toast.makeText(this, "Location Data Updated", Toast.LENGTH_SHORT).show();
    }

    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        Log.d(LOG_TAG, "Location Updates Stopped");
    }

    void parseYelpResponse(String yelpResponse_Json){
        Log.d(LOG_TAG, "parseYelpResponse()");
        Gson gson = new Gson();
        YelpResponse yelpResponsePojo = gson.fromJson(yelpResponse_Json, YelpResponse.class);
        List<Business> businesses = yelpResponsePojo.getBusinesses();
        mSuggestionListAdapter = new SuggestionListAdapter(businesses, this);
        mRecyclerView_suggestionList.setAdapter(mSuggestionListAdapter);
    }


    // MainActivity template menu override methods
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // Retain Activity state
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState){
        savedInstanceState.putLong(LOCATION_UPDATE_TIMESTAMP_KEY, mLocationUpdateTimestamp);
        super.onSaveInstanceState(savedInstanceState);

    }

}

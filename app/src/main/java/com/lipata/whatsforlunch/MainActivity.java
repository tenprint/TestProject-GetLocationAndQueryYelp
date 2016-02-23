package com.lipata.whatsforlunch;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
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
import com.lipata.whatsforlunch.api.yelp.AsyncYelpCall;
import com.lipata.whatsforlunch.data.user.UserRecords;
import com.lipata.whatsforlunch.data.yelppojo.Business;
import com.lipata.whatsforlunch.data.yelppojo.YelpResponse;

import java.util.List;

/**
 *  This Android app gets device location, queries the Yelp API for restaurant recommendations,
 *  and uses GSON to parse and display the response.
 */

public class MainActivity extends AppCompatActivity
        implements ConnectionCallbacks, OnConnectionFailedListener, LocationListener {

    // Constants
    static final String SEARCH_TERM = "restaurants"; // This should not be user-definable at this time
    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    static final double LOCATION_LIFESPAN = 10000; // "Age" of location data in milliseconds before it becomes "stale"
    static final String LOCATION_UPDATE_TIMESTAMP_KEY = "mLocationUpdateTimestamp";
    static final int MY_PERMISSIONS_ACCESS_FINE_LOCATION_ID = 0;

    // Location stuff
    protected GoogleApiClient mGoogleApiClient;
    protected Location mLastLocation;
    private LocationRequest mLocationRequest;
    long mLocationUpdateTimestamp; // in milliseconds

    // Views
    protected CoordinatorLayout mCoordinatorLayout;
    protected TextView mTextView_Latitude;
    protected TextView mTextView_Longitude;
    protected TextView mTextView_Accuracy;
    protected RecyclerView mRecyclerView_suggestionList;
    private RecyclerView.LayoutManager mSuggestionListLayoutManager;
    private RecyclerView.Adapter mSuggestionListAdapter;
    private SwipeRefreshLayout mSwipeRefreshLayout;

    UserRecords mUserRecords;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mUserRecords = new UserRecords(this);

        mCoordinatorLayout = (CoordinatorLayout) findViewById(R.id.layout_coordinator);
        mTextView_Latitude = (TextView) findViewById((R.id.latitude_text));
        mTextView_Longitude = (TextView) findViewById((R.id.longitude_text));
        mTextView_Accuracy = (TextView) findViewById(R.id.accuracy_text);

        // RecyclerView
        mRecyclerView_suggestionList = (RecyclerView) findViewById(R.id.suggestion_list);
        mRecyclerView_suggestionList.setHasFixedSize(true);
        mSuggestionListLayoutManager = new LinearLayoutManager(this);
        mRecyclerView_suggestionList.setLayoutManager(mSuggestionListLayoutManager);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipeRefreshLayout);
        mSwipeRefreshLayout.setColorSchemeColors(R.color.colorPrimary);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                Log.d(LOG_TAG, "Pulldown refresh.  onRefresh()");
                if(isLocationStale()) {
                    executeSequence();
                } else {
                    Toast.makeText(MainActivity.this, "Too soon. Please try again in a few seconds...", Toast.LENGTH_SHORT).show();
                }
                mSwipeRefreshLayout.setRefreshing(false);
            }
        });

        if (savedInstanceState!=null){
            mLocationUpdateTimestamp = savedInstanceState.getLong(LOCATION_UPDATE_TIMESTAMP_KEY);
        }

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10 * 1000)        // 10 seconds, in milliseconds
                .setFastestInterval(1 * 1000); // 1 second, in milliseconds
    }

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
//        if (mGoogleApiClient.isConnected()) {
//            stopLocationUpdates();
//            mGoogleApiClient.disconnect();
//        }

    }

    // Is stopping location updates and disconnecting redundant? I'm already doing this in onPause().  However the API doc says to always
    // call disconnect() in onStop().  UPDATE -- I've decided to only stopLocationUpdates() and disconnect at onStop()
    @Override
    protected void onStop() {
        super.onStop();
        Log.d(LOG_TAG, "onStop()");
        if (mGoogleApiClient.isConnected()) {
            stopLocationUpdates();
            mGoogleApiClient.disconnect();
        }
    }

    // Callback method for Google Play Services
    @Override
    public void onConnected(Bundle connectionHint) {
        Log.d(LOG_TAG, "onConnected()");
        executeSequence();
    }

    void executeSequence(){
        getLocation();
        // If getLastLocation() returned null, start a Location Request to get device location
        // Else, query yelp with existing location arguments
        if (mLastLocation == null || isLocationStale()) {
            requestLocationData();
        } else {
            String ll = mLastLocation.getLatitude() + "," + mLastLocation.getLongitude() + "," + mLastLocation.getAccuracy();
            Log.d(LOG_TAG, "Querying Yelp... ll = " + ll + " Search term: " + SEARCH_TERM);
            new AsyncYelpCall(ll, SEARCH_TERM, this).execute();
        }
    }

    // Callback for Marshmallow requestPermissions() response
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_ACCESS_FINE_LOCATION_ID: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    if(mGoogleApiClient.isConnected()) {
                        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
                    } else {
                        mGoogleApiClient.connect();
                    }

                } else {
                    Snackbar.make(mCoordinatorLayout, "Location Permission Required", Snackbar.LENGTH_LONG);                }
                return;
            }
        }
    }

    // Callback method for Google Play Services
    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Refer to the javadoc for ConnectionResult to see what error codes might be returned in
        // onConnectionFailed.
        // https://developers.google.com/android/reference/com/google/android/gms/common/ConnectionResult

        int errorCode = result.getErrorCode();

        Log.i(LOG_TAG, "GoogleApiClient Connection failed: ConnectionResult.getErrorCode() = " + errorCode);

        switch (errorCode){
            case 1:
                Snackbar.make(mCoordinatorLayout,
                        "ERROR: Google Play services is missing on this device",
                        Snackbar.LENGTH_INDEFINITE).show();
                break;
            case 2:
                Snackbar.make(mCoordinatorLayout,
                        "ERROR: The installed version of Google Play services is out of date.",
                        Snackbar.LENGTH_INDEFINITE).show();
                break;
            default:
                Snackbar.make(mCoordinatorLayout,
                        "ERROR: Google API Client, error code: " + errorCode,
                        Snackbar.LENGTH_INDEFINITE).show();
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
        getLocation();
    }

    // Helper methods
    private boolean isLocationStale(){
        long currentTime = SystemClock.elapsedRealtime();
        Log.d(LOG_TAG, "currentTime = " + currentTime);
        Log.d(LOG_TAG, "mLocationUpdateTimestamp = " + mLocationUpdateTimestamp);

        if ((currentTime - mLocationUpdateTimestamp) > LOCATION_LIFESPAN){
            return true;
        } else {
            return false;}
    }

    private void getLocation(){
        Log.d(LOG_TAG, "getLocation()...");

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

    private void requestLocationData(){

        Log.d(LOG_TAG, "Creating LocationRequest...");
        Toast.makeText(this, "Getting location...", Toast.LENGTH_SHORT).show();

        // Check for Location permission
        boolean isPermissionMissing = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED;
        Log.d(LOG_TAG, "isPermissionMissing = " + isPermissionMissing);

        if(isPermissionMissing) {
            // If permission is missing, we need to ask for it.  See onRequestPermissionResult() callback
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_ACCESS_FINE_LOCATION_ID);
        } else {

            // Else, permission has already been granted.  Proceed with requestLocationUpdates...
            if(mGoogleApiClient.isConnected()) {
                LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            } else {
                mGoogleApiClient.connect();
            }
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

    // Public methods

    // I made this `public` in order for it to be called from api.yelp.AsyncYelpCall.class  Is there a better way?
    public void parseYelpResponse(String yelpResponse_Json){
        Log.d(LOG_TAG, "parseYelpResponse()");
        Gson gson = new Gson();
        YelpResponse yelpResponsePojo = gson.fromJson(yelpResponse_Json, YelpResponse.class);
        List<Business> businesses = yelpResponsePojo.getBusinesses();

        // Manipulate `businesses` to apply customization
        BusinessListFilter businessListFilter = new BusinessListFilter(businesses, this, mUserRecords);
        List<Business> filteredBusinesses = businessListFilter.filter();

        mSuggestionListAdapter = new BusinessListAdapter(filteredBusinesses, this, mCoordinatorLayout, mUserRecords, businessListFilter);
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
    // TODO: Retain recyclerview state
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState){
        savedInstanceState.putLong(LOCATION_UPDATE_TIMESTAMP_KEY, mLocationUpdateTimestamp);
        super.onSaveInstanceState(savedInstanceState);

    }

}

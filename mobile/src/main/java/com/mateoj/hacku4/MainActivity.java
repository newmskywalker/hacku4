package com.mateoj.hacku4;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;

import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.parse.FindCallback;
import com.parse.ParseException;
import com.parse.ParseGeoPoint;
import com.parse.ParseQuery;

import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends LocationActivity implements MyRecyclerViewAdapter.MyClickListener,
        ResultCallback {

    public static String TAG = MainActivity.class.getSimpleName();
    public static final String PREFS_APP = "appPreferences";
    public static final String KEY_LAUNCHED = "appLaunched";
    private PendingIntent mGeofencePendingIntent;
    private StringPreference firstLaunchedPref;
    private RecyclerView mRecyclerView;
    private MyRecyclerViewAdapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private boolean isQueryInProgress = false;
    private boolean needsData = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mRecyclerView = (RecyclerView) findViewById(R.id.my_recycler_view);
        mRecyclerView.setHasFixedSize(true);
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mAdapter = new MyRecyclerViewAdapter(getDataSet());
        mAdapter.setOnItemClickListener(this);
        mRecyclerView.setAdapter(mAdapter);
        RecyclerView.ItemDecoration itemDecoration =
                new DividerItemDecoration(this, LinearLayoutManager.VERTICAL);
        mRecyclerView.addItemDecoration(itemDecoration);

        init();
    }

    private Geofence buildFenceFromBuilding(Building building) {
        return new Geofence.Builder()
                .setRequestId(building.getObjectId())
                .setCircularRegion(building.getLocation().getLatitude(),
                        building.getLocation().getLongitude(), 100)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .build();
    }


    @Override
    public void onResult(Result result) {
        Log.d(TAG, result.toString());
    }


    private PendingIntent getGeofencePendingIntent() {
        // Reuse the PendingIntent if we already have it.
        if (mGeofencePendingIntent != null) {
            return mGeofencePendingIntent;
        }
        Intent intent = new Intent(this, GeofenceTransitionsIntentService.class);
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when
        // calling addGeofences() and removeGeofences().
        mGeofencePendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.
                FLAG_UPDATE_CURRENT);

        return mGeofencePendingIntent;
    }

    private GeofencingRequest getGeoFencingRequest(List<Geofence> geofences)
    {
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
        builder.addGeofences(geofences);
        return builder.build();
    }

    private void init() {
        SharedPreferences sp = getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE);
        firstLaunchedPref = new StringPreference(sp, KEY_LAUNCHED);
    }

    @Override
    public void onConnected(Bundle bundle) {
        super.onConnected(bundle);
        if (!firstLaunchedPref.isSet()) {
            setUpGeofences();
            firstLaunchedPref.set("yes");
        }
    }
    private void setUpGeofences() {
        ParseQuery<Building> query = ParseQuery.getQuery("Building");
        query.findInBackground(new FindCallback<Building>() {
            @Override
            public void done(List<Building> objects, ParseException e) {
                List<Geofence> geofences = new ArrayList<>();

                for (Building building : objects) {
                    geofences.add(buildFenceFromBuilding(building));
                }
                LocationServices.GeofencingApi.addGeofences(
                        mGoogleApiClient,
                        getGeoFencingRequest(geofences),
                        getGeofencePendingIntent()
                ).setResultCallback(MainActivity.this);
            }
        });
    }



    private ArrayList<Event> getDataSet() {
        ArrayList results = new ArrayList<Event>();

        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> locations = new ArrayList<>();
        ArrayList<String> tags = new ArrayList<>();

        names.add("Kayaking"); names.add("Studying"); names.add("Sucking helium from balloons"); names.add("Party Fun Time!!!"); names.add("Panic over CSCI 301 project");
        locations.add("Matoaka"); locations.add("Swem"); locations.add("Sadler Center"); locations.add("Sunken Garden"); locations.add("McGlothlin Hall");
        tags.add("fun"); tags.add("boring");

        String testDescription = "It's exactly what it sounds like.";

//        Date testDate = new Date();
//        SimpleDateFormat ft = new SimpleDateFormat ("E MM-DD-YYYY 'at' hh:mm:ss a zzz");
        // System.out.println("Current Date: " + ft.format(dNow));

        DateTime testDate = new DateTime();

        for (int i = 0; i < 5; i++) {
            Event obj = new Event();
//            Event obj = new Event("Some Primary Text " + index, "Secondary " + index);
            results.add(i, obj);
        }
        return results;
    }

    @Override
    public void onItemClick(int position, View v) {
        startActivity(DetailActivity.getLaunchIntent(this, mAdapter.getItem(position)));
    }

    @Override
    public void onLocationChanged(Location location) {
        if (isQueryInProgress || !needsData)
            return;

        ParseQuery<Building> buildingQuery = ParseQuery.getQuery(Building.class);

        buildingQuery.whereNear(Building.KEY_LOCATION, new ParseGeoPoint(location.getLatitude(),
                location.getLongitude()));
        isQueryInProgress = true;
        buildingQuery.findInBackground(new FindCallback<Building>() {
            @Override
            public void done(List<Building> objects, ParseException e) {
                mAdapter.clear();
                needsData = false;
                isQueryInProgress = false;
                for (Building building : objects) {
                    ParseQuery<Event> eventQuery = ParseQuery.getQuery(Event.class);
                    eventQuery.whereEqualTo("Location", building);
                    eventQuery.findInBackground(new FindCallback<Event>() {
                        @Override
                        public void done(List<Event> objects, ParseException e) {
                            Log.d("Event", objects.toString());
                            mAdapter.addAll(objects);
                        }
                    });
                }
            }
        });
    }
}
package com.wormz.penumbra;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import com.wormz.penumbra.model.Run;
import com.wormz.penumbra.sql.RunDatabaseHelper;


/**
 * Created by markanthonypanizales on 4/18/15.
 */
public class RunManager {
    private static final String TAG = "RunManager";

    private static final String PREFS_FILE = "runs";
    private static final String PREF_CURRENT_RUN_ID = "RunManager.currentRunId";

    public static final String ACTION_LOCATION = "com.wormz.penumbra.ACTION_LOCATION";

    private static final String TEST_PROVIDER = "TEST_PROVIDER";

    private static RunManager sRunManager;
    private Context mAppContext;
    private LocationManager mLocationManager;
    private RunDatabaseHelper mHelper;
    private SharedPreferences mPrefs;
    private long mCurrentRunId;

    public RunManager(Context appContext) {
        mAppContext = appContext;
        mLocationManager = (LocationManager) mAppContext.getSystemService(Context.LOCATION_SERVICE);
        mHelper = new RunDatabaseHelper(mAppContext);
        mPrefs = mAppContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        mCurrentRunId = mPrefs.getLong(PREF_CURRENT_RUN_ID, -1);
    }

    public static RunManager get(Context context){
        if (sRunManager == null){
            // Use the application context to avoid leaking activities
            sRunManager = new RunManager(context.getApplicationContext());
        }

        return sRunManager;
    }

    private PendingIntent getLocationPendingIntent(boolean shouldCreate){
        Intent broadcast = new Intent(ACTION_LOCATION);
        int flags = shouldCreate ? 0: PendingIntent.FLAG_NO_CREATE;
        return PendingIntent.getBroadcast(mAppContext, 0, broadcast, flags);

    }

    public void startLocationUpdates(){
        String provider = LocationManager.GPS_PROVIDER;
        // If you have the test provider and it's enabled, use it
        if (mLocationManager.getProvider(TEST_PROVIDER) != null && mLocationManager.isProviderEnabled(TEST_PROVIDER)) {
            provider = TEST_PROVIDER;
        }
        Log.d(TAG, "Using provider " + provider);

        // Get the last known location and broadcast it if you have one
        Location lastKnown = mLocationManager.getLastKnownLocation(provider);
        if (lastKnown != null){
            // reset the time to now
            lastKnown.setTime(System.currentTimeMillis());
            broadcastLocation(lastKnown);
        }

        // Start updates from the location manager
        PendingIntent pi = getLocationPendingIntent(true);
        mLocationManager.requestLocationUpdates(provider, 0, 0, pi);
    }

    private void broadcastLocation(Location location){
        Intent broadcast = new Intent(ACTION_LOCATION);
        broadcast.putExtra(LocationManager.KEY_LOCATION_CHANGED, location);
        mAppContext.sendBroadcast(broadcast);
    }

    public Run startNewRun(){
        // Insert a run into db
        Run run = insertRun();
        // Start tracking the run
        startTrackingRun(run);
        return run;
    }

    public void startTrackingRun(Run run){
        // Keep the ID
        mCurrentRunId = run.getId();
        // Store it in shared preferences
        mPrefs.edit().putLong(PREF_CURRENT_RUN_ID, mCurrentRunId).commit();
        // Start location updates
        startLocationUpdates();
    }

    public void stopRun(){
        stopLocationUpdates();
        mCurrentRunId = -1;
        mPrefs.edit().remove(PREF_CURRENT_RUN_ID).commit();
    }

    private Run insertRun(){
        Run run = new Run();
        run.setId(mHelper.insertRun(run));
        return run;
    }

    public RunDatabaseHelper.RunCursor queryRuns(){
        return mHelper.queryRuns();
    }

    public void insertLocation(Location location){
        if (mCurrentRunId != -1){
            mHelper.insertLocation(mCurrentRunId, location);
        }else{
            Log.e(TAG, "Location received with no tracking run; ignoring");
        }

    }

    public void stopLocationUpdates(){
        PendingIntent pi = getLocationPendingIntent(false);
        if (pi != null){
            mLocationManager.removeUpdates(pi);
            pi.cancel();
        }
    }

    public boolean isTrackingRun(){
        return getLocationPendingIntent(false) != null;
    }

    public Run getRun(long id){
        Run run = null;
        RunDatabaseHelper.RunCursor cursor = mHelper.queryRun(id);
        cursor.moveToFirst();
        // If you got a row, get a run
        if (!cursor.isAfterLast())
            run = cursor.getRun();
        cursor.close();
        return run;

    }

    public Run getLastRun(){
        Run run = null;
        RunDatabaseHelper.RunCursor cursor = mHelper.queryRun(mCurrentRunId);
        cursor.moveToFirst();
        // If you got a row, get a run
        if (!cursor.isAfterLast())
            run = cursor.getRun();
        cursor.close();
        return run;

    }

    public boolean isTrackingRun(Run run){
        return run != null && run.getId() == mCurrentRunId;
    }

    public Location getLastLocationForRun(long runId){
        Location location = null;
        RunDatabaseHelper.LocationCursor cursor = mHelper.queryLastLocationForRun(runId);
        cursor.moveToFirst();
        // if you got a row, get a location
        if (!cursor.isAfterLast())
            location = cursor.getLocation();

        cursor.close();
        return location;
    }

    public RunDatabaseHelper.LocationCursor queryLocationsForRun(long runId){
        return mHelper.queryLocationsForRun(runId);
    }
}

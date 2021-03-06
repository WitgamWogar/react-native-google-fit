/**
 * Copyright (c) 2017-present, Stanislav Doskalenko - doskalenko.s@gmail.com
 * All rights reserved.
 *
 * This source code is licensed under the MIT-style license found in the
 * LICENSE file in the root directory of this source tree.
 *
 * Based on Asim Malik android source code, copyright (c) 2015
 *
 **/
package com.reactnative.googlefit;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;


public class GoogleFitManager implements
        ActivityEventListener {

    private ReactContext mReactContext;
    private GoogleApiClient mApiClient;
    private static final int REQUEST_OAUTH = 1;
    private static final String AUTH_PENDING = "auth_state_pending";
    private boolean mAuthInProgress = false;
    private Activity mActivity;

    private DistanceHistory distanceHistory;
    private StepHistory stepHistory;
    private WeightsHistory weightsHistory;
    private StepCounter mStepCounter;
    private StepSensor stepSensor;

    private static final String TAG = "RNGoogleFit";

    public GoogleFitManager(ReactContext reactContext, Activity activity) {
        this.mReactContext = reactContext;
        this.mActivity = activity;

        mReactContext.addActivityEventListener(this);

        this.mStepCounter = new StepCounter(mReactContext, this, activity);
        this.stepHistory = new StepHistory(mReactContext, this);
        this.weightsHistory = new WeightsHistory(mReactContext, this);
        this.distanceHistory = new DistanceHistory(mReactContext, this);

//        this.stepSensor = new StepSensor(mReactContext, activity);
    }

    public GoogleApiClient getGoogleApiClient() {
        return mApiClient;
    }

    public StepCounter getStepCounter() {
        return mStepCounter;
    }

    public StepHistory getStepHistory() {
        return stepHistory;
    }

    public WeightsHistory getWeightsHistory() {
        return weightsHistory;
    }

    public DistanceHistory getDistanceHistory() {
        return distanceHistory;
    }

    public void authorize(@Nullable final Boolean checkOnly, final Promise promise) {
        mApiClient = new GoogleApiClient.Builder(mReactContext.getApplicationContext())
                .addApi(Fitness.SENSORS_API)
                .addApi(Fitness.HISTORY_API)
                .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ))
                .addScope(new Scope(Scopes.FITNESS_BODY_READ_WRITE))
                .addScope(new Scope(Scopes.FITNESS_LOCATION_READ))
                .addConnectionCallbacks(
                    new GoogleApiClient.ConnectionCallbacks() {
                        @Override
                        public void onConnected(@Nullable Bundle bundle) {
                            WritableMap map = Arguments.createMap();
                            map.putBoolean("authorized", true);
                            promise.resolve(map);
                        }

                        @Override
                        public void onConnectionSuspended(int i) {
                            if ((mApiClient != null) && (mApiClient.isConnected())) {
                                mApiClient.disconnect();
                            }
                        }
                    }
                )
                .addOnConnectionFailedListener(
                    new GoogleApiClient.OnConnectionFailedListener() {
                        @Override
                        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                            if (mAuthInProgress || (checkOnly != null && checkOnly != false)) {
                                WritableMap map = Arguments.createMap();
                                map.putBoolean("authorized", false);
                                promise.resolve(map);
                            } else {
                                try {
                                    mAuthInProgress = true;
                                    connectionResult.startResolutionForResult(mActivity, REQUEST_OAUTH);
                                } catch (IntentSender.SendIntentException e) {
                                    Log.i(TAG, "Authorization - Failed again: " + e);
                                }
                            }
                        }
                    }
                )
                .build();

        mApiClient.connect();
    }

    public boolean isAuthorize() {
        if (mApiClient != null && mApiClient.isConnected()) {
            return true;
        } else {
            return false;
        }
    }

    protected void stop() {
        Fitness.SensorsApi.remove(mApiClient, mStepCounter)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            mApiClient.disconnect();
                        }
                    }
                });
    }


    private void sendEvent(ReactContext reactContext,
                           String eventName,
                           @Nullable WritableMap params) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }


    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_OAUTH) {
            mAuthInProgress = false;
            if (resultCode == Activity.RESULT_OK) {
                if (!mApiClient.isConnecting() && !mApiClient.isConnected()) {
                    mApiClient.connect();
                }
            }
        }
    }

    @Override
    public void onNewIntent(Intent intent) {

    }
}

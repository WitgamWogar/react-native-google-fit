/**
 * Copyright (c) 2017-present, Tyler Otto - tylerottoaz@gmail.com
 * All rights reserved.
 *
 * This source code is licensed under the MIT-style license found in the
 * LICENSE file in the root directory of this source tree.
 *
 * Based on Asim Malik android source code, copyright (c) 2015
 * Based on Stanislav Doskalenko - doskalenko.s@gmail.com, copyright (c) 2017
 *
 **/

package com.reactnative.googlefit;

import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableNativeArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResult;

import java.text.DateFormat;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;


public class DistanceHistory {

    private ReactContext mReactContext;
    private GoogleFitManager googleFitManager;

    private static final String TAG = "DistanceHistory";

    public DistanceHistory(ReactContext reactContext, GoogleFitManager googleFitManager){
        this.mReactContext = reactContext;
        this.googleFitManager = googleFitManager;
    }

    public ReadableArray aggregateDataByDate(long startTime, long endTime) {
        //Check how much distance were walked and recorded in specified days
        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_DISTANCE_DELTA, DataType.AGGREGATE_DISTANCE_DELTA)
                .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build();

        DataReadResult dataReadResult = Fitness.HistoryApi.readData(googleFitManager.getGoogleApiClient(), readRequest).await(1, TimeUnit.MINUTES);

        WritableArray map = Arguments.createArray();
        if (dataReadResult.getBuckets().size() > 0) {
            for (Bucket bucket : dataReadResult.getBuckets()) {
                List<DataSet> dataSets = bucket.getDataSets();
                for (DataSet dataSet : dataSets) {
                    processDataSet(dataSet, map);
                }
            }
        } else if (dataReadResult.getDataSets().size() > 0) {
            for (DataSet dataSet : dataReadResult.getDataSets()) {
                processDataSet(dataSet, map);
            }
        }

        return map;
    }

    public ReadableMap aggregateDataByActivityList(long startTime, long endTime, ReadableArray activityList) {
        ArrayList activityArrayList = ((ReadableNativeArray) activityList).toArrayList();
        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_DISTANCE_DELTA, DataType.AGGREGATE_DISTANCE_DELTA)
                .aggregate(DataType.TYPE_ACTIVITY_SEGMENT, DataType.AGGREGATE_ACTIVITY_SUMMARY)
                .bucketByActivityType(1, TimeUnit.MILLISECONDS)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build();

        DataReadResult dataReadResult = Fitness.HistoryApi.readData(googleFitManager.getGoogleApiClient(), readRequest).await(1, TimeUnit.MINUTES);

        WritableMap map = Arguments.createMap();
        if (dataReadResult.getBuckets().size() > 0) {
            for (Bucket bucket : dataReadResult.getBuckets()) {
                List<DataSet> dataSets = bucket.getDataSets();
                String bucketActivity = bucket.getActivity();
                if (activityArrayList.contains(bucketActivity)) {
                    for (DataSet dataSet : dataSets) {
                        processActivityDataSet(dataSet, map, bucketActivity);
                    }
                }
            }
        }

        return map;
    }

    private void processDataSet(DataSet dataSet, WritableArray map) {
        Format formatter = new SimpleDateFormat("EEE");
        for (DataPoint dp : dataSet.getDataPoints()) {
            String day = formatter.format(new Date(dp.getStartTime(TimeUnit.MILLISECONDS)));

            for (Field field : dp.getDataType().getFields()) {
                if (field.getName().equals("distance")) {
                    WritableMap distanceMap = Arguments.createMap();
                    distanceMap.putString("day", day);
                    distanceMap.putDouble("startDate", dp.getStartTime(TimeUnit.MILLISECONDS));
                    distanceMap.putDouble("endDate", dp.getEndTime(TimeUnit.MILLISECONDS));
                    distanceMap.putDouble("distance", dp.getValue(field).asFloat());
                    map.pushMap(distanceMap);
                }
            }
        }
    }

    private void processActivityDataSet(DataSet dataSet, WritableMap map, String activity) {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
        df.setTimeZone(tz);
        for (DataPoint dp : dataSet.getDataPoints()) {
            for (Field field : dp.getDataType().getFields()) {
                if (field.getName().equals("distance")) {
                    WritableMap dataMap = Arguments.createMap();
                    dataMap.putDouble("value", dp.getValue(field).asFloat());
                    dataMap.putString("start_time", df.format(new Date(dp.getStartTime(TimeUnit.MILLISECONDS))).toString());
                    dataMap.putString("end_time", df.format(new Date(dp.getEndTime(TimeUnit.MILLISECONDS))).toString());
                    map.putMap(activity, dataMap);
                }
            }
        }
    }
}
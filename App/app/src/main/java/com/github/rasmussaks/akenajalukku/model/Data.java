package com.github.rasmussaks.akenajalukku.model;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by AlWilliam on 10/29/2016.
 */

public class Data implements Parcelable {
    public static final Creator<Data> CREATOR = new Creator<Data>() {
        @Override
        public Data createFromParcel(Parcel in) {
            return new Data(in);
        }

        @Override
        public Data[] newArray(int size) {
            return new Data[size];
        }
    };
    private ArrayList<PointOfInterest> pois = new ArrayList<>();
    private ArrayList<Journey> journeys = new ArrayList<>();

    public Data() {
        // Initialize dummy data
        pois.add(new PointOfInterest(1, new LatLng(58.3824298, 26.7145573), "Baeri ja Jakobi ristmik", "Päris põnev", "http://i.imgur.com/FGCgIB7.jpg"));
        pois.add(new PointOfInterest(2, new LatLng(58.380144, 26.7223035), "Raekoja plats", "Raekoda on cool", "http://i.imgur.com/ewugjb2.jpg"));
        pois.add(new PointOfInterest(3, new LatLng(58.3740385, 26.7071558), "Tartu rongijaam", "Choo choo", "http://i.imgur.com/mRFDWKl.jpg"));
        // Add Journeys associated with newly added pois
        journeys.add(new Journey(1, Arrays.asList(1, 2), "Historical Tartu", "I know journeys", pois));
        journeys.add(new Journey(2, Arrays.asList(2, 3), "Wonderful Adventure", "I have the best journeys", pois));
    }


    protected Data(Parcel in) {
        pois = in.createTypedArrayList(PointOfInterest.CREATOR);
        journeys = in.createTypedArrayList(Journey.CREATOR);
        for (Journey journey : journeys) {
            journey.initialize(pois);
        }
    }

    public ArrayList<PointOfInterest> getPois() {
        return pois;
    }

    public void setPois(ArrayList<PointOfInterest> pois) {
        this.pois = pois;
    }

    public ArrayList<Journey> getJourneys() {
        return journeys;
    }

    public void setJourneys(ArrayList<Journey> journeys) {
        this.journeys = journeys;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(pois);
        dest.writeTypedList(journeys);
    }
}
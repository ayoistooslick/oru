package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * A listened play is only counted once it is long enough to mean something; the
 * API says whether this one qualified.
 */
public class ListenRecord {

    @SerializedName("recorded")
    public boolean recorded;

    @SerializedName("counted_for_discovery")
    public boolean countedForDiscovery;
}

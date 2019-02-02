package com.example.kumar.testapp;

/**
 * Created by Kumar on 2/26/2017.
 */

public class Playlist {
    private String trackName;
    private String artistName;
    private String album;
    private byte[] art;

    public String getTrackName() {
        return trackName;
    }

    public void setTrackName(String trackName) {
        this.trackName = trackName;
    }

    public String getArtistName() {
        return artistName;
    }

    public void setArtistName(String artistName) {
        this.artistName = artistName;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public byte[] getArt() {
        return art;
    }

    public void setArt(byte[] art) {
        this.art = art;
    }
}

package com.example.kumar.testapp;

/**
 * Created by Kumar on 3/7/2017.
 */

public class VotedSong {
    private String songName;
    private String songPath;
    private int vote;

    public String getSongName() {
        return songName;
    }

    public String getSongPath() {
        return songPath;
    }

    public void setSongName(String songName) {
        this.songName = songName;
    }

    public void setSongPath(String songPath) {
        this.songPath = songPath;
    }

    public void setVote(int vote) {
        this.vote = vote;
    }

    public int getVote() {

        return vote;
    }
}

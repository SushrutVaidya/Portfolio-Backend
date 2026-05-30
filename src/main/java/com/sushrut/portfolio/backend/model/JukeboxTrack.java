package com.sushrut.portfolio.backend.model;

public class JukeboxTrack {

    private String title;
    private String game;
    private String audioURL;

    public JukeboxTrack(String title, String game, String audioURL) {
        this.title    = title;
        this.game     = game;
        this.audioURL = audioURL;
    }

    public String getTitle()    { return title; }
    public String getGame()     { return game; }
    public String getAudioURL() { return audioURL; }

    public void setTitle(String title)       { this.title    = title; }
    public void setGame(String game)         { this.game     = game; }
    public void setAudioURL(String audioURL) { this.audioURL = audioURL; }
}

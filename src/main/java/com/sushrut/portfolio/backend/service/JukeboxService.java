package com.sushrut.portfolio.backend.service;

import java.util.List;
import org.springframework.stereotype.Service;
import com.sushrut.portfolio.backend.model.JukeboxTrack;

@Service
public class JukeboxService {

    private static final String BASE = "/audio/";

    private static final List<JukeboxTrack> TRACKS = List.of(
        new JukeboxTrack("Aladeeeeen!",                        "Aladdin (2019)",           BASE + "Aladeeeeen.mp3"),
        new JukeboxTrack("Everybody Wants to Rule the World",  "Josh Gad Cover",           BASE + "EverybodyWantsToRuleTheWorldJoshGad.mp3"),
        new JukeboxTrack("Funkadelic",                         "The Playlist",             BASE + "Funkadelic.mp3"),
        new JukeboxTrack("Lawrie",                             "The Playlist",             BASE + "Lawrie.mp3"),
        new JukeboxTrack("Man Maze Umalun Gele",               "Marathi Classics",         BASE + "ManMazeUmalunGele.mp3"),
        new JukeboxTrack("Mi Morcha Nela Nahi",                "Marathi Classics",         BASE + "miMorchaNelaNahi.mp3")
    );

    public List<JukeboxTrack> getTracks() {
        return TRACKS;
    }
}

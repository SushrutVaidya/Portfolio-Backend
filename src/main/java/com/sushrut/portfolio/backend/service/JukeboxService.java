package com.sushrut.portfolio.backend.service;

import java.util.List;
import org.springframework.stereotype.Service;
import com.sushrut.portfolio.backend.model.JukeboxTrack;

@Service
public class JukeboxService {

    private static final String BASE = "/audio/";

    private static final List<JukeboxTrack> TRACKS = List.of(
        new JukeboxTrack("Welcome to Los Santos",                    "GTA V",               BASE + "WelcomeToLosSantos.mp3"),
        new JukeboxTrack("Soviet Connection",                        "GTA IV",              BASE + "SovietConnection.mp3"),
        new JukeboxTrack("Bury the Light",                           "Devil May Cry 5",     BASE + "BuryTheLight.mp3"),
        new JukeboxTrack("Oioi, Seisyundesuka?",                     "Gintama OST",         BASE + "OioiSeisyundesuka.mp3"),
        new JukeboxTrack("Teme-raaaa!! Soredemo Gintama Tsuitennokaaaa!", "Gintama OST",    BASE + "TemeRaaa.mp3"),
        new JukeboxTrack("Can't Poop in Strange Places",             "Family Guy",          BASE + "CantPoopInStrangePlaces.mp3"),
        new JukeboxTrack("Everybody Wants to Rule the World",        "Josh Gad Cover",      BASE + "EverybodyWantsToRuleTheWorldJoshGad.mp3"),
        new JukeboxTrack("To Be Continued",                          "JoJo's Bizarre Adventure", BASE + "ToBeContinued.mp3")
    );

    public List<JukeboxTrack> getTracks() {
        return TRACKS;
    }
}

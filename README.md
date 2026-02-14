# Portfolio Backend

Spring Boot API serving dynamic stats for portfolio frontend.

## Tech Stack
- Java 17
- Spring Boot 3.x
- Maven

## API

**GET** `/api/stats`

```json
{
  "location": "Hyderabad",
  "game": "Counter Strike 2",
  "currentTime": "5:30",
  "timeStamp": 1234567890,
  "songName": "a banger",
  "songURL": "/audio/Funkadelic.mp3"
}
```

## Features
- Audio rotation every 4 hours (6 slots)
- Static audio files served from `/audio/`
- CORS configured for local dev

## Run
```bash
mvn spring-boot:run
```

Server runs on `http://localhost:8081`

## Config
Edit `application.properties`:
```properties
portfolio.location = Hyderabad
portfolio.game = Counter Strike 2
portfolio.songName = a banger
```

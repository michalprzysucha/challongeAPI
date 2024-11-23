package com.packt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class ChallongeApi {
    private static final String TOURNAMENT_NAME = "CoK";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true);
    private static final String[] HEADERS = new String[] {
            "Authorization",  "<CHALLONGE_API_KEY>",
            "Content-Type", "application/vnd.api+json",
            "Accept", "application/json",
            "Authorization-Type", "v1"
    };

    private static ChallongeApi challongeApi;
    private final Map<String, String> participants = new HashMap<>();

    private ChallongeApi() {}

    public static ChallongeApi newChallongeApi(){
        if(challongeApi != null){
            return challongeApi;
        }
        InputStream input = ChallongeApi.class.getClassLoader().getResourceAsStream("config.json");
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(input))){
            HEADERS[1] = MAPPER.readTree(reader).get("challonge_api_key").asText();
        }
        catch (IOException e){
            System.out.println("Error reading config.json: " + e.getMessage());
        }
        challongeApi = new ChallongeApi();
        return challongeApi;
    }

    private HttpResponse<String> sendRequest(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .headers(HEADERS)
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public String getTournamentId(){
        HttpResponse<String> response;
        try {
            response = sendRequest(URI.create("https://api.challonge.com/v2.1/tournaments.json"));
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        if(response.body().isEmpty()){
            return "";
        }
        try {
            JsonNode root = MAPPER.readTree(response.body());
            var tournaments = root.get("data");
            for (JsonNode tournament : tournaments){
                if (tournament.at("/attributes/name").asText().equals(TOURNAMENT_NAME)){
                    return tournament.get("id").asText();
                }
            }
        } catch (IOException e) {
            System.out.println("Error processing tournaments response: " + e.getMessage());;
        }
        return "";
    }

    private void mapParticipants(JsonNode participantsData){
        for(JsonNode participant : participantsData){
            String id = participant.get("id").asText();
            String name = participant.at("/attributes/name").asText();
            participants.put(id, name);
        }
    }

    private List<Match> getMatchesList(String tournamentId){
        HttpResponse<String> response = getMatchesData(tournamentId);
        List<Match> matches = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(response.body());
            JsonNode matchesData = root.get("data");
            JsonNode participantsData = root.get("included");
            if(participants.isEmpty()){
                mapParticipants(participantsData);
            }
            for (JsonNode match : matchesData){
                if(match.at("/attributes/state").asText().equals("complete")){
                    continue;
                }
                String firstPlayerId = match.at("/relationships/player1/data/id").asText();
                String secondPlayerId = match.at("/relationships/player2/data/id").asText();
                String firstPlayerName = participants.get(firstPlayerId);
                String secondPlayerName = participants.get(secondPlayerId);
                matches.add(new Match(firstPlayerName, secondPlayerName));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return matches;
    }

    private HttpResponse<String> getMatchesData(String tournamentId){
        HttpResponse<String> response;
        try {
            response = sendRequest(URI.create("https://api.challonge.com/v2.1/tournaments/%s/matches.json"
                    .formatted(tournamentId)));
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return response;
    }

    public static void main(String[] args) {
        String tokens = """
                yazsm 1 Michie 1
                PanKK 1.5 Mickez 0.3
                Michie 0.7 PanKK 1
                yazsm 1.5 Mickez 0.3
                Mickez 0.3 Michie 1.5
                PanKK 1 yazsm 1
                yazsm 1 Michie 1
                PanKK 1.5 Mickez 0.3
                Michie 0.7 PanKK 1
                """;
        String[] lines = tokens.split("\\R");
        Arrays.stream(lines)
                .map(String::trim)
                .map(line -> line.split(" "))
                .map(split -> "%s %f %s %f".formatted(split[0], Double.parseDouble(split[1]), split[2], Double.parseDouble(split[3])))
                .forEach(System.out::println);
    }
}

package com.packt;

public record Match(String firstPlayer, String secondPlayer) {
    @Override
    public String toString() {
        return firstPlayer + "-" + secondPlayer;
    }
}

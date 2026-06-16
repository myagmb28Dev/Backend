package com.example.pogun.service.payment;

public enum AiCreditPolicy {
    REQUEST_300(1, 300),
    REQUEST_500(1, 500);

    private final int cost;
    private final int maxCharacters;

    AiCreditPolicy(int cost, int maxCharacters) {
        this.cost = cost;
        this.maxCharacters = maxCharacters;
    }

    public int getCost() {
        return cost;
    }

    public int getMaxCharacters() {
        return maxCharacters;
    }
}

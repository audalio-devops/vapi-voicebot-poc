package br.com.voicebot.domain;

public record CallDispatchedEvent(
    String externalCallId,
    String status,
    String providerName
) {}

package br.com.voicebot.domain;

import java.util.Map;

public record OutboundCallCommand(
    String companyName,
    String targetLandlineNumber,
    String contactPerson,
    String customPitchObjective,
    Map<String, String> metadata
) {}

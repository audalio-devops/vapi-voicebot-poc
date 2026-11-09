package br.com.voicebot.infra.vapi;

import br.com.voicebot.domain.CallDispatchedEvent;
import br.com.voicebot.domain.OutboundCallCommand;
import br.com.voicebot.domain.port.VoiceAgentProviderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class VapiVoiceAgentAdapter implements VoiceAgentProviderPort {

    private static final Logger log = LoggerFactory.getLogger(VapiVoiceAgentAdapter.class);

    private final RestClient restClient;
    private final String phoneNumberId;
    private final String elevenLabsVoiceId;
    private final String anthropicModel;
    private final String webhookUrl;

    public VapiVoiceAgentAdapter(
            @Value("${vapi.api.key}") String apiKey,
            @Value("${vapi.api.base-url}") String baseUrl,
            @Value("${vapi.phone-number-id}") String phoneNumberId,
            @Value("${vapi.elevenlabs.voice-id}") String elevenLabsVoiceId,
            @Value("${vapi.anthropic.model}") String anthropicModel,
            @Value("${app.webhook.url}") String webhookUrl) {

        this.phoneNumberId = phoneNumberId;
        this.elevenLabsVoiceId = elevenLabsVoiceId;
        this.anthropicModel = anthropicModel;
        this.webhookUrl = webhookUrl;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public CallDispatchedEvent triggerOutboundCall(OutboundCallCommand cmd) {
        String systemPrompt = String.format("""
            Você é um executivo de prospecção B2B ligando para o telefone fixo da empresa %s.
            Seu objetivo é falar com %s sobre: %s.
            Diretrizes de conversação:
            - Fale em português do Brasil de forma natural, concisa e cordial.
            - Faça apenas uma pergunta por vez e aguarde a resposta.
            - Se for atendido por uma secretária eletrônica ou menu de URA sem opção de falar com atendente, despeça-se e encerre a ligação.
            """, cmd.companyName(), cmd.contactPerson(), cmd.customPitchObjective());

        Map<String, Object> payload = Map.of(
            "phoneNumberId", this.phoneNumberId,
            "customer", Map.of(
                "number", cmd.targetLandlineNumber(),
                "name", cmd.companyName()
            ),
            "assistant", Map.of(
                "firstMessage", "Olá, bom dia! Gostaria de falar com o responsável pelo setor administrativo ou comercial da " + cmd.companyName() + ", por gentileza.",
                "transcriber", Map.of(
                    "provider", "assembly-ai",
                    "language", "pt"
                ),
                "model", Map.of(
                    "provider", "anthropic",
                    "model", this.anthropicModel,
                    "temperature", 0.3,
                    "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt)
                    )
                ),
                "voice", Map.of(
                    "provider", "11labs",
                    "voiceId", this.elevenLabsVoiceId,
                    "model", "eleven_turbo_v2_5",
                    "stability", 0.5,
                    "similarityBoost", 0.75
                ),
                "serverUrl", this.webhookUrl
            )
        );

        log.info("Enviando requisição de chamada ativa ao Vapi.ai para o número: {}", cmd.targetLandlineNumber());

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("/call/phone")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Map.class);

        String callId = response != null ? (String) response.get("id") : "UNKNOWN";
        String status = response != null ? (String) response.get("status") : "FAILED";

        return new CallDispatchedEvent(callId, status, "VAPI_AI");
    }
}

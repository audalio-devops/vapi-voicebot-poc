package br.com.voicebot.application;

import br.com.voicebot.domain.CallDispatchedEvent;
import br.com.voicebot.domain.OutboundCallCommand;
import br.com.voicebot.domain.port.VoiceAgentProviderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

@Configuration
public class VoiceBotFunctions {

    private static final Logger log = LoggerFactory.getLogger(VoiceBotFunctions.class);

    @Bean
    public Function<OutboundCallCommand, CallDispatchedEvent> initiateProspectingCall(
            VoiceAgentProviderPort voiceProvider) {
        return command -> {
            log.info("Iniciando chamada ativa para {} no fixo {}", command.companyName(), command.targetLandlineNumber());
            return voiceProvider.triggerOutboundCall(command);
        };
    }

    @Bean
    @SuppressWarnings("unchecked")
    public Consumer<Map<String, Object>> vapiWebhookReceiver() {
        return payload -> {
            Map<String, Object> message = (Map<String, Object>) payload.get("message");
            if (message != null) {
                String type = (String) message.get("type");
                log.info("Evento Vapi recebido: {}", type);

                if ("end-of-call-report".equals(type)) {
                    String endedReason = (String) message.get("endedReason");
                    String summary = (String) message.get("summary");
                    String transcript = (String) message.get("transcript");
                    log.info("=== RELATÓRIO DE FIM DE CHAMADA ===");
                    log.info("Motivo de término: {}", endedReason);
                    log.info("Resumo: {}", summary);
                    log.info("Transcrição: {}", transcript);
                }
            }
        };
    }
}

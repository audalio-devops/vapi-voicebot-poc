package br.com.voicebot.domain.port;

import br.com.voicebot.domain.CallDispatchedEvent;
import br.com.voicebot.domain.OutboundCallCommand;

public interface VoiceAgentProviderPort {
    CallDispatchedEvent triggerOutboundCall(OutboundCallCommand command);
}

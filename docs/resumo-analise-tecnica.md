# 📋 Resumo da Análise Técnica do Projeto

**Projeto:** `vapi-voicebot-poc`  
**Objetivo:** Atendente Virtual Autônomo para Prospecção Ativa B2B (Foco em Telefonia Fixa Corporativa)  
**Data da Análise:** Novembro / 2026  
**Documento Principal:** [README.md](../README.md)

---

## 1. Visão Geral e Propósito

O **vapi-voicebot-poc** é uma Prova de Conceito (PoC) desenvolvida com **Java 21**, **Spring Boot 3.3.4** e **Spring Cloud Function** voltada à orquestração de chamadas ativas de prospecção corporativa (outbound B2B). 

A aplicação conecta sistemas corporativos (CRM/ERP) à infraestrutura de voz por inteligência artificial da **Vapi.ai**, automatizando o contato telefônico com empresas através de um agente conversacional humanizado, com raciocínio contextual, síntese de voz natural em português brasileiro e descarte inteligente de atendedores eletrônicos / URAs.

---

## 2. Arquitetura do Sistema

### 2.1 Padrão Arquitetural: Arquitetura Hexagonal (Ports & Adapters)

O microsserviço adota rigorosamente a separação em camadas recomendada pela **Clean Architecture / Arquitetura Hexagonal**:

```
                              [ Camada de Aplicação ]
                           Spring Cloud Function Beans
                           ├── initiateProspectingCall
                           └── vapiWebhookReceiver
                                      │
                                      ▼
                             [ Camada de Domínio ]
                                Regras de Negócio
                           ├── OutboundCallCommand (Record)
                           ├── CallDispatchedEvent (Record)
                           └── VoiceAgentProviderPort (Interface / Output Port)
                                      ▲
                                      │ (implementa)
                          [ Camada de Infraestrutura ]
                           ├── VapiVoiceAgentAdapter (RestClient)
                           └── Integração Externa: Vapi.ai API
```

#### Detalhamento das Camadas:

1. **Camada de Domínio (`br.com.voicebot.domain`)**:
   - **Totalmente desacoplada** de frameworks, anotações de infraestrutura ou SDKs de terceiros.
   - Entidades e Value Objects representados por **Java Records** imutáveis:
     - `OutboundCallCommand`: Encapsula `companyName`, `targetLandlineNumber`, `contactPerson`, `customPitchObjective` e `metadata`.
     - `CallDispatchedEvent`: Encapsula `externalCallId`, `status` e `providerName`.
   - **Porta de Saída (Output Port)**:
     - `VoiceAgentProviderPort`: Interface abstrata que define o contrato de disparo de chamadas (`triggerOutboundCall(command)`), garantindo que a regra de negócio não conheça detalhes da API Vapi.ai.

2. **Camada de Aplicação (`br.com.voicebot.application`)**:
   - Centralizada na classe `VoiceBotFunctions`.
   - Utiliza **Spring Cloud Function Web**, transformando métodos em endpoints HTTP mapeados diretamente pelo nome do Bean:
     - `initiateProspectingCall`: Implementa `Function<OutboundCallCommand, CallDispatchedEvent>` e expõe o endpoint `POST /initiateProspectingCall`.
     - `vapiWebhookReceiver`: Implementa `Consumer<Map<String, Object>>` e expõe o endpoint `POST /vapiWebhookReceiver`.

3. **Camada de Infraestrutura (`br.com.voicebot.infra.vapi`)**:
   - `VapiVoiceAgentAdapter`: Adaptador que implementa `VoiceAgentProviderPort`.
   - Emprega o `RestClient` nativo do Spring 6+ para efetuar chamadas seguras via HTTPS (`Bearer Token`) ao endpoint `/call/phone` da Vapi.ai.
   - Monta dinamicamente a definição do assistente efêmero injetado em cada chamada.

---

### 2.2 Pipeline Multimodal de IA

Em cada disparo de chamada, a aplicação instrui a Vapi.ai a orquestrar os melhores provedores de IA para cada etapa da conversa:

| Etapa | Provedor / Serviço | Modelo / Parâmetros | Finalidade |
| :--- | :--- | :--- | :--- |
| **STT (Reconhecimento de Fala)** | AssemblyAI | `assembly-ai` (Idioma: `pt`) | Transcrição em tempo real da voz do interlocutor brasileiro com baixa latência. |
| **LLM (Raciocínio & Persona)** | Anthropic Claude | `claude-3-5-sonnet-20241022` (Temp: `0.3`) | Compreensão de contexto corporativo, respostas concisas e decisão de desligamento perante URAs. |
| **TTS (Síntese de Voz)** | ElevenLabs | `eleven_turbo_v2_5` (Voz PT-BR, Stability: `0.5`, Boost: `0.75`) | Geração de áudio humanizado e fluido em português do Brasil. |
| **Telefonia / PSTN** | Vapi Phone Engine | Número configurado via `phoneNumberId` | Conexão SIP com rede de telefonia pública comutada para telefones fixos e celulares. |

---

### 2.3 Stack Tecnológica

- **Linguagem:** Java 21 (LTS)
- **Framework Principal:** Spring Boot 3.3.4
- **Arquitetura Serverless/Function:** Spring Cloud Function Web 2023.0.3
- **Cliente HTTP:** Spring Framework RestClient
- **Gerenciador de Dependências & Build:** Apache Maven 3.9+
- **Provedor de Telefonia e Orquestração de Voz:** Vapi.ai API v1
- **Modelos de IA:** Anthropic Claude 3.5 Sonnet, ElevenLabs Turbo v2.5, AssemblyAI

---

## 3. Funcionalidades Principais

### 3.1 Disparo de Chamada Ativa B2B (`initiateProspectingCall`)
- **Endpoint:** `POST /initiateProspectingCall`
- **Comportamento:**
  - Recebe os dados da empresa de destino, telefone no formato E.164, contato almejado e objetivo da abordagem.
  - Formata o **System Prompt** sob demanda:
    - Persona de executivo comercial cordial e objetivo.
    - Faz apenas uma pergunta por vez e aguarda resposta.
    - Se encontrar secretária eletrônica ou URA sem opção de falar com atendente, despede-se e encerra a chamada imediatamente para evitar custos desnecessários.
  - Define a mensagem inicial de abertura (*firstMessage*):  
    > *"Olá, bom dia! Gostaria de falar com o responsável pelo setor administrativo ou comercial da {companyName}, por gentileza."*
  - Retorna o `externalCallId` e o status inicial (`queued`).

### 3.2 Processamento Assíncrono de Webhooks (`vapiWebhookReceiver`)
- **Endpoint:** `POST /vapiWebhookReceiver`
- **Comportamento:**
  - Ouve eventos de ciclo de vida enviados pela infraestrutura da Vapi.
  - Ao receber o evento de finalização (`end-of-call-report`):
    - Extrai o **motivo de término** (`endedReason`: ex. `customer-ended-call`, `voicemail`, `assistant-ended-call`).
    - Extrai o **resumo executivo** gerado pela IA (`summary`).
    - Extrai a **transcrição completa** da conversa (`transcript`).
    - Registra as informações estruturadas em log para fácil consumo e integração futura com CRMs.

---

## 4. Roteiro de Execução e Validação

### 4.1 Configuração de Ambiente
1. **Definição de Variáveis de Ambiente:**
   As credenciais são injetadas no `application.yml` a partir de variáveis de ambiente:
   - `SERVER_PORT`: Porta local (padrão: `8080`).
   - `VAPI_API_KEY`: Chave privada da Vapi.ai.
   - `VAPI_PHONE_NUMBER_ID`: UUID do número de saída configurado na Vapi.
   - `VAPI_ELEVENLABS_VOICE_ID`: ID da voz ElevenLabs em português.
   - `VAPI_ANTHROPIC_MODEL`: Modelo da Anthropic (padrão: `claude-3-5-sonnet-20241022`).
   - `APP_WEBHOOK_URL`: URL pública exposta na internet (via túnel ngrok) para receber os eventos.

2. **Exposição Local via Ngrok:**
   ```bash
   ngrok http 8080
   ```
   Configurar `APP_WEBHOOK_URL=https://<seu-subdominio>.ngrok-free.app/vapiWebhookReceiver`.

3. **Compilação e Execução:**
   ```powershell
   # Compilação
   mvn clean compile

   # Execução
   mvn spring-boot:run
   ```

---

### 4.2 Roteiro de Validação Prática

#### Teste 1: Disparo de Chamada Ativa
```powershell
$body = @{
    companyName = "Tech Solutions Ltda"
    targetLandlineNumber = "+551130001234"
    contactPerson = "Diretor de TI"
    customPitchObjective = "Apresentar modernização de infraestrutura com redução de custos"
    metadata = @{ leadId = "LEAD-001" }
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/initiateProspectingCall" -Method Post -ContentType "application/json" -Body $body
```
* **Resultado Esperado:** Resposta `200 OK` com `externalCallId`, `status: queued` e `providerName: VAPI_AI`.

#### Teste 2: Monitoramento
- **No Telefone:** Chamada telefônica é recebida; agente fala a frase de abertura e dialoga em tempo real.
- **No Dashboard Vapi:** Visualização de latência de áudio, transcrição e logs em [dashboard.vapi.ai/calls](https://dashboard.vapi.ai/calls).

#### Teste 3: Simulação de Webhook de Fechamento
```powershell
$webhookBody = @{
    message = @{
        type = "end-of-call-report"
        endedReason = "customer-ended-call"
        summary = "O cliente atendeu, ouviu a proposta e solicitou envio de e-mail."
        transcript = "Assistente: Olá, bom dia! ... Cliente: Envie por e-mail, por favor."
    }
} | ConvertTo-Json -Depth 4

Invoke-RestMethod -Uri "http://localhost:8080/vapiWebhookReceiver" -Method Post -ContentType "application/json" -Body $webhookBody
```
* **Resultado Esperado:** Log no console contendo o bloco `=== RELATÓRIO DE FIM DE CHAMADA ===` com motivo, resumo e transcrição.

---

## 5. Mapeamento de Arquivos do Projeto

| Arquivo / Diretório | Responsabilidade |
| :--- | :--- |
| [`src/main/resources/application.yml`](../src/main/resources/application.yml) | Definição das portas, URLs e chaves da Vapi. |
| [`.env.example`](../.env.example) | Template de variáveis de ambiente exigidas pela aplicação. |
| [`src/main/java/br/com/voicebot/domain/OutboundCallCommand.java`](../src/main/java/br/com/voicebot/domain/OutboundCallCommand.java) | DTO/Record com os parâmetros da chamada de prospecção. |
| [`src/main/java/br/com/voicebot/domain/CallDispatchedEvent.java`](../src/main/java/br/com/voicebot/domain/CallDispatchedEvent.java) | DTO/Record com a resposta do despacho da chamada. |
| [`src/main/java/br/com/voicebot/domain/port/VoiceAgentProviderPort.java`](../src/main/java/br/com/voicebot/domain/port/VoiceAgentProviderPort.java) | Interface de contrato (porta de saída do domínio). |
| [`src/main/java/br/com/voicebot/application/VoiceBotFunctions.java`](../src/main/java/br/com/voicebot/application/VoiceBotFunctions.java) | Beans do Spring Cloud Function (`initiateProspectingCall` e `vapiWebhookReceiver`). |
| [`src/main/java/br/com/voicebot/infra/vapi/VapiVoiceAgentAdapter.java`](../src/main/java/br/com/voicebot/infra/vapi/VapiVoiceAgentAdapter.java) | Cliente REST integrador com a API Vapi.ai e montador de prompts. |
| [`README.md`](../README.md) | Documentação completa com diagramas de sequência e C4 em Mermaid. |


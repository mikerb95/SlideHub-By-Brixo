package com.brixo.slidehub.ai.service;

import org.springframework.stereotype.Service;

@Service
public class AssistService {

    private final GeminiService geminiService;
    private final GroqService groqService;

    public AssistService(GeminiService geminiService, GroqService groqService) {
        this.geminiService = geminiService;
        this.groqService = groqService;
    }

    public record AssistResult(String transcription, String answer) {
    }

    public AssistResult processAudio(byte[] audioBytes,
            String filename,
            String contentType,
            String repoUrl,
            int slideNumber,
            String slideContext) {
        String transcription = groqService.transcribeAudio(audioBytes, filename, contentType);
        String effectiveSlideContext = (slideContext != null && !slideContext.isBlank())
                ? slideContext
                : "Slide " + slideNumber;

        String repoContext = "";
        if (repoUrl != null && !repoUrl.isBlank()) {
            repoContext = geminiService.extractRepoContext(
                    repoUrl,
                    effectiveSlideContext + "\nPregunta transcrita: " + transcription);
        }

        String answer = groqService.answerAudienceQuestion(
                transcription,
                repoContext,
                effectiveSlideContext);

        return new AssistResult(transcription, answer);
    }
}

package com.brixo.slidehub.ai.controller;

import com.brixo.slidehub.ai.service.AssistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/assist")
public class AssistController {

    private static final Logger log = LoggerFactory.getLogger(AssistController.class);

    private final AssistService assistService;

    public AssistController(AssistService assistService) {
        this.assistService = assistService;
    }

    @PostMapping(value = "/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> processAudio(
            @RequestPart("audio") MultipartFile audio,
            @RequestParam(value = "repoUrl", required = false) String repoUrl,
            @RequestParam(value = "slideNumber", defaultValue = "1") int slideNumber,
            @RequestParam(value = "slideContext", required = false) String slideContext) {
        try {
            if (audio == null || audio.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "errorMessage", "Audio requerido"));
            }

            AssistService.AssistResult result = assistService.processAudio(
                    audio.getBytes(),
                    audio.getOriginalFilename(),
                    audio.getContentType(),
                    repoUrl,
                    slideNumber,
                    slideContext);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "transcription", result.transcription(),
                    "answer", result.answer()));
        } catch (Exception ex) {
            log.error("Error procesando audio de asistencia: {}", ex.getMessage());
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "errorMessage", ex.getMessage()));
        }
    }
}

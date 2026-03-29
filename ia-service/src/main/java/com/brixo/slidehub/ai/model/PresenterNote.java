package com.brixo.slidehub.ai.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.util.List;

/**
 * Documento MongoDB que representa las notas del presentador para un slide
 * (CLAUDE.md §10).
 *
 * No usa record: necesita @Id y mutabilidad para el upsert por
 * presentationId+slideNumber (HU-016 §2).
 */
@Document(collection = "presenter_notes")
@CompoundIndex(name = "pres_slide_idx", def = "{'presentationId': 1, 'slideNumber': 1}", unique = true)
public class PresenterNote {

    @Id
    private String id;

    private String presentationId;
    private int slideNumber;
    private String title;
    private List<String> points;
    private String suggestedTime;
    private List<String> keyPhrases;
    private List<String> demoTags;

    // Constructor completo para creación
    public PresenterNote(String presentationId, int slideNumber, String title,
            List<String> points, String suggestedTime,
            List<String> keyPhrases, List<String> demoTags) {
        this.presentationId = presentationId;
        this.slideNumber = slideNumber;
        this.title = title;
        this.points = points;
        this.suggestedTime = suggestedTime;
        this.keyPhrases = keyPhrases;
        this.demoTags = demoTags;
    }

    // Constructor vacío requerido por Spring Data MongoDB
    public PresenterNote() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPresentationId() {
        return presentationId;
    }

    public int getSlideNumber() {
        return slideNumber;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getPoints() {
        return points;
    }

    public String getSuggestedTime() {
        return suggestedTime;
    }

    public List<String> getKeyPhrases() {
        return keyPhrases;
    }

    public List<String> getDemoTags() {
        return demoTags;
    }

    // Setters necesarios para upsert (CLAUDE.md §10.3)
    public void setPresentationId(String presentationId) {
        this.presentationId = presentationId;
    }

    public void setSlideNumber(int slideNumber) {
        this.slideNumber = slideNumber;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setPoints(List<String> points) {
        this.points = points;
    }

    public void setSuggestedTime(String suggestedTime) {
        this.suggestedTime = suggestedTime;
    }

    public void setKeyPhrases(List<String> keyPhrases) {
        this.keyPhrases = keyPhrases;
    }

    public void setDemoTags(List<String> demoTags) {
        this.demoTags = demoTags;
    }
}

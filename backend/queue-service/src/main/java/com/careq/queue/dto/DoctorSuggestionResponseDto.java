package com.careq.queue.dto;

import com.careq.queue.entity.TriageLevel;

import java.util.List;

/**
 * Response for POST /api/queue/doctor-suggestions.
 *
 * Wraps the AI assessment (urgency + department the symptoms point to) together
 * with the top ranked doctor suggestions. The suggestions are live: availability
 * and predicted waits are computed from the current catalog and queue state at
 * request time.
 */
public class DoctorSuggestionResponseDto {

    private TriageLevel triageLevel;
    private String suggestedDepartment;
    private boolean emergency;
    private String urgencyNote;
    private List<DoctorSuggestionDto> suggestions;

    public DoctorSuggestionResponseDto() {
    }

    public TriageLevel getTriageLevel() {
        return triageLevel;
    }

    public void setTriageLevel(TriageLevel triageLevel) {
        this.triageLevel = triageLevel;
    }

    public String getSuggestedDepartment() {
        return suggestedDepartment;
    }

    public void setSuggestedDepartment(String suggestedDepartment) {
        this.suggestedDepartment = suggestedDepartment;
    }

    public boolean isEmergency() {
        return emergency;
    }

    public void setEmergency(boolean emergency) {
        this.emergency = emergency;
    }

    public String getUrgencyNote() {
        return urgencyNote;
    }

    public void setUrgencyNote(String urgencyNote) {
        this.urgencyNote = urgencyNote;
    }

    public List<DoctorSuggestionDto> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<DoctorSuggestionDto> suggestions) {
        this.suggestions = suggestions;
    }
}

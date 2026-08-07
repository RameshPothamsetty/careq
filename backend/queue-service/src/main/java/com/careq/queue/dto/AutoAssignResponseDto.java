package com.careq.queue.dto;

import com.careq.queue.entity.TriageLevel;

import java.util.List;

/**
 * Response for POST /api/queue/auto-assign (Phase 2).
 *
 * One DTO, two shapes:
 *  - assigned=true  — the AI joined the patient to the single best available
 *    doctor's queue. {@code entry} carries the queue entry (triage, live
 *    position, predicted wait); {@code assignedDoctor} the doctor chosen.
 *  - assigned=false — no confident specialty match, or no available doctors.
 *    Nothing was joined; {@code suggestions} lists the top candidates so the
 *    patient can confirm one manually (the Phase 1 flow).
 *
 * {@code reason}: ASSIGNED | AMBIGUOUS_SYMPTOMS | NO_AVAILABLE_DOCTORS.
 */
public class AutoAssignResponseDto {

    private boolean assigned;
    private String reason;
    private String message;

    private TriageLevel triageLevel;
    private String suggestedDepartment;
    private boolean emergency;
    private String urgencyNote;

    private DoctorSuggestionDto assignedDoctor;
    private QueueEntryResponseDto entry;
    private List<DoctorSuggestionDto> suggestions;

    public AutoAssignResponseDto() {
    }

    public boolean isAssigned() {
        return assigned;
    }

    public void setAssigned(boolean assigned) {
        this.assigned = assigned;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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

    public DoctorSuggestionDto getAssignedDoctor() {
        return assignedDoctor;
    }

    public void setAssignedDoctor(DoctorSuggestionDto assignedDoctor) {
        this.assignedDoctor = assignedDoctor;
    }

    public QueueEntryResponseDto getEntry() {
        return entry;
    }

    public void setEntry(QueueEntryResponseDto entry) {
        this.entry = entry;
    }

    public List<DoctorSuggestionDto> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<DoctorSuggestionDto> suggestions) {
        this.suggestions = suggestions;
    }
}

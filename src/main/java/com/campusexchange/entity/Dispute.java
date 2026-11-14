package com.campusexchange.entity;

import java.time.LocalDateTime;

public class Dispute {

    public static final String STATUS_OPEN     = "OPEN";
    public static final String STATUS_RESOLVED = "RESOLVED";

    private int disputeId;
    private int ledgerId;
    private int reporterId;
    private String reporterUsername;  // joined
    private String reason;
    private String status;
    private String adminNotes;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    // Extra display fields joined from ledger/listing
    private String listingTitle;
    private int listingId;

    public Dispute() {}

    public boolean isOpen() {
        return STATUS_OPEN.equals(this.status);
    }

    // Getters and Setters
    public int getDisputeId() { return disputeId; }
    public void setDisputeId(int disputeId) { this.disputeId = disputeId; }

    public int getLedgerId() { return ledgerId; }
    public void setLedgerId(int ledgerId) { this.ledgerId = ledgerId; }

    public int getReporterId() { return reporterId; }
    public void setReporterId(int reporterId) { this.reporterId = reporterId; }

    public String getReporterUsername() { return reporterUsername; }
    public void setReporterUsername(String reporterUsername) { this.reporterUsername = reporterUsername; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getListingTitle() { return listingTitle; }
    public void setListingTitle(String listingTitle) { this.listingTitle = listingTitle; }

    public int getListingId() { return listingId; }
    public void setListingId(int listingId) { this.listingId = listingId; }
}

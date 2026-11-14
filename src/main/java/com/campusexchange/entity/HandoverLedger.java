package com.campusexchange.entity;

import java.time.LocalDateTime;

public class HandoverLedger {

    // Transaction state constants — strict state machine
    public static final String STATE_RESERVED         = "RESERVED";
    public static final String STATE_MEETUP_PROPOSED  = "MEETUP_PROPOSED";
    public static final String STATE_MEETUP_ARRANGED  = "MEETUP_ARRANGED";
    public static final String STATE_VERIFYING        = "VERIFYING";
    public static final String STATE_COMPLETED        = "COMPLETED";
    public static final String STATE_DISPUTED         = "DISPUTED";
    public static final String STATE_CANCELLED        = "CANCELLED";

    private long version;
    public long getVersion(){return version;}
    public void setVersion(long version){this.version=version;}
    private int ledgerId;
    private int listingId;
    private int buyerId;
    private int sellerId;

    // For display purposes (joined from users)
    private String buyerUsername;
    private String sellerUsername;
    private String listingTitle;

    private String meetupLocation;
    private LocalDateTime meetupTime;
    private String buyerToken;
    private String sellerToken;
    private boolean buyerVerified;
    private boolean sellerVerified;
    private Integer proposedBy;
    private String transactionState;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public HandoverLedger() {}

    public boolean isCompleted() {
        return STATE_COMPLETED.equals(this.transactionState);
    }

    public boolean isDisputed() {
        return STATE_DISPUTED.equals(this.transactionState);
    }

    public boolean isMeetupArranged() {
        return STATE_MEETUP_ARRANGED.equals(this.transactionState);
    }

    // Getters and Setters
    public int getLedgerId() { return ledgerId; }
    public void setLedgerId(int ledgerId) { this.ledgerId = ledgerId; }

    public int getListingId() { return listingId; }
    public void setListingId(int listingId) { this.listingId = listingId; }

    public int getBuyerId() { return buyerId; }
    public void setBuyerId(int buyerId) { this.buyerId = buyerId; }

    public int getSellerId() { return sellerId; }
    public void setSellerId(int sellerId) { this.sellerId = sellerId; }

    public String getBuyerUsername() { return buyerUsername; }
    public void setBuyerUsername(String buyerUsername) { this.buyerUsername = buyerUsername; }

    public String getSellerUsername() { return sellerUsername; }
    public void setSellerUsername(String sellerUsername) { this.sellerUsername = sellerUsername; }

    public String getListingTitle() { return listingTitle; }
    public void setListingTitle(String listingTitle) { this.listingTitle = listingTitle; }

    public String getMeetupLocation() { return meetupLocation; }
    public void setMeetupLocation(String meetupLocation) { this.meetupLocation = meetupLocation; }

    public LocalDateTime getMeetupTime() { return meetupTime; }
    public void setMeetupTime(LocalDateTime meetupTime) { this.meetupTime = meetupTime; }

    public String getBuyerToken() { return buyerToken; }
    public void setBuyerToken(String buyerToken) { this.buyerToken = buyerToken; }

    public String getSellerToken() { return sellerToken; }
    public void setSellerToken(String sellerToken) { this.sellerToken = sellerToken; }

    public boolean isBuyerVerified() { return buyerVerified; }
    public void setBuyerVerified(boolean buyerVerified) { this.buyerVerified = buyerVerified; }

    public boolean isSellerVerified() { return sellerVerified; }
    public void setSellerVerified(boolean sellerVerified) { this.sellerVerified = sellerVerified; }

    public Integer getProposedBy() { return proposedBy; }
    public void setProposedBy(Integer proposedBy) { this.proposedBy = proposedBy; }

    public String getTransactionState() { return transactionState; }
    public void setTransactionState(String transactionState) { this.transactionState = transactionState; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

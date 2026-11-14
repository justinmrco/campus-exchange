package com.campusexchange.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Listing {

    public static final String STATUS_AVAILABLE  = "AVAILABLE";
    public static final String STATUS_RESERVED   = "RESERVED";
    public static final String STATUS_COMPLETED  = "COMPLETED";
    public static final String STATUS_INACTIVE   = "INACTIVE";
    public static final String STATUS_DISPUTED   = "DISPUTED";

    private int listingId;
    private int sellerId;
    private String sellerUsername; // joined from users table for display
    private String title;
    private String imagePath;
    public String getImagePath(){return imagePath;}
    public void setImagePath(String value){imagePath=value;}
    public String getImageUrl(){return imagePath!=null && imagePath.matches("/(images/demo/[a-z0-9-]+|media/listings/[a-f0-9-]{36})\\.jpg")?imagePath:null;}
    public boolean isDemoPhoto(){return getImageUrl()!=null && imagePath.startsWith("/images/demo/");}
    private String description;
    private BigDecimal price;
    private String category;
    private String status;
    private LocalDateTime createdAt;

    public Listing() {}

    public boolean isAvailable() {
        return STATUS_AVAILABLE.equals(this.status);
    }

    public boolean isReserved() {
        return STATUS_RESERVED.equals(this.status);
    }

    public int getListingId() { return listingId; }
    public void setListingId(int listingId) { this.listingId = listingId; }

    public int getSellerId() { return sellerId; }
    public void setSellerId(int sellerId) { this.sellerId = sellerId; }

    public String getSellerUsername() { return sellerUsername; }
    public void setSellerUsername(String sellerUsername) { this.sellerUsername = sellerUsername; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

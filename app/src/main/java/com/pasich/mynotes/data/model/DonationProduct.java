package com.pasich.mynotes.data.model;

public class DonationProduct {
    private String id;
    private String title;
    private String description;
    private String price;
    private String iconResource;
    private boolean isPurchased;

    public DonationProduct(String id, String title, String description, String price, String iconResource) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.price = price;
        this.iconResource = iconResource;
        this.isPurchased = false;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getIconResource() {
        return iconResource;
    }

    public void setIconResource(String iconResource) {
        this.iconResource = iconResource;
    }

    public boolean isPurchased() {
        return isPurchased;
    }

    public void setPurchased(boolean purchased) {
        isPurchased = purchased;
    }
}

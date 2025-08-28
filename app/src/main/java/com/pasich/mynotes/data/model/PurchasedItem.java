package com.pasich.mynotes.data.model;

public class PurchasedItem {
    private String productId;
    private String title;
    private String iconResource;
    private int count;

    public PurchasedItem(String productId, String title, String iconResource, int count) {
        this.productId = productId;
        this.title = title;
        this.iconResource = iconResource;
        this.count = count;
    }

    // Getters and setters
    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getIconResource() {
        return iconResource;
    }

    public void setIconResource(String iconResource) {
        this.iconResource = iconResource;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void incrementCount() {
        this.count++;
    }
}

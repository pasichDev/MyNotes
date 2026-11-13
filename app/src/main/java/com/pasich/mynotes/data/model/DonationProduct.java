package com.pasich.mynotes.data.model;

import java.util.Comparator;

/** Model representing an in-app donation product. */
public class DonationProduct {

    /** Price amount for a product whose offer details are missing. */
    public static final long NO_PRICE = Long.MAX_VALUE;

    /**
     * Cheapest first, by the exact amount Play reports. The formatted price is for display only:
     * parsing it reads a thousands separator ("¥1,000") as a decimal point.
     */
    public static final Comparator<DonationProduct> BY_PRICE =
            Comparator.comparingLong(DonationProduct::getPriceAmountMicros);

    private String id;
    private String title;
    private String description;
    private String price;
    private String iconResource;
    private final long priceAmountMicros;
    private boolean isPurchased;

    public DonationProduct(
            String id, String title, String description, String price, String iconResource) {
        this(id, title, description, price, iconResource, NO_PRICE);
    }

    public DonationProduct(
            String id,
            String title,
            String description,
            String price,
            String iconResource,
            long priceAmountMicros) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.price = price;
        this.iconResource = iconResource;
        this.priceAmountMicros = priceAmountMicros;
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

    /** The price in micro-units of its currency, or {@link #NO_PRICE}. */
    public long getPriceAmountMicros() {
        return priceAmountMicros;
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

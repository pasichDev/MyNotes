package com.pasich.mynotes.billing;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.android.billingclient.api.*;
import com.pasich.mynotes.data.model.DonationProduct;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class BillingManager implements PurchasesUpdatedListener, BillingClientStateListener {

    private static final String TAG = "BillingManager";
    
    public interface BillingManagerListener {
        void onBillingInitialized();
        void onProductsLoaded(List<DonationProduct> products);
        void onPurchaseSuccessful(String productId);
        void onPurchaseFailed(int responseCode, String debugMessage);
        void onBillingError(String errorMessage);
        void onPurchasesLoaded(List<Purchase> purchases);
    }

    private final BillingClient billingClient;
    private final BillingManagerListener listener;
    private boolean isServiceConnected;

    // Product IDs for donations
    private static final List<String> DONATION_PRODUCT_IDS = Arrays.asList(
            "donate_seed_of_ideas",
            "donate_spark_of_inspiration",
            "donate_midnight_notebook",
            "donate_wave_of_support",
            "donate_universe_of_inspiration"
    );

    public BillingManager(Context context, BillingManagerListener listener) {
        this.listener = listener;
        billingClient = BillingClient.newBuilder(context)
                .setListener(this)
                .enablePendingPurchases()
                .build();
        
        startServiceConnection();
    }

    private void startServiceConnection() {
        billingClient.startConnection(this);
    }

    @Override
    public void onBillingSetupFinished(BillingResult billingResult) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            isServiceConnected = true;
            Log.d(TAG, "Billing service connected successfully");
            listener.onBillingInitialized();
            queryProductDetails();
            queryPurchases(); // Додаємо запит покупок
        } else {
            Log.e(TAG, "Failed to connect to billing service: " + billingResult.getDebugMessage());
            listener.onBillingError("Failed to connect to billing service");
        }
    }

    @Override
    public void onBillingServiceDisconnected() {
        isServiceConnected = false;
        Log.w(TAG, "Billing service disconnected");
    }

    private void queryProductDetails() {
        if (!isServiceConnected) {
            Log.w(TAG, "Billing service not connected, can't query product details");
            return;
        }

        List<QueryProductDetailsParams.Product> productList = new ArrayList<>();
        for (String productId : DONATION_PRODUCT_IDS) {
            productList.add(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            );
        }

        QueryProductDetailsParams queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build();

        billingClient.queryProductDetailsAsync(queryProductDetailsParams,
                (billingResult, productDetailsList) -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        List<DonationProduct> products = new ArrayList<>();
                        for (ProductDetails productDetails : productDetailsList) {
                            DonationProduct product = createDonationProduct(productDetails);
                            products.add(product);
                        }

                        // Сортуємо товари за ціною (від меншої до більшої)
                        Collections.sort(products, new Comparator<DonationProduct>() {
                            @Override
                            public int compare(DonationProduct p1, DonationProduct p2) {
                                try {
                                    // Витягуємо числові значення з цін
                                    double price1 = extractPrice(p1.getPrice());
                                    double price2 = extractPrice(p2.getPrice());
                                    return Double.compare(price1, price2);
                                } catch (Exception e) {
                                    Log.w(TAG, "Error sorting prices: " + e.getMessage());
                                    return 0;
                                }
                            }
                        });

                        listener.onProductsLoaded(products);
                    } else {
                        Log.e(TAG, "Failed to query product details: " + billingResult.getDebugMessage());
                        listener.onBillingError("Failed to load products");
                    }
                });
    }

    private DonationProduct createDonationProduct(ProductDetails productDetails) {
        String iconResource = getIconForProduct(productDetails.getProductId());
        ProductDetails.OneTimePurchaseOfferDetails offerDetails = productDetails.getOneTimePurchaseOfferDetails();
        String price = offerDetails != null ? offerDetails.getFormattedPrice() : "N/A";
        
        return new DonationProduct(
                productDetails.getProductId(),
                productDetails.getTitle(),
                productDetails.getDescription(),
                price,
                iconResource
        );
    }

    private String getIconForProduct(String productId) {
        return switch (productId) {
            case "donate_seed_of_ideas" -> "ic_seed";
            case "donate_spark_of_inspiration" -> "ic_spark";
            case "donate_midnight_notebook" -> "ic_notebook";
            case "donate_wave_of_support" -> "ic_wave";
            case "donate_universe_of_inspiration" -> "ic_universe";
            default -> "ic_heart";
        };
    }

    public void launchBillingFlow(Activity activity, String productId) {
        if (!isServiceConnected) {
            listener.onBillingError("Billing service not connected");
            return;
        }

        List<QueryProductDetailsParams.Product> productList = List.of(
                QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
        );

        QueryProductDetailsParams queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build();

        billingClient.queryProductDetailsAsync(queryProductDetailsParams,
                (billingResult, productDetailsList) -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && !productDetailsList.isEmpty()) {
                        
                        ProductDetails productDetails = productDetailsList.get(0);
                        List<BillingFlowParams.ProductDetailsParams> productDetailsParamsList = List.of(
                                BillingFlowParams.ProductDetailsParams.newBuilder()
                                        .setProductDetails(productDetails)
                                        .build()
                        );

                        BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                                .setProductDetailsParamsList(productDetailsParamsList)
                                .build();

                        BillingResult result = billingClient.launchBillingFlow(activity, billingFlowParams);
                        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                            Log.e(TAG, "Failed to launch billing flow: " + result.getDebugMessage());
                            listener.onPurchaseFailed(result.getResponseCode(), result.getDebugMessage());
                        }
                    } else {
                        Log.e(TAG, "Product not found: " + productId);
                        listener.onBillingError("Product not found");
                    }
                });
    }

    @Override
    public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                handlePurchase(purchase);
            }
        } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User canceled the purchase");
            listener.onPurchaseFailed(billingResult.getResponseCode(), "Purchase canceled by user");
        } else {
            Log.e(TAG, "Purchase failed: " + billingResult.getDebugMessage());
            listener.onPurchaseFailed(billingResult.getResponseCode(), billingResult.getDebugMessage());
        }
    }

    private void handlePurchase(Purchase purchase) {
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            // Acknowledge the purchase if it hasn't been acknowledged yet
            if (!purchase.isAcknowledged()) {
                AcknowledgePurchaseParams acknowledgePurchaseParams =
                        AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.getPurchaseToken())
                                .build();
                
                billingClient.acknowledgePurchase(acknowledgePurchaseParams, billingResult -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "Purchase acknowledged successfully");
                    }
                });
            }
            
            // Get the first product ID from the purchase
            List<String> products = purchase.getProducts();
            if (!products.isEmpty()) {
                listener.onPurchaseSuccessful(products.get(0));
            }
        }
    }

    public void destroy() {
        if (billingClient != null && billingClient.isReady()) {
            billingClient.endConnection();
        }
    }

    public static String getBillingErrorMessage(int responseCode) {
        return switch (responseCode) {
            case BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
                    "Billing API version is not supported for the type requested";
            case BillingClient.BillingResponseCode.DEVELOPER_ERROR ->
                    "Invalid arguments provided to the API";
            case BillingClient.BillingResponseCode.ERROR -> "Fatal error during the API action";
            case BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED ->
                    "Requested feature is not supported";
            case BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                    "Failure to purchase since item is already owned";
            case BillingClient.BillingResponseCode.ITEM_NOT_OWNED ->
                    "Failure to consume since item is not owned";
            case BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
                    "Requested product is not available for purchase";
            case BillingClient.BillingResponseCode.SERVICE_DISCONNECTED ->
                    "Play Store service is not connected now";
            case BillingClient.BillingResponseCode.SERVICE_TIMEOUT ->
                    "The request has reached the maximum timeout before Google Play responds";
            case BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE ->
                    "Network connection is down";
            case BillingClient.BillingResponseCode.USER_CANCELED ->
                    "User pressed back or canceled a dialog";
            default -> "Unknown billing error";
        };
    }

    /**
     * Витягує числове значення ціни з рядка ціни
     * Наприклад: "₴40.00" -> 40.0, "200,00 UAH" -> 200.0
     */
    private double extractPrice(String priceString) {
        if (priceString == null || priceString.isEmpty()) {
            return 0.0;
        }
        
        try {
            // Видаляємо всі символи, крім цифр, крапок та ком
            String cleanPrice = priceString.replaceAll("[^\\d.,]", "");
            
            // Замінюємо кому на крапку для уніфікації
            cleanPrice = cleanPrice.replace(",", ".");
            
            // Якщо є кілька крапок, залишаємо тільки останню
            int lastDotIndex = cleanPrice.lastIndexOf(".");
            if (lastDotIndex != -1 && lastDotIndex != cleanPrice.indexOf(".")) {
                cleanPrice = cleanPrice.substring(0, lastDotIndex).replace(".", "") + 
                           cleanPrice.substring(lastDotIndex);
            }
            
            return Double.parseDouble(cleanPrice);
        } catch (NumberFormatException e) {
            Log.w(TAG, "Failed to parse price: " + priceString, e);
            return 0.0;
        }
    }

    public void queryPurchases() {
        if (!isServiceConnected) {
            Log.w(TAG, "Billing service not connected, can't query purchases");
            return;
        }

        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build();

        billingClient.queryPurchasesAsync(params,
                (billingResult, purchasesList) -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        listener.onPurchasesLoaded(purchasesList);
                    } else {
                        Log.e(TAG, "Failed to query purchases: " + billingResult.getDebugMessage());
                        listener.onPurchasesLoaded(new ArrayList<>());
                    }
                });
    }
}

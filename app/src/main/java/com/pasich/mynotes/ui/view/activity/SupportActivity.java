package com.pasich.mynotes.ui.view.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.billingclient.api.Purchase;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import com.google.android.material.transition.platform.MaterialFade;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.billing.BillingManager;
import com.pasich.mynotes.data.model.DonationProduct;
import com.pasich.mynotes.data.model.PurchasedItem;
import com.pasich.mynotes.databinding.ActivitySupportBinding;
import com.pasich.mynotes.ui.adapter.DonationProductAdapter;
import com.pasich.mynotes.ui.adapter.PurchasedItemsAdapter;

import java.util.List;
import java.util.Objects;
import java.util.ArrayList;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class SupportActivity extends BaseActivity implements BillingManager.BillingManagerListener {

    private static final String TAG = "SupportActivity";
    
    public ActivitySupportBinding binding;
    private BillingManager billingManager;
    private DonationProductAdapter donationAdapter;
    private PurchasedItemsAdapter purchasedItemsAdapter;
    
    // Поля для керування спойлерами
    private LinearLayout donationContent, purchasesContent, contactContent;
    private ImageView donationArrow, purchasesArrow, contactArrow;
    
    // Поля для геймифікації
    private View noPurchasesLayout, gamificationLayout;
    private TextView totalSupportCount;
    private RecyclerView purchasedItemsRecycler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        selectTheme();
        binding = ActivitySupportBinding.inflate(getLayoutInflater());
        getWindow().setEnterTransition(new MaterialFade().addTarget(binding.activitySupport));
        getWindow().setAllowEnterTransitionOverlap(true);
        super.onCreate(savedInstanceState);
        setContentView(binding.getRoot());
        setupEdgeToEdgeInsets(binding.getRoot());
        binding.setActivity(this);
        initActivity();
        initListeners();
        initBilling();
        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                setEnabled(finishActivity());
            }
        });
    }

    @Override
    public void initListeners() {
        // Ініціалізуємо геймифікацію
        initGameification();
        
        // Знаходимо всі елементи спойлерів
        donationContent = findViewById(R.id.donation_content);
        donationArrow = findViewById(R.id.donation_arrow);
        
        purchasesContent = findViewById(R.id.purchases_content);
        purchasesArrow = findViewById(R.id.purchases_arrow);
        
        contactContent = findViewById(R.id.contact_content);
        contactArrow = findViewById(R.id.contact_arrow);

        // Налаштовуємо спойлери з новою логікою
        setupExpandableSection(
            findViewById(R.id.donation_header),
            donationContent,
            donationArrow,
            "donation", false // відкритий за замовчуванням
        );

        setupExpandableSection(
            findViewById(R.id.purchases_header),
            purchasesContent,
            purchasesArrow,
            "purchases",
                true // закритий за замовчуванням
        );

        setupExpandableSection(
            findViewById(R.id.contact_header),
            contactContent,
            contactArrow,
            "contact",
            false // закритий за замовчуванням
        );
    }

    private void initGameification() {
        noPurchasesLayout = findViewById(R.id.no_purchases_layout);
        gamificationLayout = findViewById(R.id.gamification_layout);
        totalSupportCount = findViewById(R.id.total_support_count);
        purchasedItemsRecycler = findViewById(R.id.purchased_items_recycler);
        
        // Налаштовуємо RecyclerView для колекції
        purchasedItemsAdapter = new PurchasedItemsAdapter();
        purchasedItemsRecycler.setLayoutManager(new GridLayoutManager(this, 4));
        purchasedItemsRecycler.setAdapter(purchasedItemsAdapter);
        purchasedItemsRecycler.setNestedScrollingEnabled(false);
    }

    private void initBilling() {
        billingManager = new BillingManager(this, this);
        setupDonationRecyclerView();
    }

    private void setupDonationRecyclerView() {
        RecyclerView recyclerView = findViewById(R.id.donation_products_recycler);
        donationAdapter = new DonationProductAdapter(product -> {
            if (billingManager != null) {
                billingManager.launchBillingFlow(this, product.getId());
            }
        });
        
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(donationAdapter);
    }

    private void setupExpandableSection(View header, LinearLayout content, ImageView arrow, String sectionId, boolean initiallyExpanded) {
        if (header == null || content == null || arrow == null) {
            return; // Якщо елементи не знайдені, виходимо
        }

        // Встановлюємо початковий стан
        content.setVisibility(initiallyExpanded ? View.VISIBLE : View.GONE);
        arrow.setRotation(initiallyExpanded ? 180f : 0f);

        header.setOnClickListener(v -> {
            boolean isExpanded = content.getVisibility() == View.VISIBLE;
            if (isExpanded) {
                // Закриваємо поточний спойлер
                closeSection(content, arrow);
            } else {
                // Закриваємо всі інші спойлери і відкриваємо поточний
                closeAllSectionsExcept(sectionId);
                openSection(content, arrow);
            }
        });
    }

    private void closeAllSectionsExcept(String exceptSectionId) {
        if (!"donation".equals(exceptSectionId) && donationContent != null && donationContent.getVisibility() == View.VISIBLE) {
            closeSection(donationContent, donationArrow);
        }
        if (!"purchases".equals(exceptSectionId) && purchasesContent != null && purchasesContent.getVisibility() == View.VISIBLE) {
            closeSection(purchasesContent, purchasesArrow);
        }
        if (!"contact".equals(exceptSectionId) && contactContent != null && contactContent.getVisibility() == View.VISIBLE) {
            closeSection(contactContent, contactArrow);
        }
    }

    private void openSection(LinearLayout content, ImageView arrow) {
        content.setVisibility(View.VISIBLE);
        arrow.animate().rotation(180f).setDuration(200).start();
    }

    private void closeSection(LinearLayout content, ImageView arrow) {
        content.setVisibility(View.GONE);
        arrow.animate().rotation(0f).setDuration(200).start();
    }

    private void initActivity() {
        setSupportActionBar(binding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
    }

    public void sendTelegram() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/pasichDev")));
    }

    public void sendEmail() {
        Intent intent = new Intent(Intent.ACTION_SENDTO).setData(Uri.parse("mailto:pasichDev@outlook.com"));
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }

    public void reddit() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.reddit.com/r/MyNotes_/")));
    }

    public void sendKoFi() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/pasichdev")));
    }

    public void openPlayStore() {
        final Intent rateAppIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName()));
        if (!getPackageManager().queryIntentActivities(rateAppIntent, 0).isEmpty()) {
            startActivity(rateAppIntent);
        } else {
            Toast.makeText(this, getString(R.string.notFoundPlayMarket), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finishActivity();
        }
        return true;
    }

    private boolean finishActivity() {
        if (billingManager != null) {
            billingManager.destroy();
        }
        supportFinishAfterTransition();
        return true;
    }

    @Override
    public void onBillingInitialized() {
        // Сразу загружаем покупки для обновления UI
        if (billingManager != null) {
            billingManager.queryPurchases();
        }
    }



    @Override
    public void onProductsLoaded(List<DonationProduct> products) {
        Log.d(TAG, "Products loaded: " + products.size());
        runOnUiThread(() -> {
            findViewById(R.id.purchases_loading).setVisibility(View.GONE);
            
            if (products.isEmpty()) {
                findViewById(R.id.purchases_empty).setVisibility(View.VISIBLE);
                findViewById(R.id.donation_products_recycler).setVisibility(View.GONE);
            } else {
                findViewById(R.id.purchases_empty).setVisibility(View.GONE);
                findViewById(R.id.donation_products_recycler).setVisibility(View.VISIBLE);
                donationAdapter.setProducts(products);
                
                // Після завантаження товарів одразу перевіряємо покупки
                if (billingManager != null) {
                    billingManager.queryPurchases();
                }
            }
        });
    }

    @Override
    public void onPurchaseSuccessful(String productId) {
        Log.d(TAG, "Purchase successful: " + productId);
        runOnUiThread(() -> {
            showPurchaseSuccessDialog();
            // Обновляем покупки для геймификации
            if (billingManager != null) {
                billingManager.queryPurchases();
            }
        });
    }

    @Override
    public void onPurchaseFailed(int responseCode, String debugMessage) {
        Log.e(TAG, "Purchase failed: " + responseCode + " - " + debugMessage);
        runOnUiThread(() -> {
            String errorMessage = BillingManager.getBillingErrorMessage(responseCode);
            Snackbar.make(binding.getRoot(), 
                getString(R.string.purchase_error_title) + ": " + errorMessage, 
                Snackbar.LENGTH_LONG).show();
        });
    }

    @Override
    public void onBillingError(String errorMessage) {
        Log.e(TAG, "Billing error: " + errorMessage);
        runOnUiThread(() -> {
            findViewById(R.id.purchases_loading).setVisibility(View.GONE);
            findViewById(R.id.purchases_empty).setVisibility(View.VISIBLE);
            findViewById(R.id.donation_products_recycler).setVisibility(View.GONE);
            
            Snackbar.make(binding.getRoot(), errorMessage, Snackbar.LENGTH_LONG).show();
        });
    }

    @Override
    public void onPurchasesLoaded(List<Purchase> purchases) {
        Log.d(TAG, "Purchases loaded for gamification: " + purchases.size());
        runOnUiThread(() -> {
            updateGameification(purchases);
            updateProductsWithPurchaseStatus(purchases);
        });
    }

    private void updateGameification(List<Purchase> purchases) {
        if (purchases.isEmpty()) {
            // Показуємо стандартний заголовок
            noPurchasesLayout.setVisibility(View.VISIBLE);
            gamificationLayout.setVisibility(View.GONE);
        } else {
            // Показуємо геймифікацію
            noPurchasesLayout.setVisibility(View.GONE);
            gamificationLayout.setVisibility(View.VISIBLE);
            
            // Обробляємо покупки для колекції
            java.util.Map<String, PurchasedItem> itemsMap = new java.util.HashMap<>();
            int totalCount = 0;
            
            for (Purchase purchase : purchases) {
                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    List<String> products = purchase.getProducts();
                    for (String productId : products) {
                        totalCount++;
                        
                        // Завжди отримуємо актуальні дані для товару
                        String iconResource = getIconForProduct(productId);
                        String title = getProductTitle(productId);
                        
                        if (itemsMap.containsKey(productId)) {
                            PurchasedItem existingItem = itemsMap.get(productId);
                            existingItem.incrementCount();
                            // Оновлюємо назву та іконку на випадок змін
                            existingItem.setTitle(title);
                            existingItem.setIconResource(iconResource);
                        } else {
                            itemsMap.put(productId, new PurchasedItem(productId, title, iconResource, 1));
                        }
                    }
                }
            }
            
            // Оновлюємо UI
            totalSupportCount.setText(String.valueOf(totalCount));
            List<PurchasedItem> items = new ArrayList<>(itemsMap.values());
            purchasedItemsAdapter.setItems(items);
        }
    }

    private String getIconForProduct(String productId) {
        return switch (productId) {
            case "donate_seed_of_ideas" -> "ic_seed";
            case "donate_spark_of_inspiration" -> "ic_spark";
            case "donate_midnight_notebook" -> "ic_notebook";
            case "donate_wave_of_support" -> "ic_wave";
            case "donate_universe_of_inspiration" -> "ic_universe";
            default ->
                // Для невідомих/старих товарів використовуємо іконку серця
                    "ic_heart";
        };
    }

    private void updateProductsWithPurchaseStatus(List<Purchase> purchases) {
        // Збираємо список ID куплених товарів
        List<String> purchasedProductIds = new ArrayList<>();
        for (Purchase purchase : purchases) {
            if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                purchasedProductIds.addAll(purchase.getProducts());
            }
        }
        
        // Оновлюємо адаптер товарів
        if (donationAdapter != null) {
            donationAdapter.updatePurchasedProducts(purchasedProductIds);
        }
    }

    private String getProductTitle(String productId) {
        return switch (productId) {
            case "donate_seed_of_ideas" -> getString(R.string.donation_seed_short);
            case "donate_spark_of_inspiration" -> getString(R.string.donation_spark_short);
            case "donate_midnight_notebook" -> getString(R.string.donation_midnight_short);
            case "donate_wave_of_support" -> getString(R.string.donation_wave_short);
            case "donate_universe_of_inspiration" -> getString(R.string.donation_universe_short);
            default ->
                // Для невідомих/старих товарів даємо приємну назву
                    getString(R.string.donation_legacy_friend);
        };
    }

    private void showPurchaseSuccessDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_purchase_success, null);
        
        MaterialButton closeButton = view.findViewById(R.id.close_button);
        closeButton.setOnClickListener(v -> dialog.dismiss());
        
        dialog.setContentView(view);
        dialog.show();
    }
}

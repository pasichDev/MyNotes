package com.pasich.mynotes.ui.view.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
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
import com.pasich.mynotes.databinding.ActivitySupportBinding;
import com.pasich.mynotes.utils.adapters.DonationProductAdapter;

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
    
    // Поля для керування спойлерами
    private LinearLayout donationContent, purchasesContent, contactContent;
    private ImageView donationArrow, purchasesArrow, contactArrow;

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
            "donation", false
        );

        setupExpandableSection(
            findViewById(R.id.purchases_header),
            purchasesContent,
            purchasesArrow,
            "purchases",
                true
        );

        setupExpandableSection(
            findViewById(R.id.contact_header),
            contactContent,
            contactArrow,
            "contact",
            false
        );
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
            if (!isExpanded) {
                // Закриваємо всі інші спойлери і відкриваємо поточний
                closeAllSectionsExcept(sectionId);
                openSection(content, arrow);
            }
            // Якщо спойлер вже відкритий, не закриваємо його
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
        // Загружаем продукты для отображения
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
            }
        });
    }

    @Override
    public void onPurchaseSuccessful(String productId) {
        runOnUiThread(this::showPurchaseSuccessDialog);
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
        Log.d(TAG, "Purchases loaded: " + purchases.size());
        runOnUiThread(this::updateProductsWithPurchaseStatus);
    }

    private void updateProductsWithPurchaseStatus() {
        if (donationAdapter != null) {
            donationAdapter.updatePurchasedProducts(new ArrayList<>());
        }
    }

    private void showPurchaseSuccessDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        @SuppressLint("InflateParams") View view = getLayoutInflater().inflate(R.layout.dialog_purchase_success, null);
        
        MaterialButton closeButton = view.findViewById(R.id.close_button);
        closeButton.setOnClickListener(v -> dialog.dismiss());
        
        dialog.setContentView(view);
        dialog.show();
    }
}

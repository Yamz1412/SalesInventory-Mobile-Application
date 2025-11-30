package com.app.SalesInventory;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.List;

public class SellList extends BaseActivity  {
    private ListView sellListView;
    private SellAdapter sellAdapter;
    private List<Product> productList;
    private ProductRepository productRepository;
    private Button btnCheckout;
    private CartManager cartManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sell_list);
        productRepository = SalesInventoryApplication.getProductRepository();
        cartManager = CartManager.getInstance();
        sellListView = findViewById(R.id.SellListD);
        btnCheckout = findViewById(R.id.btnCheckout);
        productList = new ArrayList<>();
        sellAdapter = new SellAdapter(this, productList);
        sellListView.setAdapter(sellAdapter);
        loadProducts();
        setupListItemClick();
        setupCheckoutButton();
    }

    private void loadProducts() {
        productRepository.getAllProducts().observe(this, products -> {
            if (products != null) {
                productList.clear();
                productList.addAll(products);
                sellAdapter.notifyDataSetChanged();
            }
        });
    }

    private void setupListItemClick() {
        sellListView.setOnItemClickListener((parent, view, position, id) -> {
            Product selectedProduct = productList.get(position);
            showProductOptionsDialog(selectedProduct);
        });
    }

    private void setupCheckoutButton() {
        btnCheckout.setOnClickListener(v -> {
            if (cartManager.getItems().isEmpty()) {
                Toast.makeText(this, "Cart is empty", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(SellList.this, sellProduct.class);
            startActivity(intent);
        });
    }

    private void showProductOptionsDialog(Product product) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_product_options, null, false);
        TextView tvName = dialogView.findViewById(R.id.tvOptionProductName);
        TextView tvPrice = dialogView.findViewById(R.id.tvOptionProductPrice);
        RadioGroup rgSize = dialogView.findViewById(R.id.rgSize);
        RadioButton rbSmall = dialogView.findViewById(R.id.rbSizeSmall);
        RadioButton rbMedium = dialogView.findViewById(R.id.rbSizeMedium);
        RadioButton rbLarge = dialogView.findViewById(R.id.rbSizeLarge);
        CheckBox cbExtraShot = dialogView.findViewById(R.id.cbAddonExtraShot);
        CheckBox cbWhipped = dialogView.findViewById(R.id.cbAddonWhippedCream);
        CheckBox cbSyrup = dialogView.findViewById(R.id.cbAddonSyrup);
        TextInputEditText etQty = dialogView.findViewById(R.id.etOptionQty);
        Button btnCancel = dialogView.findViewById(R.id.btnOptionCancel);
        Button btnAddToCart = dialogView.findViewById(R.id.btnOptionAddToCart);

        tvName.setText(product.getProductName());
        tvPrice.setText("₱" + String.format("%.2f", product.getSellingPrice()));

        rbMedium.setChecked(true);
        etQty.setText("1");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnAddToCart.setOnClickListener(v -> {
            String size;
            int checkedId = rgSize.getCheckedRadioButtonId();
            if (checkedId == R.id.rbSizeSmall) {
                size = "Small";
            } else if (checkedId == R.id.rbSizeLarge) {
                size = "Large";
            } else {
                size = "Medium";
            }

            List<String> addonList = new ArrayList<>();
            if (cbExtraShot.isChecked()) addonList.add("Extra Shot");
            if (cbWhipped.isChecked()) addonList.add("Whipped Cream");
            if (cbSyrup.isChecked()) addonList.add("Syrup");
            String addon = "";
            if (!addonList.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < addonList.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(addonList.get(i));
                }
                addon = sb.toString();
            }

            String qtyStr = etQty.getText() != null ? etQty.getText().toString().trim() : "";
            int q;
            try {
                q = Integer.parseInt(qtyStr);
            } catch (Exception e) {
                Toast.makeText(this, "Invalid quantity", Toast.LENGTH_SHORT).show();
                return;
            }
            if (q <= 0 || q > product.getQuantity()) {
                Toast.makeText(this, "Quantity must be between 1 and " + product.getQuantity(), Toast.LENGTH_SHORT).show();
                return;
            }

            String displayName = product.getProductName() + " (" + size + ")";
            cartManager.addItem(
                    product.getProductId(),
                    displayName,
                    product.getSellingPrice(),
                    q,
                    product.getQuantity(),
                    size,
                    addon
            );

            Toast.makeText(this, "Added to cart", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }
}
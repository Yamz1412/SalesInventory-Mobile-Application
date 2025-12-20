package com.app.SalesInventory;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.firebase.auth.FirebaseAuth;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReceiptActivity extends AppCompatActivity {

    private TextView tvTitle;
    private TextView tvTotal;
    private TextView tvCash;
    private TextView tvChange;
    private TextView tvDeliveryInfo;
    private TextView tvEmail;
    private TextView tvDateTime;
    private Button btnConfirm;
    private ListView lvReceiptItems;
    private SalesRepository salesRepository;
    private ProductRepository productRepository;
    private CartManager cartManager;
    private double finalTotal;
    private double cashGiven;
    private double change;
    private String paymentMethod;
    private boolean isDelivery;
    private String deliveryName;
    private String deliveryPhone;
    private String deliveryAddress;
    private String deliveryPayment;
    private String receiptUriString;
    private String currentOrderId;
    private PDFGenerator pdfGenerator;
    private String buyerEmail;
    private String formattedDateTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt);
        salesRepository = SalesInventoryApplication.getSalesRepository();
        productRepository = SalesInventoryApplication.getProductRepository();
        cartManager = CartManager.getInstance();
        tvTitle = findViewById(R.id.tvReceiptTitle);
        tvTotal = findViewById(R.id.tvReceiptTotal);
        tvCash = findViewById(R.id.tvReceiptCash);
        tvChange = findViewById(R.id.tvReceiptChange);
        tvDeliveryInfo = findViewById(R.id.tvReceiptDelivery);
        tvEmail = findViewById(R.id.tvReceiptEmail);
        tvDateTime = findViewById(R.id.tvReceiptDate);
        btnConfirm = findViewById(R.id.btnReceiptConfirm);
        lvReceiptItems = findViewById(R.id.lvReceiptItems);
        Intent i = getIntent();
        paymentMethod = i.getStringExtra("paymentMethod");
        finalTotal = i.getDoubleExtra("finalTotal", 0);
        cashGiven = i.getDoubleExtra("cashGiven", 0);
        change = i.getDoubleExtra("change", 0);
        isDelivery = i.getBooleanExtra("isDelivery", false);
        deliveryName = i.getStringExtra("deliveryName");
        deliveryPhone = i.getStringExtra("deliveryPhone");
        deliveryAddress = i.getStringExtra("deliveryAddress");
        deliveryPayment = i.getStringExtra("deliveryPayment");
        receiptUriString = i.getStringExtra("receiptUri");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        formattedDateTime = sdf.format(new Date());
        try {
            if (FirebaseAuth.getInstance().getCurrentUser() != null && FirebaseAuth.getInstance().getCurrentUser().getEmail() != null) {
                buyerEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
            } else {
                buyerEmail = "";
            }
        } catch (Exception e) {
            buyerEmail = "";
        }
        populateReceipt();
        try {
            pdfGenerator = new PDFGenerator(this);
        } catch (Exception e) {
            pdfGenerator = null;
        }
        btnConfirm.setOnClickListener(v -> processAndRecordSale());
    }

    private void populateReceipt() {
        tvTitle.setText("RECEIPT");
        tvEmail.setText("Email: " + (buyerEmail != null && !buyerEmail.isEmpty() ? buyerEmail : "N/A"));
        tvDateTime.setText("Date: " + (formattedDateTime != null ? formattedDateTime : ""));
        tvTotal.setText(String.format(Locale.US, "Total: ₱%.2f", finalTotal));
        tvCash.setText(String.format(Locale.US, "Cash: ₱%.2f", cashGiven));
        tvChange.setText(String.format(Locale.US, "Change: ₱%.2f", change));
        if (isDelivery) {
            tvDeliveryInfo.setText("Delivery: " + deliveryName + " • " + deliveryPhone + "\n" + deliveryAddress + " • " + deliveryPayment);
            tvDeliveryInfo.setVisibility(android.view.View.VISIBLE);
        } else {
            tvDeliveryInfo.setVisibility(android.view.View.GONE);
        }
        List<CartManager.CartItem> items = cartManager.getItems();
        android.widget.BaseAdapter adapter = new android.widget.BaseAdapter() {
            @Override
            public int getCount() {
                return items.size();
            }
            @Override
            public Object getItem(int position) {
                return items.get(position);
            }
            @Override
            public long getItemId(int position) {
                return position;
            }
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                if (convertView == null) {
                    convertView = getLayoutInflater().inflate(R.layout.item_receipt_row, parent, false);
                }
                CartManager.CartItem item = items.get(position);
                TextView tvName = convertView.findViewById(R.id.tvReceiptItemName);
                TextView tvQty = convertView.findViewById(R.id.tvReceiptItemQty);
                TextView tvPrice = convertView.findViewById(R.id.tvReceiptItemPrice);
                tvName.setText(item.productName);
                tvQty.setText("x" + item.quantity);
                tvPrice.setText("₱" + String.format(Locale.US, "%.2f", item.getLineTotal()));
                return convertView;
            }
        };
        lvReceiptItems.setAdapter(adapter);
    }

    private void processAndRecordSale() {
        btnConfirm.setEnabled(false);
        List<CartManager.CartItem> items = cartManager.getItems();
        if (items.isEmpty()) {
            Toast.makeText(this, "Cart is empty", Toast.LENGTH_SHORT).show();
            btnConfirm.setEnabled(true);
            return;
        }
        currentOrderId = java.util.UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        String deliveryType;
        String deliveryStatus;
        long deliveryDate;
        if (isDelivery) {
            deliveryType = "DELIVERY";
            deliveryStatus = "PENDING";
            deliveryDate = 0;
        } else {
            deliveryType = "WALK_IN";
            deliveryStatus = "DELIVERED";
            deliveryDate = now;
        }
        double subtotal = cartManager.getSubtotal();
        final int totalItems = items.size();
        final int[] completed = {0};
        final boolean[] hadError = {false};
        for (CartManager.CartItem item : items) {
            double lineTotal = item.getLineTotal();
            double ratio = subtotal == 0 ? 0 : lineTotal / subtotal;
            double lineFinal = finalTotal * ratio;
            Sales sale = new Sales();
            sale.setOrderId(currentOrderId);
            sale.setProductId(item.productId);
            sale.setProductName(item.productName);
            sale.setQuantity(item.quantity);
            sale.setPrice(item.unitPrice);
            sale.setTotalPrice(lineFinal);
            sale.setPaymentMethod(paymentMethod);
            sale.setDate(now);
            sale.setTimestamp(now);
            sale.setDeliveryType(deliveryType);
            sale.setDeliveryStatus(deliveryStatus);
            sale.setDeliveryDate(deliveryDate);
            sale.setDeliveryName(isDelivery ? deliveryName : "");
            sale.setDeliveryPhone(isDelivery ? deliveryPhone : "");
            sale.setDeliveryAddress(isDelivery ? deliveryAddress : "");
            sale.setDeliveryPaymentMethod(isDelivery ? deliveryPayment : "");
            if (receiptUriString != null && !receiptUriString.isEmpty()) {
                sale.setReceiptUri(receiptUriString);
            } else if ("Cash".equals(paymentMethod)) {
                sale.setReceiptUri(Uri.fromParts("cash", String.valueOf(System.currentTimeMillis()), null).toString());
            }
            sale.setBuyerEmail(buyerEmail != null ? buyerEmail : "");
            sale.setDateTimeString(formattedDateTime != null ? formattedDateTime : "");
            Sales currentSale = sale;
            CartManager.CartItem currentItem = item;
            salesRepository.addSale(currentSale, new SalesRepository.OnSaleAddedListener() {
                @Override
                public void onSaleAdded(String saleId) {
                    currentSale.setId(saleId);
                    productRepository.updateProductQuantity(currentItem.productId, Math.max(0, currentItem.stock - currentItem.quantity), new ProductRepository.OnProductUpdatedListener() {
                        @Override
                        public void onProductUpdated() {
                            synchronized (completed) {
                                completed[0]++;
                                if (completed[0] >= totalItems) finalizeRecording(hadError[0]);
                            }
                        }
                        @Override
                        public void onError(String error) {
                            hadError[0] = true;
                            synchronized (completed) {
                                completed[0]++;
                                if (completed[0] >= totalItems) finalizeRecording(hadError[0]);
                            }
                        }
                    });
                }
                @Override
                public void onError(String error) {
                    hadError[0] = true;
                    synchronized (completed) {
                        completed[0]++;
                        if (completed[0] >= totalItems) finalizeRecording(hadError[0]);
                    }
                }
            });
        }
    }

    private void finalizeRecording(boolean hadError) {
        runOnUiThread(() -> {
            if (hadError) {
                Toast.makeText(this, "Some errors occurred while recording sale", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Sale recorded successfully", Toast.LENGTH_SHORT).show();
            }
            try {
                if (pdfGenerator != null) {
                    File pdf = pdfGenerator.generateReceiptPdf(currentOrderId, cartManager.getItems(), finalTotal, cashGiven, change, isDelivery, deliveryName, deliveryPhone, deliveryAddress, deliveryPayment, paymentMethod, receiptUriString);
                    if (pdf != null) {
                        Toast.makeText(this, "Receipt saved: " + pdf.getAbsolutePath(), Toast.LENGTH_LONG).show();
                    }
                }
            } catch (Exception e) {}
            cartManager.clear();
            Intent i = new Intent(ReceiptActivity.this, SellList.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        });
    }
}
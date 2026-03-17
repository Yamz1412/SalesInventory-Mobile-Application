package com.app.SalesInventory;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class MockDataInjector {

    private static final String[] SUPPLIERS = {
            "Global Bean Society",
            "Dairy Crafters Inc.",
            "Sweet Theory Syrups",
            "EcoPack Solutions",
            "Daily Bakehouse",
            "Brewline Merch Co.",
            "Tea & Leaves Co.",
            "RTD Bottlers"
    };

    private static final Object[][] RAW_MATERIALS = {
            {"Arabica Beans", "Raw Material", "Raw Material", "kg", 450.0, 0},
            {"Robusta Beans", "Raw Material", "Raw Material", "kg", 400.0, 0},
            {"Espresso Blend", "Raw Material", "Raw Material", "kg", 500.0, 0},
            {"Decaf Beans", "Raw Material", "Raw Material", "kg", 550.0, 0},
            {"Colombian Roast", "Raw Material", "Raw Material", "kg", 600.0, 0},
            {"Ethiopian Roast", "Raw Material", "Raw Material", "kg", 650.0, 0},
            {"French Roast", "Raw Material", "Raw Material", "kg", 480.0, 0},

            {"Bear Brand Powder", "Raw Material", "Raw Material", "kg", 200.0, 1},
            {"Fresh Milk", "Raw Material", "Raw Material", "L", 80.0, 1},
            {"Oat Milk", "Raw Material", "Raw Material", "L", 150.0, 1},
            {"Almond Milk", "Raw Material", "Raw Material", "L", 160.0, 1},
            {"Condensed Milk", "Raw Material", "Raw Material", "can", 45.0, 1},
            {"Evaporated Milk", "Raw Material", "Raw Material", "can", 35.0, 1},
            {"Heavy Cream", "Raw Material", "Raw Material", "L", 220.0, 1},

            {"Vanilla Syrup", "Raw Material", "Raw Material", "bottle", 300.0, 2},
            {"Caramel Syrup", "Raw Material", "Raw Material", "bottle", 300.0, 2},
            {"Hazelnut Syrup", "Raw Material", "Raw Material", "bottle", 300.0, 2},
            {"Mocha Sauce", "Raw Material", "Raw Material", "bottle", 350.0, 2},
            {"White Choco Sauce", "Raw Material", "Raw Material", "bottle", 350.0, 2},
            {"Cinnamon Powder", "Raw Material", "Raw Material", "kg", 150.0, 2},

            {"Hot Cups 12oz", "Packaging", "Raw Material", "pcs", 3.0, 3},
            {"Cold Cups 16oz", "Packaging", "Raw Material", "pcs", 4.0, 3},
            {"Cup Lids", "Packaging", "Raw Material", "pcs", 1.5, 3},
            {"Straws", "Packaging", "Raw Material", "pcs", 0.5, 3},
            {"Cup Sleeves", "Packaging", "Raw Material", "pcs", 2.0, 3},
            {"Takeout Bags", "Packaging", "Raw Material", "pcs", 5.0, 3},

            {"Croissant", "Food", "Retail", "pcs", 45.0, 4},
            {"Chocolate Chip Cookie", "Food", "Retail", "pcs", 35.0, 4},
            {"Blueberry Muffin", "Food", "Retail", "pcs", 55.0, 4},
            {"Cinnamon Roll (Raw)", "Food", "Retail", "pcs", 60.0, 4},
            {"Bagoong Bottle (Raw)", "Food", "Retail", "pcs", 40.0, 4},
            {"Coffee Jelly Base", "Food", "Raw Material", "kg", 120.0, 4},

            {"Coffee Mug", "Merchandise", "Retail", "pcs", 150.0, 5},
            {"Tumbler", "Merchandise", "Retail", "pcs", 250.0, 5},
            {"T-Shirt", "Merchandise", "Retail", "pcs", 300.0, 5},
            {"Tote Bag", "Merchandise", "Retail", "pcs", 100.0, 5},
            {"Brewing Kit", "Merchandise", "Retail", "pcs", 800.0, 5},
            {"Filter Paper", "Merchandise", "Retail", "pcs", 50.0, 5},

            {"Matcha Powder", "Raw Material", "Raw Material", "kg", 800.0, 6},
            {"Earl Grey Tea", "Raw Material", "Raw Material", "box", 250.0, 6},
            {"Chamomile Tea", "Raw Material", "Raw Material", "box", 250.0, 6},
            {"Green Tea", "Raw Material", "Raw Material", "box", 200.0, 6},
            {"Black Tea", "Raw Material", "Raw Material", "box", 200.0, 6},
            {"Chai Tea", "Raw Material", "Raw Material", "box", 280.0, 6},

            {"Mineral Water", "Beverage", "Retail", "bottle", 15.0, 7},
            {"Sparkling Water", "Beverage", "Retail", "bottle", 45.0, 7},
            {"Cold Brew RTD", "Beverage", "Retail", "bottle", 80.0, 7},
            {"Apple Juice", "Beverage", "Retail", "bottle", 50.0, 7},
            {"Orange Juice", "Beverage", "Retail", "bottle", 50.0, 7},
            {"Soda", "Beverage", "Retail", "can", 30.0, 7}
    };

    private static final Object[][] MENU_ITEMS = {
            {"Matcha Latte", "COFFEE", "Menu", 69.0, 25.0},
            {"Coffe Jelly Frappe", "COFFEE", "Menu", 100.0, 40.0},
            {"Cinnamon Rillr", "COFFEE", "Menu", 150.0, 60.0},
            {"Bear Brand Milk", "MILK", "Menu", 250.0, 180.0},
            {"Bear Brand", "MILK", "Menu", 50.0, 25.0},
            {"Bagoong", "ALL", "Menu", 60.0, 40.0},
            {"Espresso Shot", "ADD-ON", "Menu", 30.0, 10.0},
            {"Extra Milk", "ADD-ON", "Menu", 20.0, 8.0},
            {"Extra Syrup", "ADD-ON", "Menu", 15.0, 5.0}
    };

    public static void injectHanZaiDefenseData(Context context, Runnable onComplete) {
        Toast.makeText(context, "Injecting 1 Year of Data. Please wait...", Toast.LENGTH_LONG).show();

        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null) {
            Toast.makeText(context, "Error: No user logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DatabaseReference rtDb = FirebaseDatabase.getInstance().getReference();
        Random random = new Random();

        resetHanZaiDefenseData(context, () -> {

            WriteBatch batch = db.batch();
            List<Map<String, Object>> generatedProducts = new ArrayList<>();
            List<Map<String, Object>> generatedMenu = new ArrayList<>();

            for (Object[] rm : RAW_MATERIALS) {
                String id = UUID.randomUUID().toString();
                String supplierName = SUPPLIERS[(int) rm[5]];

                Map<String, Object> p = createProductMap(id, (String)rm[0], (String)rm[1], (String)rm[2],
                        100 + random.nextInt(200), (double)rm[4] * 1.5, (double)rm[4], (String)rm[3], supplierName);

                DocumentReference pRef = db.collection("users").document(ownerId).collection("products").document(id);
                batch.set(pRef, p);
                generatedProducts.add(p);
            }

            for (Object[] mi : MENU_ITEMS) {
                String id = UUID.randomUUID().toString();
                Map<String, Object> m = createProductMap(id, (String)mi[0], (String)mi[1], (String)mi[2],
                        0, (double)mi[3], (double)mi[4], "serving", "In-House");

                DocumentReference mRef = db.collection("users").document(ownerId).collection("products").document(id);
                batch.set(mRef, m);
                generatedMenu.add(m);
            }

            long now = System.currentTimeMillis();
            long oneYearAgo = now - (365L * 24 * 60 * 60 * 1000);

            for (int i = 0; i < 250; i++) {
                Map<String, Object> saleProduct = generatedMenu.get(random.nextInt(generatedMenu.size()));

                double qty = 1 + random.nextInt(3);
                double price = (double) saleProduct.get("sellingPrice");
                double cost = (double) saleProduct.get("costPrice");

                long saleTime = oneYearAgo + (long)(random.nextDouble() * (now - oneYearAgo));
                boolean isDelivery = random.nextDouble() > 0.8;

                Map<String, Object> sale = new HashMap<>();
                sale.put("orderId", "ORD-" + String.format("%05d", random.nextInt(99999)));
                sale.put("productId", saleProduct.get("productId"));
                sale.put("productName", saleProduct.get("productName"));
                sale.put("quantity", qty);
                sale.put("price", price);
                sale.put("totalPrice", price * qty);
                sale.put("totalCost", cost * qty);
                sale.put("discountAmount", random.nextDouble() > 0.9 ? (price * qty * 0.1) : 0.0);
                sale.put("paymentMethod", random.nextBoolean() ? "Cash" : "GCash");
                sale.put("timestamp", saleTime);
                sale.put("date", saleTime);

                if (isDelivery) {
                    sale.put("deliveryType", "DELIVERY");
                    sale.put("deliveryStatus", random.nextBoolean() ? "DELIVERED" : "PENDING");
                    sale.put("deliveryName", "Walk-in Customer " + i);
                    sale.put("deliveryDate", saleTime + (60 * 60 * 1000));
                } else {
                    sale.put("deliveryType", "Dine-In");
                }

                DocumentReference sRef = db.collection("users").document(ownerId).collection("sales").document();
                sale.put("id", sRef.getId());
                batch.set(sRef, sale);
            }

            DatabaseReference supplierRef = rtDb.child("Suppliers");
            for (int i = 0; i < SUPPLIERS.length; i++) {
                String sName = SUPPLIERS[i];
                Map<String, Object> sProfile = new HashMap<>();
                sProfile.put("name", sName);
                sProfile.put("ownerAdminId", ownerId);
                sProfile.put("email", sName.toLowerCase().replace(" ", "") + "@supplier.com");
                sProfile.put("phone", "0912345678" + i);
                sProfile.put("address", "Supplier Address " + (i + 1));
                sProfile.put("categories", "Raw Materials, Wholesale");
                supplierRef.push().setValue(sProfile);
            }

            batch.commit().addOnSuccessListener(aVoid -> {

                for (int i = 0; i < 24; i++) {
                    int supplierIndex = random.nextInt(SUPPLIERS.length);
                    String supplierName = SUPPLIERS[supplierIndex];
                    long poTime = oneYearAgo + (long)(random.nextDouble() * (now - oneYearAgo));

                    List<Map<String, Object>> supplierProducts = new ArrayList<>();
                    for (Map<String, Object> p : generatedProducts) {
                        if (supplierName.equals(p.get("supplier"))) {
                            supplierProducts.add(p);
                        }
                    }

                    double poTotal = 0;
                    List<Map<String, Object>> poItems = new ArrayList<>();

                    int itemsInPo = 2 + random.nextInt(3);
                    for (int j = 0; j < itemsInPo && j < supplierProducts.size(); j++) {
                        Map<String, Object> sp = supplierProducts.get(j);
                        double qty = 10 + random.nextInt(50);
                        double cost = (double) sp.get("costPrice");
                        poTotal += (qty * cost);

                        Map<String, Object> item = new HashMap<>();
                        item.put("productName", sp.get("productName"));
                        item.put("quantity", qty);
                        item.put("cost", cost);
                        poItems.add(item);
                    }

                    Map<String, Object> po = new HashMap<>();
                    po.put("poNumber", "PO-" + String.format("%04d", random.nextInt(9999)));
                    po.put("supplierName", supplierName);
                    po.put("orderDate", poTime);
                    po.put("totalAmount", poTotal);
                    po.put("status", random.nextDouble() > 0.2 ? "RECEIVED" : "PARTIAL");
                    po.put("ownerAdminId", ownerId);
                    po.put("items", poItems);

                    rtDb.child("PurchaseOrders").push().setValue(po);
                }

                String[] reasons = {"Spoilage", "Damaged in Transit", "Manual Count Error", "Expired"};
                for (int i = 0; i < 15; i++) {
                    Map<String, Object> p = generatedProducts.get(random.nextInt(generatedProducts.size()));
                    long adjTime = oneYearAgo + (long)(random.nextDouble() * (now - oneYearAgo));

                    Map<String, Object> adj = new HashMap<>();
                    adj.put("productId", p.get("productId"));
                    adj.put("productName", p.get("productName"));
                    adj.put("adjustmentType", "Remove Stock");
                    adj.put("quantityAdjusted", -1.0 - random.nextInt(5));
                    adj.put("reason", reasons[random.nextInt(reasons.length)]);
                    adj.put("timestamp", adjTime);
                    adj.put("dateLogged", adjTime);
                    adj.put("ownerAdminId", ownerId);
                    adj.put("adjustedBy", "System Injector");

                    rtDb.child("StockAdjustments").push().setValue(adj);
                }

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (onComplete != null) onComplete.run();
                });

            });
        });
    }

    public static void resetHanZaiDefenseData(Context context, Runnable onComplete) {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DatabaseReference rtDb = FirebaseDatabase.getInstance().getReference();

        ProductRepository.getInstance((Application) context.getApplicationContext()).clearLocalData();

        db.collection("users").document(ownerId).collection("sales").get().addOnSuccessListener(snapshot -> {
            WriteBatch batch = db.batch();
            for (com.google.firebase.firestore.DocumentSnapshot ds : snapshot.getDocuments()) batch.delete(ds.getReference());
            batch.commit();
        });

        db.collection("users").document(ownerId).collection("products").get().addOnSuccessListener(snapshot -> {
            WriteBatch batch = db.batch();
            for (com.google.firebase.firestore.DocumentSnapshot ds : snapshot.getDocuments()) batch.delete(ds.getReference());
            batch.commit();
        });

        rtDb.child("StockAdjustments").orderByChild("ownerAdminId").equalTo(ownerId).addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
            @Override public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                for (com.google.firebase.database.DataSnapshot ds : snapshot.getChildren()) ds.getRef().removeValue();
            }
            @Override public void onCancelled(com.google.firebase.database.DatabaseError error) {}
        });

        rtDb.child("PurchaseOrders").orderByChild("ownerAdminId").equalTo(ownerId).addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
            @Override public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                for (com.google.firebase.database.DataSnapshot ds : snapshot.getChildren()) ds.getRef().removeValue();
            }
            @Override public void onCancelled(com.google.firebase.database.DatabaseError error) {}
        });

        rtDb.child("Suppliers").orderByChild("ownerAdminId").equalTo(ownerId).addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
            @Override public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                for (com.google.firebase.database.DataSnapshot ds : snapshot.getChildren()) ds.getRef().removeValue();
            }
            @Override public void onCancelled(com.google.firebase.database.DatabaseError error) {}
        });

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (onComplete != null) onComplete.run();
        }, 1500);
    }

    private static Map<String, Object> createProductMap(String id, String name,
                                                        String category, String type, int qty,
                                                        double sellPrice, double costPrice, String unit, String supplier) {
        Map<String, Object> p = new HashMap<>();
        p.put("productId",    id);
        p.put("productName",  name);
        p.put("categoryName", category);
        p.put("productType",  type);
        p.put("quantity",     (double) qty);
        p.put("sellingPrice", sellPrice);
        p.put("costPrice",    costPrice);
        p.put("unit",         unit);
        p.put("supplier",     supplier);
        p.put("active",       true);
        p.put("isActive",     true);
        p.put("ownerAdminId", FirestoreManager.getInstance().getBusinessOwnerId());
        p.put("dateAdded",    System.currentTimeMillis());
        return p;
    }
}
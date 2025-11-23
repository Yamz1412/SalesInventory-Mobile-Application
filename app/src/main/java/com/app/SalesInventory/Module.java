package com.app.SalesInventory;

import android.app.Application;
import android.widget.ArrayAdapter;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;

public class Module extends Application {

    public ArrayList<String> list = new ArrayList<>();
    public ArrayAdapter<String> listAdapter;
    public String itemID;
    public String ItemName;

    @Override
    public void onCreate() {
        super.onCreate();

        FirebaseApp.initializeApp(this);

        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        FirebaseDatabase.getInstance().getReference("Product").keepSynced(true);
        FirebaseDatabase.getInstance().getReference("Sales").keepSynced(true);
    }

    public String getItemID() {
        return itemID;
    }

    public void setItemID(String itemID) {
        this.itemID = itemID;
    }

    public String getItemName() {
        return ItemName;
    }

    public void setItemName(String itemName) {
        ItemName = itemName;
    }

    public void setItemID(Product product) {
    }
}
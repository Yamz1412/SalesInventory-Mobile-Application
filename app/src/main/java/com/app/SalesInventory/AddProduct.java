package com.app.SalesInventory;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.DateFormat;
import java.util.Calendar;

public class AddProduct extends AppCompatActivity {

    EditText name, lot, code, price, sellPrice, amount, expiryDate, minStock;
    Spinner categorySpinner;
    Button saveBtn;
    DatabaseReference Productref, Historyref;
    Product mbr;
    String UserID;
    FirebaseAuth fAuth;

    Calendar calendar = Calendar.getInstance();
    String date = DateFormat.getDateInstance().format(calendar.getTime());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_product); // Note: Using activity_main layout

        // Init Views
        name = findViewById(R.id.Name);
        lot = findViewById(R.id.Lot);
        code = findViewById(R.id.Code);
        price = findViewById(R.id.Price);
        sellPrice = findViewById(R.id.SellPrice);
        amount = findViewById(R.id.Amount);
        expiryDate = findViewById(R.id.ExpiryDate); // NEW
        minStock = findViewById(R.id.MinStock); // NEW
        categorySpinner = findViewById(R.id.CategorySpinner); // NEW

        saveBtn = findViewById(R.id.Save);

        fAuth = FirebaseAuth.getInstance();
        UserID = fAuth.getCurrentUser().getUid();

        Productref = FirebaseDatabase.getInstance().getReference("Product");
        Historyref = FirebaseDatabase.getInstance().getReference("History");

        String[] categories = {"Select Category", "Drinks", "Pastries", "Meals", "Ingredients", "Others"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item, categories);
        adapter.setDropDownViewResource(R.layout.spinner_item);
        categorySpinner.setAdapter(adapter);

        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String strName = name.getText().toString().trim();
                String strLot = lot.getText().toString().trim();
                String strCode = code.getText().toString().trim();
                String strPrice = price.getText().toString().trim();
                String strSellPrice = sellPrice.getText().toString().trim();
                String strAmount = amount.getText().toString().trim();
                String strExpiry = expiryDate.getText().toString().trim();
                String strMinStock = minStock.getText().toString().trim();
                String strCategory = categorySpinner.getSelectedItem().toString();


                if (TextUtils.isEmpty(strName)) { name.setError("Required"); return; }
                if (TextUtils.isEmpty(strAmount)) { amount.setError("Required"); return; }
                if (strCategory.equals("Select Category")) {
                    Toast.makeText(AddProduct.this, "Please select a category", Toast.LENGTH_SHORT).show();
                    return;
                }

                Product newProduct = new Product(
                        strName, strLot, strCode, strPrice, strSellPrice,
                        UserID, strAmount, date, strCategory, strMinStock, strExpiry
                );

                String key = strName + strCode;
                Productref.child(key).setValue(newProduct);
                Historyref.push().setValue(newProduct);
                Toast.makeText(AddProduct.this, "Product Added!", Toast.LENGTH_LONG).show();

                name.getText().clear();
                lot.getText().clear();
                code.getText().clear();
                price.getText().clear();
                sellPrice.getText().clear();
                amount.getText().clear();
            }
        });


    }
}


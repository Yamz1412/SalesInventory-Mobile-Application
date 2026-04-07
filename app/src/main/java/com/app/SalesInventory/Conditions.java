package com.app.SalesInventory;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

public class Conditions extends BaseActivity  {

    Button btnAgree;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conditions);

        // FIX: Force the top-left back arrow to appear
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Terms and Conditions");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        btnAgree = findViewById(R.id.btnAgreeTerms);

        btnAgree.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                setResult(RESULT_OK, intent);
                finish();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // If they click back, it means they cancelled/declined the terms
            setResult(RESULT_CANCELED);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
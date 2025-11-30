package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.List;

public class CategoryManagementActivity extends BaseActivity  {

    private RecyclerView recyclerView;
    private CategoryAdapter adapter;
    private List<Category> categoryList;
    private FloatingActionButton fabAddCategory;
    private DatabaseReference categoryRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_management);
        recyclerView = findViewById(R.id.recyclerViewCategories);
        fabAddCategory = findViewById(R.id.fabAddCategory);
        categoryList = new ArrayList<>();
        adapter = new CategoryAdapter(this, categoryList, this::showEditDialog, this::deleteCategory);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        categoryRef = FirebaseDatabase.getInstance().getReference("Categories");
        loadCategories();
        fabAddCategory.setOnClickListener(v -> showAddCategoryDialog());
    }

    private void loadCategories() {
        categoryRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                categoryList.clear();
                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    Category category = dataSnapshot.getValue(Category.class);
                    if (category != null) categoryList.add(category);
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CategoryManagementActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupTypeSpinner(Spinner spinner, String currentType) {
        List<String> types = new ArrayList<>();
        types.add("Inventory");
        types.add("Menu");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, types);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        int index = 0;
        if (currentType != null && currentType.equalsIgnoreCase("Menu")) index = 1;
        spinner.setSelection(index);
    }

    private void showAddCategoryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_category, null);
        builder.setView(dialogView);
        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        EditText etCategoryName = dialogView.findViewById(R.id.etCategoryName);
        EditText etDescription = dialogView.findViewById(R.id.etDescription);
        Spinner spinnerType = dialogView.findViewById(R.id.spinnerCategoryType);
        SwitchMaterial switchActive = dialogView.findViewById(R.id.switchActive);
        Button btnSave = dialogView.findViewById(R.id.btnSave);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        tvTitle.setText("Add Category");
        setupTypeSpinner(spinnerType, "Inventory");
        switchActive.setChecked(true);
        AlertDialog dialog = builder.create();
        btnSave.setOnClickListener(v -> {
            String name = etCategoryName.getText().toString().trim();
            String description = etDescription.getText().toString().trim();
            if (name.isEmpty()) {
                etCategoryName.setError("Category name is required");
                return;
            }
            String selectedType = spinnerType.getSelectedItem() != null ? spinnerType.getSelectedItem().toString() : "Inventory";
            String categoryId = categoryRef.push().getKey();
            if (categoryId == null) {
                Toast.makeText(this, "Error generating ID", Toast.LENGTH_SHORT).show();
                return;
            }
            Category category = new Category(categoryId, name, description, System.currentTimeMillis());
            category.setType(selectedType);
            category.setActive(switchActive.isChecked());
            categoryRef.child(categoryId).setValue(category)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(CategoryManagementActivity.this, "Category added successfully", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    })
                    .addOnFailureListener(e -> Toast.makeText(CategoryManagementActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showEditDialog(Category category) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_category, null);
        builder.setView(dialogView);
        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        EditText etCategoryName = dialogView.findViewById(R.id.etCategoryName);
        EditText etDescription = dialogView.findViewById(R.id.etDescription);
        Spinner spinnerType = dialogView.findViewById(R.id.spinnerCategoryType);
        SwitchMaterial switchActive = dialogView.findViewById(R.id.switchActive);
        Button btnSave = dialogView.findViewById(R.id.btnSave);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        tvTitle.setText("Edit Category");
        etCategoryName.setText(category.getCategoryName());
        etDescription.setText(category.getDescription());
        setupTypeSpinner(spinnerType, category.getType() != null ? category.getType() : "Inventory");
        switchActive.setChecked(category.isActive());
        btnSave.setText("Update");
        AlertDialog dialog = builder.create();
        btnSave.setOnClickListener(v -> {
            String name = etCategoryName.getText().toString().trim();
            String description = etDescription.getText().toString().trim();
            if (name.isEmpty()) {
                etCategoryName.setError("Category name is required");
                return;
            }
            String selectedType = spinnerType.getSelectedItem() != null ? spinnerType.getSelectedItem().toString() : "Inventory";
            category.setCategoryName(name);
            category.setDescription(description);
            category.setType(selectedType);
            category.setActive(switchActive.isChecked());
            categoryRef.child(category.getCategoryId()).setValue(category)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(CategoryManagementActivity.this, "Category updated successfully", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    })
                    .addOnFailureListener(e -> Toast.makeText(CategoryManagementActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void deleteCategory(Category category) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Category")
                .setMessage("Are you sure you want to delete this category?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    categoryRef.child(category.getCategoryId()).removeValue()
                            .addOnSuccessListener(aVoid -> Toast.makeText(CategoryManagementActivity.this, "Category deleted", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(CategoryManagementActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
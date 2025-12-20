package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class InventoryMovementsActivity extends BaseActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvNoData;
    private Button btnExportCsv;
    private Button btnRefresh;

    private InventoryMovementAdapter adapter;
    private List<InventoryMovement> movements;
    private InventoryMovementsRepository repository;
    private ReportExportUtil exportUtil;
    private CSVGenerator csvGenerator;

    private static final int PERMISSION_REQUEST_CODE = 500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory_movements);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Inventory Movements");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        recyclerView = findViewById(R.id.recyclerViewMovements);
        progressBar = findViewById(R.id.progressBarMovements);
        tvNoData = findViewById(R.id.tvNoMovements);
        btnExportCsv = findViewById(R.id.btnExportMovementsCsv);
        btnRefresh = findViewById(R.id.btnRefreshMovements);
        movements = new ArrayList<>();
        adapter = new InventoryMovementAdapter(movements);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        repository = InventoryMovementsRepository.getInstance(getApplication());
        exportUtil = new ReportExportUtil(this);
        csvGenerator = new CSVGenerator();
        btnExportCsv.setOnClickListener(v -> {
            if (ensureWritePermission()) exportMovementsCsv();
        });
        btnRefresh.setOnClickListener(v -> refresh());
        observeMovements();
    }

    private void observeMovements() {
        progressBar.setVisibility(View.VISIBLE);
        repository.getAllMovements().observe(this, new Observer<List<InventoryMovement>>() {
            @Override
            public void onChanged(List<InventoryMovement> list) {
                movements.clear();
                if (list != null) movements.addAll(list);
                adapter.notifyDataSetChanged();
                progressBar.setVisibility(View.GONE);
                if (movements.isEmpty()) {
                    tvNoData.setVisibility(View.VISIBLE);
                } else {
                    tvNoData.setVisibility(View.GONE);
                }
            }
        });
    }

    private void refresh() {
        progressBar.setVisibility(View.VISIBLE);
        repository.shutdown();
        repository = InventoryMovementsRepository.getInstance(getApplication());
        observeMovements();
    }

    private void exportMovementsCsv() {
        try {
            File dir = exportUtil.getExportDirectory();
            if (dir == null) {
                exportUtil.showExportError("Unable to access export directory");
                return;
            }
            String fname = exportUtil.generateFileName("InventoryMovements", ReportExportUtil.EXPORT_CSV);
            ReportExportUtil.ExportResult r = exportUtil.createOutputStreamForFile(fname, ReportExportUtil.EXPORT_CSV);
            if (r == null || r.outputStream == null) throw new Exception("Unable to obtain output stream");
            try {
                csvGenerator.generateInventoryMovementsCSV(r.outputStream, movements);
                exportUtil.showExportSuccess(r.displayPath);
            } finally {
                try { r.outputStream.close(); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            exportUtil.showExportError(e.getMessage() == null ? "Export failed" : e.getMessage());
        }
    }

    private boolean ensureWritePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true;
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) return true;
        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                exportMovementsCsv();
            } else {
                Toast.makeText(this, "Storage permission required to export", Toast.LENGTH_LONG).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
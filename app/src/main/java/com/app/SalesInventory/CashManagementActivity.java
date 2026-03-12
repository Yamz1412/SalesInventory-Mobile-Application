package com.app.SalesInventory;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CashManagementActivity extends BaseActivity {

    // --- UI Components from XML ---
    private Toolbar toolbar;
    private TextView tvTotalCash, tvCurrentDate;
    private TabLayout tabLayout;

    // Layout Containers for Tabs
    private LinearLayout layoutSummary, layoutFundTransfer, layoutTransactions;

    // Summary Tab
    private RecyclerView rvWallets;
    private ExtendedFloatingActionButton fabAddWallet;

    // Fund Transfer Tab
    private AutoCompleteTextView spinFromWallet, spinToWallet;
    private TextView tvFromBalance, tvToBalance;
    private TextInputEditText etTransferAmount;
    private Button btnCancelTransfer, btnSaveTransfer;

    // Transactions Tab
    private RecyclerView rvTransactions;
    private Button btnTransactionReport;

    // --- Data & Firebase ---
    private FirebaseFirestore db;
    private String ownerId;
    private NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
    private SimpleDateFormat timeFormat = new SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault());

    // Lists & Adapters
    private List<Wallet> walletList = new ArrayList<>();
    private WalletAdapter walletAdapter;

    private List<CashTransaction> transactionList = new ArrayList<>();
    private CashTransactionAdapter transactionAdapter;

    // Transfer State
    private Wallet selectedFromWallet = null;
    private Wallet selectedToWallet = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cash_management);

        db = FirebaseFirestore.getInstance();
        ownerId = FirestoreManager.getInstance().getBusinessOwnerId();

        initUI();
        setupTabs();
        setupRecyclerViews();

        tvCurrentDate.setText(dateFormat.format(new Date()));

        loadWallets();
        loadTransactions();

        setupListeners();
    }

    private void initUI() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tvTotalCash = findViewById(R.id.tvTotalCash);
        tvCurrentDate = findViewById(R.id.tvCurrentDate);
        tabLayout = findViewById(R.id.tabLayout);

        layoutSummary = findViewById(R.id.layout_summary);
        layoutFundTransfer = findViewById(R.id.layout_fund_transfer);
        layoutTransactions = findViewById(R.id.layout_transactions);

        rvWallets = findViewById(R.id.rvWallets);
        fabAddWallet = findViewById(R.id.fabAddWallet);

        spinFromWallet = findViewById(R.id.spinFromWallet);
        spinToWallet = findViewById(R.id.spinToWallet);
        tvFromBalance = findViewById(R.id.tvFromBalance);
        tvToBalance = findViewById(R.id.tvToBalance);
        etTransferAmount = findViewById(R.id.etTransferAmount);
        btnCancelTransfer = findViewById(R.id.btnCancelTransfer);
        btnSaveTransfer = findViewById(R.id.btnSaveTransfer);

        rvTransactions = findViewById(R.id.rvTransactions);
        btnTransactionReport = findViewById(R.id.btnTransactionReport);
    }

    private void setupTabs() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                layoutSummary.setVisibility(View.GONE);
                layoutFundTransfer.setVisibility(View.GONE);
                layoutTransactions.setVisibility(View.GONE);
                fabAddWallet.setVisibility(View.GONE);

                switch (tab.getPosition()) {
                    case 0: // Summary
                        layoutSummary.setVisibility(View.VISIBLE);
                        fabAddWallet.setVisibility(View.VISIBLE);
                        break;
                    case 1: // Fund Transfer
                        layoutFundTransfer.setVisibility(View.VISIBLE);
                        break;
                    case 2: // Transactions
                        layoutTransactions.setVisibility(View.VISIBLE);
                        break;
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupRecyclerViews() {
        rvWallets.setLayoutManager(new LinearLayoutManager(this));
        walletAdapter = new WalletAdapter(walletList, w -> showWalletDetailsDialog(w));
        rvWallets.setAdapter(walletAdapter);

        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        transactionAdapter = new CashTransactionAdapter(transactionList);
        rvTransactions.setAdapter(transactionAdapter);
    }

    private void setupListeners() {
        fabAddWallet.setOnClickListener(v -> showAddWalletDialog());

        btnCancelTransfer.setOnClickListener(v -> {
            spinFromWallet.setText("");
            spinToWallet.setText("");
            etTransferAmount.setText("");
            tvFromBalance.setText("Available Balance: ₱ 0.00");
            tvToBalance.setText("Available Balance: ₱ 0.00");
            selectedFromWallet = null;
            selectedToWallet = null;
        });

        btnSaveTransfer.setOnClickListener(v -> processFundTransfer());

        // Setup Spinners for Fund Transfer
        spinFromWallet.setOnItemClickListener((parent, view, position, id) -> {
            String selectedName = (String) parent.getItemAtPosition(position);
            selectedFromWallet = getWalletByName(selectedName);
            if (selectedFromWallet != null) {
                tvFromBalance.setText("Available Balance: " + currencyFormat.format(selectedFromWallet.balance));
            }
        });

        spinToWallet.setOnItemClickListener((parent, view, position, id) -> {
            String selectedName = (String) parent.getItemAtPosition(position);
            selectedToWallet = getWalletByName(selectedName);
            if (selectedToWallet != null) {
                tvToBalance.setText("Available Balance: " + currencyFormat.format(selectedToWallet.balance));
            }
        });
    }

    // ==========================================
    // DATA LOADING
    // ==========================================
    private void loadWallets() {
        if (ownerId == null) return;
        db.collection("users").document(ownerId).collection("wallets")
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;

                    walletList.clear();
                    double totalCash = 0.0;
                    List<String> walletNames = new ArrayList<>();

                    for (DocumentSnapshot doc : value.getDocuments()) {
                        Wallet w = new Wallet();
                        w.id = doc.getId();
                        w.name = doc.getString("name");
                        w.type = doc.getString("type");
                        w.balance = doc.getDouble("balance") != null ? doc.getDouble("balance") : 0.0;
                        w.accountName = doc.getString("accountName");
                        w.accountNumber = doc.getString("accountNumber");

                        walletList.add(w);
                        walletNames.add(w.name);
                        totalCash += w.balance;
                    }

                    tvTotalCash.setText(currencyFormat.format(totalCash));
                    walletAdapter.notifyDataSetChanged();

                    // Update Transfer Spinners
                    ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, walletNames);
                    spinFromWallet.setAdapter(spinnerAdapter);
                    spinToWallet.setAdapter(spinnerAdapter);
                });
    }

    private void loadTransactions() {
        if (ownerId == null) return;
        db.collection("users").document(ownerId).collection("cash_transactions")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    transactionList.clear();
                    for (DocumentSnapshot doc : value.getDocuments()) {
                        CashTransaction t = new CashTransaction();
                        t.id = doc.getId();
                        t.title = doc.getString("title");
                        t.date = doc.getString("date");
                        t.amount = doc.getDouble("amount") != null ? doc.getDouble("amount") : 0.0;
                        t.isIncome = doc.getBoolean("isIncome") != null ? doc.getBoolean("isIncome") : true;
                        t.walletId = doc.getString("walletId");
                        t.timestamp = doc.getLong("timestamp") != null ? doc.getLong("timestamp") : 0L;
                        transactionList.add(t);
                    }
                    transactionAdapter.notifyDataSetChanged();
                });
    }

    private Wallet getWalletByName(String name) {
        for (Wallet w : walletList) {
            if (w.name.equals(name)) return w;
        }
        return null;
    }

    // ==========================================
    // FUND TRANSFER LOGIC
    // ==========================================
    private void processFundTransfer() {
        if (selectedFromWallet == null) {
            spinFromWallet.setError("Select source wallet");
            return;
        }
        if (selectedToWallet == null) {
            spinToWallet.setError("Select destination wallet");
            return;
        }
        if (selectedFromWallet.id.equals(selectedToWallet.id)) {
            Toast.makeText(this, "Cannot transfer to the same wallet", Toast.LENGTH_SHORT).show();
            return;
        }

        String amountStr = etTransferAmount.getText().toString().trim();
        if (amountStr.isEmpty()) {
            etTransferAmount.setError("Enter amount");
            return;
        }

        double amount = Double.parseDouble(amountStr);
        if (amount <= 0) {
            etTransferAmount.setError("Amount must be greater than 0");
            return;
        }
        if (amount > selectedFromWallet.balance) {
            etTransferAmount.setError("Insufficient balance");
            return;
        }

        DocumentReference fromRef = db.collection("users").document(ownerId).collection("wallets").document(selectedFromWallet.id);
        DocumentReference toRef = db.collection("users").document(ownerId).collection("wallets").document(selectedToWallet.id);

        db.runTransaction(transaction -> {
            DocumentSnapshot fromSnap = transaction.get(fromRef);
            DocumentSnapshot toSnap = transaction.get(toRef);

            double fromNewBal = fromSnap.getDouble("balance") - amount;
            double toNewBal = toSnap.getDouble("balance") + amount;

            transaction.update(fromRef, "balance", fromNewBal);
            transaction.update(toRef, "balance", toNewBal);
            return null;
        }).addOnSuccessListener(aVoid -> {
            long ts = System.currentTimeMillis();
            String dateStr = timeFormat.format(new Date());

            // Log Deduction
            Map<String, Object> logFrom = new HashMap<>();
            logFrom.put("title", "Transfer to " + selectedToWallet.name);
            logFrom.put("date", dateStr);
            logFrom.put("amount", amount);
            logFrom.put("isIncome", false);
            logFrom.put("walletId", selectedFromWallet.id);
            logFrom.put("timestamp", ts);

            // Log Addition
            Map<String, Object> logTo = new HashMap<>();
            logTo.put("title", "Transfer from " + selectedFromWallet.name);
            logTo.put("date", dateStr);
            logTo.put("amount", amount);
            logTo.put("isIncome", true);
            logTo.put("walletId", selectedToWallet.id);
            logTo.put("timestamp", ts + 1);

            db.collection("users").document(ownerId).collection("cash_transactions").add(logFrom);
            db.collection("users").document(ownerId).collection("cash_transactions").add(logTo);

            Toast.makeText(this, "Transfer Successful", Toast.LENGTH_SHORT).show();
            btnCancelTransfer.performClick(); // Reset form
            tabLayout.getTabAt(0).select(); // Go back to summary
        }).addOnFailureListener(e -> Toast.makeText(this, "Transfer failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // ==========================================
    // DIALOGS
    // ==========================================
    private void showAddWalletDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_wallet, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        Spinner spinnerType = view.findViewById(R.id.spinnerWalletType);
        View layoutGcashFields = view.findViewById(R.id.layoutGcashFields);
        TextInputEditText etName = view.findViewById(R.id.etGcashName);
        TextInputEditText etNumber = view.findViewById(R.id.etGcashNumber);
        TextInputEditText etBalance = view.findViewById(R.id.etInitialBalance);
        Button btnCancel = view.findViewById(R.id.btnCancelWallet);
        Button btnSave = view.findViewById(R.id.btnSaveWallet);

        String[] types = {"Physical Cash", "GCash (E-Wallet)"};
        spinnerType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, types));

        spinnerType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) layoutGcashFields.setVisibility(View.GONE);
                else layoutGcashFields.setVisibility(View.VISIBLE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            boolean isGcash = spinnerType.getSelectedItemPosition() == 1;
            String gName = etName.getText().toString().trim();
            String gNum = etNumber.getText().toString().trim();
            String balStr = etBalance.getText().toString().trim();
            double balance = balStr.isEmpty() ? 0.0 : Double.parseDouble(balStr);

            if (isGcash && gName.isEmpty()) { etName.setError("Required"); return; }
            if (isGcash && gNum.isEmpty()) { etNumber.setError("Required"); return; }

            String docId = isGcash ? "GCASH_" + System.currentTimeMillis() : "CASH_" + System.currentTimeMillis();

            Map<String, Object> newWallet = new HashMap<>();
            newWallet.put("name", isGcash ? "GCash - " + gName : "Cash Register");
            newWallet.put("type", isGcash ? "E-Wallet" : "Physical Cash");
            newWallet.put("balance", balance);
            if (isGcash) {
                newWallet.put("accountName", gName);
                newWallet.put("accountNumber", gNum);
            }

            db.collection("users").document(ownerId).collection("wallets").document(docId).set(newWallet)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Wallet Added!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    });
        });
        dialog.show();
    }

    private void showWalletDetailsDialog(Wallet wallet) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_wallet_details, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();

        TextView tvName = view.findViewById(R.id.tvDetailWalletName);
        TextView tvNum = view.findViewById(R.id.tvDetailWalletNumber);
        TextView tvBalance = view.findViewById(R.id.tvDetailBalance);
        Button btnReset = view.findViewById(R.id.btnResetCash);
        ListView lvHistory = view.findViewById(R.id.lvWalletHistory);
        Button btnClose = view.findViewById(R.id.btnCloseDetail);

        tvName.setText(wallet.name);
        tvBalance.setText(currencyFormat.format(wallet.balance));

        if ("E-Wallet".equals(wallet.type)) {
            tvNum.setVisibility(View.VISIBLE);
            tvNum.setText(wallet.accountNumber + " | " + wallet.accountName);
            btnReset.setVisibility(View.GONE);
        } else {
            tvNum.setVisibility(View.GONE);
            btnReset.setVisibility(View.VISIBLE);
        }

        List<CashTransaction> specificHistory = new ArrayList<>();
        for (CashTransaction t : transactionList) {
            if (t.walletId != null && t.walletId.equals(wallet.id)) {
                specificHistory.add(t);
            } else if ("Physical Cash".equals(wallet.type) && t.title.toLowerCase().contains("cash")) {
                specificHistory.add(t);
            } else if ("E-Wallet".equals(wallet.type) && t.title.toLowerCase().contains("gcash")) {
                specificHistory.add(t);
            }
        }

        ArrayAdapter<CashTransaction> historyAdapter = new ArrayAdapter<CashTransaction>(this, android.R.layout.simple_list_item_2, android.R.id.text1, specificHistory) {
            @NonNull @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView t1 = v.findViewById(android.R.id.text1);
                TextView t2 = v.findViewById(android.R.id.text2);
                CashTransaction t = getItem(position);

                String sign = t.isIncome ? "+" : "-";
                t1.setText(t.title + " (" + sign + currencyFormat.format(t.amount) + ")");
                if (t.isIncome) t1.setTextColor(getResources().getColor(R.color.successGreen));
                else t1.setTextColor(getResources().getColor(R.color.errorRed));
                t2.setText(t.date);
                return v;
            }
        };
        lvHistory.setAdapter(historyAdapter);

        btnReset.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Reset Cash")
                    .setMessage("Are you sure you want to reset this cash drawer to zero? Do this only at the end of your shift after withdrawing the physical money.")
                    .setPositiveButton("Reset", (d, w) -> {
                        db.collection("users").document(ownerId).collection("wallets").document(wallet.id)
                                .update("balance", 0.0)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Cash Reset to Zero", Toast.LENGTH_SHORT).show();
                                    dialog.dismiss();
                                });
                    })
                    .setNegativeButton("Cancel", null).show();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    // ==========================================
    // MODELS & ADAPTERS
    // ==========================================

    private static class Wallet {
        String id, name, type, accountName, accountNumber;
        double balance;
    }

    public interface OnWalletClickListener {
        void onClick(Wallet w);
    }

    private class WalletAdapter extends RecyclerView.Adapter<WalletAdapter.WalletViewHolder> {
        List<Wallet> wallets;
        OnWalletClickListener listener;

        public WalletAdapter(List<Wallet> wallets, OnWalletClickListener listener) {
            this.wallets = wallets;
            this.listener = listener;
        }

        @NonNull @Override
        public WalletViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cash_wallet, parent, false);
            return new WalletViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull WalletViewHolder holder, int position) {
            Wallet w = wallets.get(position);
            holder.tvName.setText(w.name);
            holder.tvType.setText(w.type);
            holder.tvBalance.setText(currencyFormat.format(w.balance));

            holder.itemView.setOnClickListener(v -> listener.onClick(w));
        }

        @Override public int getItemCount() { return wallets.size(); }

        class WalletViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvType, tvBalance;
            WalletViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvWalletName);
                tvType = itemView.findViewById(R.id.tvWalletType);
                tvBalance = itemView.findViewById(R.id.tvWalletBalance);
            }
        }
    }

    private static class CashTransaction {
        String id, title, date, walletId;
        double amount;
        boolean isIncome;
        long timestamp;
    }

    private class CashTransactionAdapter extends RecyclerView.Adapter<CashTransactionAdapter.TransViewHolder> {
        List<CashTransaction> transactions;

        public CashTransactionAdapter(List<CashTransaction> transactions) {
            this.transactions = transactions;
        }

        @NonNull @Override
        public TransViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction_log, parent, false);
            return new TransViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull TransViewHolder holder, int position) {
            CashTransaction t = transactions.get(position);
            holder.tvTitle.setText(t.title);
            holder.tvDate.setText(t.date);

            if (t.isIncome) {
                holder.tvAmount.setText("+ " + currencyFormat.format(t.amount));
                holder.tvAmount.setTextColor(getResources().getColor(R.color.successGreen));
            } else {
                holder.tvAmount.setText("- " + currencyFormat.format(Math.abs(t.amount)));
                holder.tvAmount.setTextColor(getResources().getColor(R.color.errorRed));
            }
        }
        @Override public int getItemCount() { return transactions.size(); }

        class TransViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvDate, tvAmount;
            public TransViewHolder(@NonNull View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tvTransTitle);
                tvDate = itemView.findViewById(R.id.tvTransDate);
                tvAmount = itemView.findViewById(R.id.tvTransAmount);
            }
        }
    }
}
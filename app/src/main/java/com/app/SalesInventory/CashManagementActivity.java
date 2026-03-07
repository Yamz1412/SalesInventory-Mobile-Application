package com.app.SalesInventory;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CashManagementActivity extends BaseActivity {

    private TextView tvTotalCash, tvCurrentDate;
    private TabLayout tabLayout;
    private ExtendedFloatingActionButton fabAddWallet;

    // View Containers for Tabs
    private LinearLayout layoutSummary, layoutFundTransfer, layoutTransactions;

    // Summary Tab
    private RecyclerView rvWallets;
    private WalletAdapter walletAdapter;
    private List<Wallet> walletList;

    // Fund Transfer Tab
    private AutoCompleteTextView spinFromWallet, spinToWallet;
    private TextView tvFromBalance, tvToBalance;
    private TextInputEditText etTransferAmount;
    private Button btnCancelTransfer, btnSaveTransfer;

    // Transactions Tab
    private RecyclerView rvTransactions;
    private Button btnTransactionReport;
    private TransactionAdapter transactionAdapter;
    private List<Transaction> transactionList;

    // Formatter
    private NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
    private SimpleDateFormat displayDateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cash_management);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Cash Management");
        }

        tvTotalCash = findViewById(R.id.tvTotalCash);
        tvCurrentDate = findViewById(R.id.tvCurrentDate);
        tabLayout = findViewById(R.id.tabLayout);
        fabAddWallet = findViewById(R.id.fabAddWallet);

        layoutSummary = findViewById(R.id.layout_summary);
        layoutFundTransfer = findViewById(R.id.layout_fund_transfer);
        layoutTransactions = findViewById(R.id.layout_transactions);

        spinFromWallet = findViewById(R.id.spinFromWallet);
        spinToWallet = findViewById(R.id.spinToWallet);
        tvFromBalance = findViewById(R.id.tvFromBalance);
        tvToBalance = findViewById(R.id.tvToBalance);
        etTransferAmount = findViewById(R.id.etTransferAmount);
        btnCancelTransfer = findViewById(R.id.btnCancelTransfer);
        btnSaveTransfer = findViewById(R.id.btnSaveTransfer);

        rvTransactions = findViewById(R.id.rvTransactions);
        btnTransactionReport = findViewById(R.id.btnTransactionReport);

        tvCurrentDate.setText(displayDateFormat.format(new Date()));

        loadData();
        setupTabs();
        setupFundTransferLogic();
        setupTransactions();

        fabAddWallet.setOnClickListener(v -> Toast.makeText(this, "Add Wallet dialog opening...", Toast.LENGTH_SHORT).show());
        btnTransactionReport.setOnClickListener(v -> showTransactionOverviewDialog());
    }

    private void loadData() {
        walletList = new ArrayList<>();
        walletList.add(new Wallet("Cash on Hand", "Physical Cash", 5430.00, R.drawable.ic_account_balance_wallet));
        walletList.add(new Wallet("GCash", "E-Wallet", 12500.50, R.drawable.ic_account_balance_wallet));
        walletList.add(new Wallet("Maya", "E-Wallet", 3200.00, R.drawable.ic_account_balance_wallet));
        walletList.add(new Wallet("BDO Account", "Bank Account", 45000.00, R.drawable.ic_account_balance_wallet));

        rvWallets = findViewById(R.id.rvWallets);
        rvWallets.setLayoutManager(new LinearLayoutManager(this));
        walletAdapter = new WalletAdapter(walletList);
        rvWallets.setAdapter(walletAdapter);

        calculateTotalCash();

        // Initialize with real timestamps so filters work correctly!
        long now = System.currentTimeMillis();
        long oneDay = 86400000L;
        long oneWeek = oneDay * 7L;

        transactionList = new ArrayList<>();
        transactionList.add(new Transaction("POS Sale (Cash)", "Today - 10:30 AM", 150.00, true, now));
        transactionList.add(new Transaction("Supplier Payment (PO)", "Yesterday - 09:15 AM", 2500.00, false, now - oneDay));
        transactionList.add(new Transaction("POS Sale (GCash)", "Last Week - 02:45 PM", 340.00, true, now - oneWeek));
    }

    private void calculateTotalCash() {
        double total = 0;
        for (Wallet w : walletList) total += w.getBalance();
        tvTotalCash.setText(currencyFormat.format(total));
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
                    case 0:
                        layoutSummary.setVisibility(View.VISIBLE);
                        fabAddWallet.setVisibility(View.VISIBLE);
                        break;
                    case 1:
                        layoutFundTransfer.setVisibility(View.VISIBLE);
                        break;
                    case 2:
                        layoutTransactions.setVisibility(View.VISIBLE);
                        break;
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupFundTransferLogic() {
        List<String> walletNames = new ArrayList<>();
        for (Wallet w : walletList) walletNames.add(w.getName());

        ArrayAdapter<String> spinAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, walletNames);
        spinFromWallet.setAdapter(spinAdapter);
        spinToWallet.setAdapter(spinAdapter);

        spinFromWallet.setOnItemClickListener((parent, view, position, id) -> {
            Wallet selected = findWalletByName(spinAdapter.getItem(position));
            if (selected != null) tvFromBalance.setText("Available Balance: " + currencyFormat.format(selected.getBalance()));
        });

        spinToWallet.setOnItemClickListener((parent, view, position, id) -> {
            Wallet selected = findWalletByName(spinAdapter.getItem(position));
            if (selected != null) tvToBalance.setText("Available Balance: " + currencyFormat.format(selected.getBalance()));
        });

        btnCancelTransfer.setOnClickListener(v -> {
            spinFromWallet.setText("", false);
            spinToWallet.setText("", false);
            etTransferAmount.setText("");
            tvFromBalance.setText("Available Balance: ₱ 0.00");
            tvToBalance.setText("Available Balance: ₱ 0.00");
        });

        btnSaveTransfer.setOnClickListener(v -> {
            String fromName = spinFromWallet.getText().toString();
            String toName = spinToWallet.getText().toString();
            String amtStr = etTransferAmount.getText() != null ? etTransferAmount.getText().toString() : "";

            if (fromName.isEmpty() || toName.isEmpty() || amtStr.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            if (fromName.equals(toName)) {
                Toast.makeText(this, "Cannot transfer to same wallet", Toast.LENGTH_SHORT).show();
                return;
            }

            double amount;
            try { amount = Double.parseDouble(amtStr); }
            catch (NumberFormatException e) { return; }

            Wallet fromWallet = findWalletByName(fromName);
            Wallet toWallet = findWalletByName(toName);

            if (fromWallet == null || toWallet == null) return;
            if (amount > fromWallet.getBalance()) {
                Toast.makeText(this, "Insufficient balance", Toast.LENGTH_SHORT).show();
                return;
            }

            fromWallet.setBalance(fromWallet.getBalance() - amount);
            toWallet.setBalance(toWallet.getBalance() + amount);

            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault());
            String dateStr = sdf.format(new Date());

            // Passing the current timestamp so it can be filtered later!
            transactionList.add(0, new Transaction("Transfer: " + fromName + " -> " + toName, dateStr, amount, false, System.currentTimeMillis()));

            walletAdapter.notifyDataSetChanged();
            transactionAdapter.notifyDataSetChanged();
            calculateTotalCash();

            Toast.makeText(this, "Transfer Successful!", Toast.LENGTH_SHORT).show();
            btnCancelTransfer.performClick();
            tabLayout.getTabAt(0).select();
        });
    }

    private void setupTransactions() {
        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        transactionAdapter = new TransactionAdapter(transactionList);
        rvTransactions.setAdapter(transactionAdapter);
    }

    // =======================================================
    // TRANSACTION OVERVIEW REPORT LOGIC (Filters + Range)
    // =======================================================
    private void showTransactionOverviewDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_transaction_report, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        Button btnStartDate = view.findViewById(R.id.btnStartDate);
        Button btnEndDate = view.findViewById(R.id.btnEndDate);
        AutoCompleteTextView spinDateFilter = view.findViewById(R.id.spinDateFilter);

        TextView tvTotalIn = view.findViewById(R.id.tvTotalIn);
        TextView tvTotalOut = view.findViewById(R.id.tvTotalOut);
        TextView tvNetMovement = view.findViewById(R.id.tvNetMovement);

        RecyclerView rvFilteredTransactions = view.findViewById(R.id.rvFilteredTransactions);
        rvFilteredTransactions.setLayoutManager(new LinearLayoutManager(this));
        List<Transaction> filteredList = new ArrayList<>();
        TransactionAdapter dialogAdapter = new TransactionAdapter(filteredList);
        rvFilteredTransactions.setAdapter(dialogAdapter);

        final Calendar startCal = Calendar.getInstance();
        final Calendar endCal = Calendar.getInstance();

        // 1. The Dynamic Calculator
        Runnable applyFilterAndUpdate = () -> {
            // Set start time to 00:00:00
            startCal.set(Calendar.HOUR_OF_DAY, 0);
            startCal.set(Calendar.MINUTE, 0);
            startCal.set(Calendar.SECOND, 0);

            // Set end time to 23:59:59
            endCal.set(Calendar.HOUR_OF_DAY, 23);
            endCal.set(Calendar.MINUTE, 59);
            endCal.set(Calendar.SECOND, 59);

            btnStartDate.setText(displayDateFormat.format(startCal.getTime()));
            btnEndDate.setText(displayDateFormat.format(endCal.getTime()));

            double totalIn = 0;
            double totalOut = 0;
            filteredList.clear();

            for (Transaction t : transactionList) {
                if (t.timestamp >= startCal.getTimeInMillis() && t.timestamp <= endCal.getTimeInMillis()) {
                    filteredList.add(t);
                    if (t.isIncome) totalIn += t.amount;
                    else totalOut += t.amount;
                }
            }

            dialogAdapter.notifyDataSetChanged();

            tvTotalIn.setText(String.format(Locale.getDefault(), "+ ₱ %,.2f", totalIn));
            tvTotalOut.setText(String.format(Locale.getDefault(), "- ₱ %,.2f", totalOut));

            double net = totalIn - totalOut;
            tvNetMovement.setText(String.format(Locale.getDefault(), "₱ %,.2f", net));
            if (net < 0) tvNetMovement.setTextColor(getResources().getColor(R.color.errorRed));
            else tvNetMovement.setTextColor(getResources().getColor(R.color.text_primary));
        };

        // 2. Setup the Preset Dropdown
        String[] filters = {"This week", "Last week", "This month", "Last month", "This year", "Last year", "All time", "Custom"};
        ArrayAdapter<String> filterAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, filters);
        spinDateFilter.setAdapter(filterAdapter);

        spinDateFilter.setOnItemClickListener((parent, v, position, id) -> {
            String preset = filters[position];
            long now = System.currentTimeMillis();
            endCal.setTimeInMillis(now);
            startCal.setTimeInMillis(now);

            switch (preset) {
                case "This week":
                    startCal.set(Calendar.DAY_OF_WEEK, startCal.getFirstDayOfWeek());
                    break;
                case "Last week":
                    startCal.add(Calendar.WEEK_OF_YEAR, -1);
                    startCal.set(Calendar.DAY_OF_WEEK, startCal.getFirstDayOfWeek());
                    endCal.setTimeInMillis(startCal.getTimeInMillis());
                    endCal.add(Calendar.DAY_OF_YEAR, 6);
                    break;
                case "This month":
                    startCal.set(Calendar.DAY_OF_MONTH, 1);
                    break;
                case "Last month":
                    startCal.add(Calendar.MONTH, -1);
                    startCal.set(Calendar.DAY_OF_MONTH, 1);
                    endCal.setTimeInMillis(startCal.getTimeInMillis());
                    endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH));
                    break;
                case "This year":
                    startCal.set(Calendar.DAY_OF_YEAR, 1);
                    break;
                case "Last year":
                    startCal.add(Calendar.YEAR, -1);
                    startCal.set(Calendar.DAY_OF_YEAR, 1);
                    endCal.setTimeInMillis(startCal.getTimeInMillis());
                    endCal.set(Calendar.DAY_OF_YEAR, endCal.getActualMaximum(Calendar.DAY_OF_YEAR));
                    break;
                case "All time":
                    startCal.setTimeInMillis(0); // From the beginning of time
                    break;
                case "Custom":
                    return; // Keeps current manual dates
            }
            applyFilterAndUpdate.run();
        });

        // 3. Manual Date Pickers (Switches Dropdown to Custom)
        btnStartDate.setOnClickListener(v -> {
            new DatePickerDialog(this, (d, year, month, day) -> {
                startCal.set(year, month, day);
                spinDateFilter.setText("Custom", false);
                applyFilterAndUpdate.run();
            }, startCal.get(Calendar.YEAR), startCal.get(Calendar.MONTH), startCal.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnEndDate.setOnClickListener(v -> {
            new DatePickerDialog(this, (d, year, month, day) -> {
                endCal.set(year, month, day);
                spinDateFilter.setText("Custom", false);
                applyFilterAndUpdate.run();
            }, endCal.get(Calendar.YEAR), endCal.get(Calendar.MONTH), endCal.get(Calendar.DAY_OF_MONTH)).show();
        });

        // Initialize to "This month"
        spinDateFilter.setText("This month", false);
        startCal.set(Calendar.DAY_OF_MONTH, 1);
        applyFilterAndUpdate.run();
    }

    private Wallet findWalletByName(String name) {
        for (Wallet w : walletList) {
            if (w.getName().equals(name)) return w;
        }
        return null;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ==========================================
    // MODELS & ADAPTERS
    // ==========================================

    static class Wallet {
        String name, type;
        double balance;
        int iconResId;

        public Wallet(String name, String type, double balance, int iconResId) {
            this.name = name; this.type = type; this.balance = balance; this.iconResId = iconResId;
        }
        public String getName() { return name; }
        public String getType() { return type; }
        public double getBalance() { return balance; }
        public void setBalance(double balance) { this.balance = balance; }
        public int getIconResId() { return iconResId; }
    }

    class WalletAdapter extends RecyclerView.Adapter<WalletAdapter.WalletViewHolder> {
        private List<Wallet> wallets;
        public WalletAdapter(List<Wallet> wallets) { this.wallets = wallets; }

        @NonNull @Override
        public WalletViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new WalletViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cash_wallet, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull WalletViewHolder holder, int position) {
            Wallet wallet = wallets.get(position);
            holder.tvName.setText(wallet.getName());
            holder.tvType.setText(wallet.getType());
            holder.tvBalance.setText(currencyFormat.format(wallet.getBalance()));
            try { holder.ivIcon.setImageResource(wallet.getIconResId()); }
            catch (Exception e) { holder.ivIcon.setImageResource(R.drawable.ic_account_balance_wallet); }
        }
        @Override public int getItemCount() { return wallets.size(); }

        class WalletViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon; TextView tvName, tvType, tvBalance;
            public WalletViewHolder(@NonNull View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.ivWalletIcon);
                tvName = itemView.findViewById(R.id.tvWalletName);
                tvType = itemView.findViewById(R.id.tvWalletType);
                tvBalance = itemView.findViewById(R.id.tvWalletBalance);
            }
        }
    }

    static class Transaction {
        String title, date;
        double amount;
        boolean isIncome;
        long timestamp; // NEW: Critical for range filtering

        public Transaction(String title, String date, double amount, boolean isIncome, long timestamp) {
            this.title = title;
            this.date = date;
            this.amount = amount;
            this.isIncome = isIncome;
            this.timestamp = timestamp;
        }
    }

    class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransViewHolder> {
        private List<Transaction> transactions;
        public TransactionAdapter(List<Transaction> transactions) { this.transactions = transactions; }

        @NonNull @Override
        public TransViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new TransViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction_log, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull TransViewHolder holder, int position) {
            Transaction t = transactions.get(position);
            holder.tvTitle.setText(t.title);
            holder.tvDate.setText(t.date);

            if (t.isIncome) {
                holder.tvAmount.setText("+ " + currencyFormat.format(t.amount));
                holder.tvAmount.setTextColor(getResources().getColor(R.color.success_primary));
                holder.tvTitle.setText(t.title + " (In)");
            } else {
                holder.tvAmount.setText("- " + currencyFormat.format(Math.abs(t.amount)));
                holder.tvAmount.setTextColor(getResources().getColor(R.color.errorRed));
                holder.tvTitle.setText(t.title + " (Out)");
            }

            try { holder.ivIcon.setImageResource(R.drawable.ic_receipt); }
            catch (Exception ignored) {}
        }
        @Override public int getItemCount() { return transactions.size(); }

        class TransViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvDate, tvAmount;
            ImageView ivIcon;
            public TransViewHolder(@NonNull View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tvTransTitle);
                tvDate = itemView.findViewById(R.id.tvTransDate);
                tvAmount = itemView.findViewById(R.id.tvTransAmount);
                ivIcon = itemView.findViewById(R.id.ivTransIcon);
            }
        }
    }
}
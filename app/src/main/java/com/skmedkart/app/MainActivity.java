package com.skmedkart.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private DB db;
    private LinearLayout box;
    private final int PAD = 24;

    private static class CartItem {
        long medicineId;
        String name;
        double price;
        int qty;
        CartItem(long id, String n, double p, int q) {
            medicineId = id; name = n; price = p; qty = q;
        }
        double amount() { return price * qty; }
    }

    private final ArrayList<CartItem> cart = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        db = new DB(this);
        requestNotificationPermission();
        home();
    }

    private TextView text(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setPadding(PAD, 12, PAD, 12);
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        return b;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setPadding(PAD, 12, PAD, 12);
        box.addView(e);
        return e;
    }

    private void page(String title) {
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(12, 12, 12, 12);
        android.widget.ScrollView sc = new android.widget.ScrollView(this);
        sc.addView(box);
        setContentView(sc);
        TextView h = text(title, 25);
        h.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        box.addView(h);
    }

    private void home() {
        cart.clear();
        page("🏪 Sri Krishna Medicals");
        box.addView(text("SKMedKART • Pharmacy Billing & Reminder", 17));

        Button b = button("🧾 New Bill");
        box.addView(b);
        b.setOnClickListener(v -> bill());

        b = button("👤 Customers & Reminders");
        box.addView(b);
        b.setOnClickListener(v -> customers());

        b = button("💊 Medicine Stock");
        box.addView(b);
        b.setOnClickListener(v -> medicines());

        b = button("📊 Sales History");
        box.addView(b);
        b.setOnClickListener(v -> sales());
    }

    private void bill() {
        page("🧾 New Bill");
        EditText customer = input("Customer name");
        EditText phone = input("Mobile number");
        phone.setInputType(2);

        box.addView(text("Select medicine and quantity", 17));

        ArrayList<DB.Medicine> meds = db.medicineList();
        ArrayList<String> labels = new ArrayList<>();
        for (DB.Medicine m : meds) {
            labels.add(m.name + "  ₹" + String.format(Locale.getDefault(), "%.2f", m.price)
                    + "  (Stock: " + m.stock + ")");
        }

        Spinner medicineSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels);
        medicineSpinner.setAdapter(adapter);
        box.addView(medicineSpinner);

        EditText qty = input("Quantity");
        qty.setInputType(2);

        Button addItem = button("➕ ADD ITEM");
        box.addView(addItem);

        LinearLayout cartBox = new LinearLayout(this);
        cartBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(cartBox);

        TextView totalText = text("Grand Total: ₹0.00", 20);
        totalText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        box.addView(totalText);

        addItem.setOnClickListener(v -> {
            if (meds.isEmpty()) {
                Toast.makeText(this, "Add medicines to Stock first", Toast.LENGTH_SHORT).show();
                return;
            }
            int pos = medicineSpinner.getSelectedItemPosition();
            if (pos < 0 || pos >= meds.size()) return;
            int q;
            try { q = Integer.parseInt(qty.getText().toString().trim()); }
            catch (Exception e) { q = 0; }
            if (q <= 0) {
                Toast.makeText(this, "Enter a valid quantity", Toast.LENGTH_SHORT).show();
                return;
            }

            DB.Medicine m = meds.get(pos);
            int already = 0;
            for (CartItem item : cart) {
                if (item.medicineId == m.id) already += item.qty;
            }
            if (already + q > m.stock) {
                Toast.makeText(this, "Only " + (m.stock - already) + " in stock", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean merged = false;

for (CartItem item : cart) {
    if (item.medicineId == m.id) {
        item.qty += q;
        merged = true;
        break;
    }
}

if (!merged) {
    cart.add(new CartItem(m.id, m.name, m.price, q));
}
            qty.setText("");
            renderCart(cartBox, totalText);
        });

        Button save = button("💾 SAVE BILL");
        box.addView(save);
        Button clear = button("CLEAR ITEMS");
        box.addView(clear);
        Button back = button("← Home");
        box.addView(back);

        clear.setOnClickListener(v -> {
            cart.clear();
            renderCart(cartBox, totalText);
        });
        back.setOnClickListener(v -> home());

        save.setOnClickListener(v -> {
            if (cart.isEmpty()) {
                Toast.makeText(this, "Add at least one medicine", Toast.LENGTH_SHORT).show();
                return;
            }
            double total = 0;
            for (CartItem item : cart) total += item.amount();
            String customerName = customer.getText().toString().trim();
            if (customerName.isEmpty()) customerName = "Walk-in Customer";

            ArrayList<DB.BillItem> items = new ArrayList<>();
            for (CartItem item : cart) {
                items.add(new DB.BillItem(item.medicineId, item.name, item.price, item.qty));
            }

            try {
                String d = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
                db.addBillWithItems(customerName, phone.getText().toString().trim(), total, d, items);
                Toast.makeText(this, "Bill saved ₹" +
                        String.format(Locale.getDefault(), "%.2f", total) +
                        " • Stock updated", Toast.LENGTH_LONG).show();
                cart.clear();
                home();
            } catch (IllegalStateException e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Could not save bill", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void renderCart(LinearLayout cartBox, TextView totalText) {
        cartBox.removeAllViews();
        double total = 0;
        for (int i = 0; i < cart.size(); i++) {
            CartItem item = cart.get(i);
            total += item.amount();
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView t = text(item.name + " × " + item.qty + "  ₹" +
                    String.format(Locale.getDefault(), "%.2f", item.amount()), 16);
            row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
            Button remove = button("X");
            row.addView(remove);
            final int index = i;
            remove.setOnClickListener(v -> {
                cart.remove(index);
                renderCart(cartBox, totalText);
            });
            cartBox.addView(row);
        }
        totalText.setText("Grand Total: ₹" + String.format(Locale.getDefault(), "%.2f", total));
    }

    private void customers() {
        page("👤 Customers & Reminders");
        EditText n = input("Customer name");
        EditText p = input("Mobile number");
        p.setInputType(2);
        EditText notes = input("Reminder note");
        Button add = button("ADD CUSTOMER");
        box.addView(add);
        add.setOnClickListener(v -> {
            if (n.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "Enter customer name", Toast.LENGTH_SHORT).show();
                return;
            }
            db.addCustomer(n.getText().toString(), p.getText().toString(), notes.getText().toString());
            customers();
        });

        Cursor cur = db.customers();
        while (cur.moveToNext()) {
            String name = cur.getString(1);
            box.addView(text("• " + name + "  " + cur.getString(2) + "\n  " + cur.getString(3), 16));
            Button r = button("🔔 Remind tomorrow — " + name);
            box.addView(r);
            r.setOnClickListener(v -> setReminder(name));
        }
        cur.close();

        Button back = button("← Home");
        box.addView(back);
        back.setOnClickListener(v -> home());
    }

    private void setReminder(String customer) {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        Intent i = new Intent(this, ReminderReceiver.class);
        i.putExtra("customer", customer);
        i.putExtra("message", "Medicine refill reminder");
        PendingIntent pi = PendingIntent.getBroadcast(this,
                (int) (System.currentTimeMillis() % 100000), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 86400000L, pi);
        Toast.makeText(this, "Reminder set for tomorrow", Toast.LENGTH_SHORT).show();
    }

    private void medicines() {
        page("💊 Medicine Stock");
        EditText n = input("Medicine name");
        EditText price = input("Selling price ₹");
        price.setInputType(2 | 8192);
        EditText stock = input("Stock quantity");
        stock.setInputType(2);
        EditText exp = input("Expiry (MM/YYYY)");

        Button add = button("ADD MEDICINE");
        box.addView(add);
        add.setOnClickListener(v -> {
            try {
                String name = n.getText().toString().trim();
                double pr = Double.parseDouble(price.getText().toString().trim());
                int st = Integer.parseInt(stock.getText().toString().trim());
                if (name.isEmpty() || pr < 0 || st < 0) throw new Exception();
                db.addMedicine(name, pr, st, exp.getText().toString().trim());
                medicines();
            } catch (Exception e) {
                Toast.makeText(this, "Check medicine details", Toast.LENGTH_SHORT).show();
            }
        });

        Cursor cur = db.medicines();
        while (cur.moveToNext()) {
            box.addView(text("💊 " + cur.getString(1) + "  ₹" + cur.getDouble(2) +
                    "\nStock: " + cur.getInt(3) + "   Expiry: " + cur.getString(4), 16));
        }
        cur.close();

        Button back = button("← Home");
        box.addView(back);
        back.setOnClickListener(v -> home());
    }

    private void sales() {
        page("📊 Sales History");
        Cursor cur = db.bills();
        double sum = 0;
        while (cur.moveToNext()) {
            sum += cur.getDouble(3);
            box.addView(text("🧾 " + cur.getString(1) + "  ₹" +
                    String.format(Locale.getDefault(), "%.2f", cur.getDouble(3)) +
                    "\n" + cur.getString(4), 16));
        }
        cur.close();
        box.addView(text("TOTAL SALES: ₹" +
                String.format(Locale.getDefault(), "%.2f", sum), 21));
        Button back = button("← Home");
        box.addView(back);
        back.setOnClickListener(v -> home());
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
    }
}

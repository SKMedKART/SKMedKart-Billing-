package com.skmedkart.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DB extends SQLiteOpenHelper {
    private static final int DB_VERSION = 2;

    public static class Medicine {
        public long id;
        public String name;
        public double price;
        public int stock;
        public String expiry;
        Medicine(long id, String name, double price, int stock, String expiry) {
            this.id = id; this.name = name; this.price = price; this.stock = stock; this.expiry = expiry;
        }
    }

    public static class BillItem {
        public long medicineId;
        public String name;
        public double price;
        public int qty;
        public BillItem(long medicineId, String name, double price, int qty) {
            this.medicineId = medicineId; this.name = name; this.price = price; this.qty = qty;
        }
    }

    public DB(Context context) { super(context, "skmedkart.db", null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE customers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,phone TEXT,notes TEXT)");
        db.execSQL("CREATE TABLE medicines(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,price REAL NOT NULL,stock INTEGER NOT NULL,expiry TEXT)");
        db.execSQL("CREATE TABLE bills(id INTEGER PRIMARY KEY AUTOINCREMENT,customer TEXT,phone TEXT,total REAL NOT NULL,created TEXT NOT NULL)");
        db.execSQL("CREATE TABLE bill_items(id INTEGER PRIMARY KEY AUTOINCREMENT,bill_id INTEGER NOT NULL,medicine_id INTEGER NOT NULL,medicine_name TEXT NOT NULL,price REAL NOT NULL,qty INTEGER NOT NULL,amount REAL NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS bill_items(id INTEGER PRIMARY KEY AUTOINCREMENT,bill_id INTEGER NOT NULL,medicine_id INTEGER NOT NULL,medicine_name TEXT NOT NULL,price REAL NOT NULL,qty INTEGER NOT NULL,amount REAL NOT NULL)");
        }
    }

    public long addCustomer(String name, String phone, String notes) {
        ContentValues v = new ContentValues();
        v.put("name", name); v.put("phone", phone); v.put("notes", notes);
        return getWritableDatabase().insert("customers", null, v);
    }

    public long addMedicine(String name, double price, int stock, String expiry) {
        ContentValues v = new ContentValues();
        v.put("name", name); v.put("price", price); v.put("stock", stock); v.put("expiry", expiry);
        return getWritableDatabase().insert("medicines", null, v);
    }

    /**
     * Saves the bill and deducts every sold quantity from medicine stock atomically.
     * If any item has insufficient stock, the entire operation is cancelled.
     */
    public long addBillWithItems(String customer, String phone, double total, String created,
                                 ArrayList<BillItem> items) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Map<Long, Integer> required = new HashMap<>();
            for (BillItem item : items) {
                if (item.qty <= 0) throw new IllegalStateException("Invalid quantity for " + item.name);
                Integer old = required.get(item.medicineId);
                required.put(item.medicineId, (old == null ? 0 : old) + item.qty);
            }

            for (Map.Entry<Long, Integer> entry : required.entrySet()) {
                Cursor c = db.rawQuery("SELECT name,stock FROM medicines WHERE id=?",
                        new String[]{String.valueOf(entry.getKey())});
                if (!c.moveToFirst()) {
                    c.close();
                    throw new IllegalStateException("Medicine no longer exists");
                }
                String name = c.getString(0);
                int stock = c.getInt(1);
                c.close();
                if (entry.getValue() > stock) {
                    throw new IllegalStateException("Insufficient stock: " + name + " (available " + stock + ")");
                }
            }

            ContentValues bill = new ContentValues();
            bill.put("customer", customer);
            bill.put("phone", phone);
            bill.put("total", total);
            bill.put("created", created);
            long billId = db.insertOrThrow("bills", null, bill);

            for (BillItem item : items) {
                double amount = item.price * item.qty;
                ContentValues line = new ContentValues();
                line.put("bill_id", billId);
                line.put("medicine_id", item.medicineId);
                line.put("medicine_name", item.name);
                line.put("price", item.price);
                line.put("qty", item.qty);
                line.put("amount", amount);
                db.insertOrThrow("bill_items", null, line);

                db.execSQL("UPDATE medicines SET stock=stock-? WHERE id=?",
                        new Object[]{item.qty, item.medicineId});
            }

            db.setTransactionSuccessful();
            return billId;
        } finally {
            db.endTransaction();
        }
    }

    public ArrayList<Medicine> medicineList() {
        ArrayList<Medicine> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,name,price,stock,expiry FROM medicines ORDER BY name COLLATE NOCASE", null);
        while (c.moveToNext()) {
            list.add(new Medicine(c.getLong(0), c.getString(1), c.getDouble(2), c.getInt(3), c.getString(4)));
        }
        c.close();
        return list;
    }

    public Cursor customers() {
        return getReadableDatabase().rawQuery(
                "SELECT id,name,phone,notes FROM customers ORDER BY id DESC", null);
    }

    public Cursor medicines() {
        return getReadableDatabase().rawQuery(
                "SELECT id,name,price,stock,expiry FROM medicines ORDER BY id DESC", null);
    }

    public Cursor bills() {
        return getReadableDatabase().rawQuery(
                "SELECT id,customer,phone,total,created FROM bills ORDER BY id DESC", null);
    }
}

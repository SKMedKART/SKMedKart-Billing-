package com.skmedkart.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

public class DB extends SQLiteOpenHelper {

    private static final String DB_NAME = "skmedkart.db";
    private static final int DB_VERSION = 2;

    public DB(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    // =========================
    // MEDICINE MODEL
    // =========================
    public static class Medicine {
        public long id;
        public String name;
        public double price;
        public int stock;
        public String expiry;

        public Medicine(long id, String name, double price,
                        int stock, String expiry) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.stock = stock;
            this.expiry = expiry;
        }
    }

    // =========================
    // BILL ITEM MODEL
    // =========================
    public static class BillItem {
        public long medicineId;
        public String name;
        public double price;
        public int qty;

        public BillItem(long medicineId, String name,
                        double price, int qty) {
            this.medicineId = medicineId;
            this.name = name;
            this.price = price;
            this.qty = qty;
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        // MEDICINES
        db.execSQL(
                "CREATE TABLE medicines (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name TEXT NOT NULL," +
                        "price REAL NOT NULL," +
                        "stock INTEGER NOT NULL DEFAULT 0," +
                        "expiry TEXT)"
        );

        // CUSTOMERS
        db.execSQL(
                "CREATE TABLE customers (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name TEXT NOT NULL," +
                        "phone TEXT," +
                        "notes TEXT)"
        );

        // BILLS
        db.execSQL(
                "CREATE TABLE bills (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "customer TEXT," +
                        "phone TEXT," +
                        "total REAL NOT NULL," +
                        "date TEXT)"
        );

        // BILL ITEMS
        db.execSQL(
                "CREATE TABLE bill_items (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "bill_id INTEGER NOT NULL," +
                        "medicine_id INTEGER," +
                        "medicine_name TEXT," +
                        "price REAL," +
                        "qty INTEGER)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db,
                          int oldVersion,
                          int newVersion) {

        // Keep existing data.
        if (oldVersion < 2) {
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS bill_items (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "bill_id INTEGER NOT NULL," +
                            "medicine_id INTEGER," +
                            "medicine_name TEXT," +
                            "price REAL," +
                            "qty INTEGER)"
            );
        }
    }

    // =====================================================
    // MEDICINE
    // =====================================================

    public void addMedicine(String name,
                            double price,
                            int stock,
                            String expiry) {

        SQLiteDatabase db = getWritableDatabase();

        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("price", price);
        v.put("stock", stock);
        v.put("expiry", expiry);

        db.insert("medicines", null, v);
    }

    public ArrayList<Medicine> medicineList() {

        ArrayList<Medicine> list = new ArrayList<>();

        SQLiteDatabase db = getReadableDatabase();

        Cursor c = db.query(
                "medicines",
                null,
                null,
                null,
                null,
                null,
                "name COLLATE NOCASE ASC"
        );

        while (c.moveToNext()) {

            long id = c.getLong(c.getColumnIndexOrThrow("id"));
            String name = c.getString(
                    c.getColumnIndexOrThrow("name"));
            double price = c.getDouble(
                    c.getColumnIndexOrThrow("price"));
            int stock = c.getInt(
                    c.getColumnIndexOrThrow("stock"));
            String expiry = c.getString(
                    c.getColumnIndexOrThrow("expiry"));

            list.add(new Medicine(
                    id,
                    name,
                    price,
                    stock,
                    expiry
            ));
        }

        c.close();

        return list;
    }

    public Cursor medicines() {

        SQLiteDatabase db = getReadableDatabase();

        return db.query(
                "medicines",
                new String[]{
                        "id",
                        "name",
                        "price",
                        "stock",
                        "expiry"
                },
                null,
                null,
                null,
                null,
                "name COLLATE NOCASE ASC"
        );
    }

    // =====================================================
    // STOCK REDUCTION
    // =====================================================

    private boolean reduceStock(SQLiteDatabase db,
                                long medicineId,
                                int qty) {

        Cursor c = db.query(
                "medicines",
                new String[]{"stock"},
                "id=?",
                new String[]{String.valueOf(medicineId)},
                null,
                null,
                null
        );

        if (!c.moveToFirst()) {
            c.close();
            return false;
        }

        int currentStock = c.getInt(0);
        c.close();

        if (qty <= 0) {
            return false;
        }

        if (currentStock < qty) {
            return false;
        }

        int newStock = currentStock - qty;

        ContentValues v = new ContentValues();
        v.put("stock", newStock);

        int updated = db.update(
                "medicines",
                v,
                "id=? AND stock>=?",
                new String[]{
                        String.valueOf(medicineId),
                        String.valueOf(qty)
                }
        );

        return updated == 1;
    }

    // =====================================================
    // BILL + STOCK UPDATE
    // =====================================================

    public void addBillWithItems(
            String customer,
            String phone,
            double total,
            String date,
            ArrayList<BillItem> items) {

        SQLiteDatabase db = getWritableDatabase();

        db.beginTransaction();

        try {

            // ---------------------------------------------
            // FIRST CHECK ALL STOCK
            // ---------------------------------------------

            for (BillItem item : items) {

                Cursor c = db.query(
                        "medicines",
                        new String[]{"stock"},
                        "id=?",
                        new String[]{
                                String.valueOf(item.medicineId)
                        },
                        null,
                        null,
                        null
                );

                if (!c.moveToFirst()) {
                    c.close();
                    throw new IllegalStateException(
                            "Medicine not found: " + item.name
                    );
                }

                int stock = c.getInt(0);
                c.close();

                if (item.qty <= 0) {
                    throw new IllegalStateException(
                            "Invalid quantity for " + item.name
                    );
                }

                if (item.qty > stock) {
                    throw new IllegalStateException(
                            "Only " + stock +
                                    " in stock for " +
                                    item.name
                    );
                }
            }

            // ---------------------------------------------
            // CREATE BILL
            // ---------------------------------------------

            ContentValues billValues = new ContentValues();

            billValues.put("customer", customer);
            billValues.put("phone", phone);
            billValues.put("total", total);
            billValues.put("date", date);

            long billId = db.insert(
                    "bills",
                    null,
                    billValues
            );

            if (billId == -1) {
                throw new IllegalStateException(
                        "Could not create bill"
                );
            }

            // ---------------------------------------------
            // ADD BILL ITEMS + REDUCE STOCK
            // ---------------------------------------------

            for (BillItem item : items) {

                ContentValues itemValues =
                        new ContentValues();

                itemValues.put(
                        "bill_id",
                        billId
                );

                itemValues.put(
                        "medicine_id",
                        item.medicineId
                );

                itemValues.put(
                        "medicine_name",
                        item.name
                );

                itemValues.put(
                        "price",
                        item.price
                );

                itemValues.put(
                        "qty",
                        item.qty
                );

                long itemId = db.insert(
                        "bill_items",
                        null,
                        itemValues
                );

                if (itemId == -1) {
                    throw new IllegalStateException(
                            "Could not save bill item"
                    );
                }

                // IMPORTANT:
                // Reduce stock here
                boolean reduced = reduceStock(
                        db,
                        item.medicineId,
                        item.qty
                );

                if (!reduced) {
                    throw new IllegalStateException(
                            "Stock update failed for " +
                                    item.name
                    );
                }
            }

            db.setTransactionSuccessful();

        } finally {
            db.endTransaction();
        }
    }

    // =====================================================
    // CUSTOMERS
    // =====================================================

    public void addCustomer(
            String name,
            String phone,
            String notes) {

        SQLiteDatabase db = getWritableDatabase();

        ContentValues v = new ContentValues();

        v.put("name", name);
        v.put("phone", phone);
        v.put("notes", notes);

        db.insert(
                "customers",
                null,
                v
        );
    }

    public Cursor customers() {

        SQLiteDatabase db = getReadableDatabase();

        return db.query(
                "customers",
                new String[]{
                        "id",
                        "name",
                        "phone",
                        "notes"
                },
                null,
                null,
                null,
                null,
                "id DESC"
        );
    }

    // =====================================================
    // SALES HISTORY
    // =====================================================

    public Cursor bills() {

        SQLiteDatabase db = getReadableDatabase();

        return db.query(
                "bills",
                new String[]{
                        "id",
                        "customer",
                        "phone",
                        "total",
                        "date"
                },
                null,
                null,
                null,
                null,
                "id DESC"
        );
    }
}

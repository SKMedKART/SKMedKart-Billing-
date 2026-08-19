package com.skmedkart.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

public class DB extends SQLiteOpenHelper {

    private static final String DB_NAME = "skmedkart.db";
    private static final int DB_VERSION = 4;

    // ============================================================
    // MEDICINE
    // ============================================================

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

    // ============================================================
    // BILL ITEM
    // ============================================================

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

    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public DB(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    // ============================================================
    // CREATE DATABASE
    // ============================================================

    @Override
    public void onCreate(SQLiteDatabase db) {

        // Medicines
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS medicines (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name TEXT NOT NULL," +
                        "price REAL NOT NULL," +
                        "stock INTEGER NOT NULL," +
                        "expiry TEXT" +
                        ")"
        );

        // Customers
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS customers (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name TEXT NOT NULL," +
                        "phone TEXT," +
                        "notes TEXT" +
                        ")"
        );

        // Bills
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS bills (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "customer TEXT," +
                        "phone TEXT," +
                        "total REAL NOT NULL," +
                        "date TEXT" +
                        ")"
        );

        // Bill items
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS bill_items (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "bill_id INTEGER NOT NULL," +
                        "medicine_id INTEGER NOT NULL," +
                        "name TEXT NOT NULL," +
                        "price REAL NOT NULL," +
                        "qty INTEGER NOT NULL" +
                        ")"
        );
    }

    // ============================================================
    // DATABASE UPGRADE
    // ============================================================

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        // Do NOT delete existing data.
        //
        // Version 4 is used for the stock-deduction update.
        // Existing tables are kept.

        if (oldVersion < 4) {

            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS bill_items (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "bill_id INTEGER NOT NULL," +
                            "medicine_id INTEGER NOT NULL," +
                            "name TEXT NOT NULL," +
                            "price REAL NOT NULL," +
                            "qty INTEGER NOT NULL" +
                            ")"
            );
        }
    }

    // ============================================================
    // ADD MEDICINE
    // ============================================================

    public long addMedicine(String name,
                            double price,
                            int stock,
                            String expiry) {

        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();

        values.put("name", name);
        values.put("price", price);
        values.put("stock", stock);
        values.put("expiry", expiry);

        return db.insert("medicines", null, values);
    }

    // ============================================================
    // MEDICINE LIST
    // ============================================================

    public ArrayList<Medicine> medicineList() {

        ArrayList<Medicine> list = new ArrayList<>();

        SQLiteDatabase db = getReadableDatabase();

        Cursor c = db.query(
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
                "name ASC"
        );

        try {

            while (c.moveToNext()) {

                list.add(
                        new Medicine(
                                c.getLong(0),
                                c.getString(1),
                                c.getDouble(2),
                                c.getInt(3),
                                c.getString(4)
                        )
                );
            }

        } finally {
            c.close();
        }

        return list;
    }

    // ============================================================
    // MEDICINES CURSOR
    // ============================================================

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
                "name ASC"
        );
    }

    // ============================================================
    // ADD CUSTOMER
    // ============================================================

    public long addCustomer(String name,
                            String phone,
                            String notes) {

        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();

        values.put("name", name);
        values.put("phone", phone);
        values.put("notes", notes);

        return db.insert("customers", null, values);
    }

    // ============================================================
    // CUSTOMERS
    // ============================================================

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
                "name ASC"
        );
    }

    // ============================================================
    // SAVE BILL + ITEMS + DEDUCT STOCK
    // ============================================================

    public void addBillWithItems(String customer,
                                 String phone,
                                 double total,
                                 String date,
                                 ArrayList<BillItem> items) {

        if (items == null || items.isEmpty()) {
            throw new IllegalStateException(
                    "No medicines in bill"
            );
        }

        SQLiteDatabase db = getWritableDatabase();

        db.beginTransaction();

        try {

            // ----------------------------------------------------
            // 1. CHECK STOCK BEFORE SAVING ANYTHING
            // ----------------------------------------------------

            for (BillItem item : items) {

                Cursor c = db.rawQuery(
                        "SELECT stock FROM medicines WHERE id = ?",
                        new String[]{
                                String.valueOf(item.medicineId)
                        }
                );

                try {

                    if (!c.moveToFirst()) {

                        throw new IllegalStateException(
                                "Medicine not found: " + item.name
                        );
                    }

                    int currentStock = c.getInt(0);

                    if (currentStock < item.qty) {

                        throw new IllegalStateException(
                                "Only " + currentStock +
                                        " stock available for " +
                                        item.name
                        );
                    }

                } finally {

                    c.close();
                }
            }

            // ----------------------------------------------------
            // 2. SAVE BILL
            // ----------------------------------------------------

            ContentValues billValues = new ContentValues();

            billValues.put(
                    "customer",
                    customer == null
                            ? "Walk-in Customer"
                            : customer
            );

            billValues.put(
                    "phone",
                    phone == null
                            ? ""
                            : phone
            );

            billValues.put("total", total);
            billValues.put("date", date);

            long billId = db.insert(
                    "bills",
                    null,
                    billValues
            );

            if (billId == -1) {

                throw new IllegalStateException(
                        "Could not save bill"
                );
            }

            // ----------------------------------------------------
            // 3. SAVE BILL ITEMS + DEDUCT STOCK
            // ----------------------------------------------------

            for (BillItem item : items) {

                // Save bill item
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
                        "name",
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

                // ------------------------------------------------
                // ⭐ ACTUAL STOCK DEDUCTION
                // ------------------------------------------------

                int changed = db.compileStatement(
                        "UPDATE medicines " +
                                "SET stock = stock - ? " +
                                "WHERE id = ? " +
                                "AND stock >= ?"
                ).executeUpdateDelete(
                        item.qty,
                        item.medicineId,
                        item.qty
                );

                if (changed != 1) {

                    throw new IllegalStateException(
                            "Stock update failed for " +
                                    item.name
                    );
                }
            }

            // ----------------------------------------------------
            // 4. COMMIT EVERYTHING
            // ----------------------------------------------------

            db.setTransactionSuccessful();

        } finally {

            db.endTransaction();
        }
    }

    // ============================================================
    // BILLS / SALES HISTORY
    // ============================================================

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

    // ============================================================
    // CLOSE DATABASE
    // ============================================================

    @Override
    public synchronized void close() {
        super.close();
    }
}

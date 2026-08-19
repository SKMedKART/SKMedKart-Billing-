package com.skmedkart.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

public class DB extends SQLiteOpenHelper {

    private static final String DB_NAME = "skmedkart.db";
    private static final int DB_VERSION = 3;

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

    // =========================
    // DATABASE CREATE
    // =========================
    @Override
    public void onCreate(SQLiteDatabase db) {

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS medicines (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name TEXT NOT NULL," +
                        "price REAL NOT NULL," +
                        "stock INTEGER NOT NULL DEFAULT 0," +
                        "expiry TEXT" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS customers (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name TEXT NOT NULL," +
                        "phone TEXT," +
                        "notes TEXT" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS bills (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "customer TEXT," +
                        "phone TEXT," +
                        "total REAL NOT NULL," +
                        "date TEXT" +
                        ")"
        );

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

    // =========================
    // DATABASE UPGRADE
    // =========================
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        // Make sure all required tables exist.
        // Existing data is NOT deleted.

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS medicines (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name TEXT NOT NULL," +
                        "price REAL NOT NULL," +
                        "stock INTEGER NOT NULL DEFAULT 0," +
                        "expiry TEXT" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS customers (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name TEXT NOT NULL," +
                        "phone TEXT," +
                        "notes TEXT" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS bills (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "customer TEXT," +
                        "phone TEXT," +
                        "total REAL NOT NULL," +
                        "date TEXT" +
                        ")"
        );

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

    // =========================
    // ADD MEDICINE
    // =========================
    public long addMedicine(String name,
                            double price,
                            int stock,
                            String expiry) {

        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("name", name.trim());
        values.put("price", price);
        values.put("stock", stock);
        values.put("expiry", expiry.trim());

        return db.insert("medicines", null, values);
    }

    // =========================
    // MEDICINE LIST
    // =========================
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
                "name COLLATE NOCASE ASC"
        );

        try {
            while (c.moveToNext()) {

                long id = c.getLong(0);
                String name = c.getString(1);
                double price = c.getDouble(2);
                int stock = c.getInt(3);
                String expiry = c.getString(4);

                list.add(
                        new Medicine(
                                id,
                                name,
                                price,
                                stock,
                                expiry == null ? "" : expiry
                        )
                );
            }
        } finally {
            c.close();
        }

        return list;
    }

    // =========================
    // MEDICINES CURSOR
    // MainActivity uses:
    // 1=name
    // 2=price
    // 3=stock
    // 4=expiry
    // =========================
    public Cursor medicines() {

        SQLiteDatabase db = getReadableDatabase();

        return db.rawQuery(
                "SELECT id, name, price, stock, expiry " +
                        "FROM medicines " +
                        "ORDER BY name COLLATE NOCASE ASC",
                null
        );
    }

    // =========================
    // ADD CUSTOMER
    // =========================
    public long addCustomer(String name,
                            String phone,
                            String notes) {

        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("name", name.trim());
        values.put("phone", phone == null ? "" : phone.trim());
        values.put("notes", notes == null ? "" : notes.trim());

        return db.insert("customers", null, values);
    }

    // =========================
    // CUSTOMERS CURSOR
    // MainActivity uses:
    // 1=name
    // 2=phone
    // 3=notes
    // =========================
    public Cursor customers() {

        SQLiteDatabase db = getReadableDatabase();

        return db.rawQuery(
                "SELECT id, name, phone, notes " +
                        "FROM customers " +
                        "ORDER BY name COLLATE NOCASE ASC",
                null
        );
    }

    // =========================
    // SAVE BILL + BILL ITEMS
    // + AUTOMATIC STOCK REDUCTION
    // =========================
    public void addBillWithItems(String customer,
                                 String phone,
                                 double total,
                                 String date,
                                 ArrayList<BillItem> items) {

        if (items == null || items.isEmpty()) {
            throw new IllegalStateException("No medicines in bill");
        }

        SQLiteDatabase db = getWritableDatabase();

        db.beginTransaction();

        try {

            // ---------------------------------
            // 1. CHECK STOCK BEFORE SAVING
            // ---------------------------------
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

                    if (item.qty <= 0) {
                        throw new IllegalStateException(
                                "Invalid quantity for " + item.name
                        );
                    }

                    if (currentStock < item.qty) {
                        throw new IllegalStateException(
                                "Not enough stock for " +
                                        item.name +
                                        ". Available: " +
                                        currentStock
                        );
                    }

                } finally {
                    c.close();
                }
            }

            // ---------------------------------
            // 2. INSERT BILL
            // ---------------------------------
            ContentValues billValues = new ContentValues();

            billValues.put(
                    "customer",
                    customer == null ? "Walk-in Customer" : customer
            );

            billValues.put(
                    "phone",
                    phone == null ? "" : phone
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
                        "Could not create bill"
                );
            }

            // ---------------------------------
            // 3. INSERT BILL ITEMS
            // 4. REDUCE STOCK
            // ---------------------------------
            for (BillItem item : items) {

                ContentValues itemValues = new ContentValues();

                itemValues.put("bill_id", billId);
                itemValues.put("medicine_id", item.medicineId);
                itemValues.put("name", item.name);
                itemValues.put("price", item.price);
                itemValues.put("qty", item.qty);

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
                // Reduce medicine stock.


                // The above statement needs bound values,
                // so use update() below instead.

                // SQLiteDatabase.update with expression
                // is not suitable for arithmetic directly,
                // therefore execute raw SQL safely.
                db.execSQL(
                        "UPDATE medicines " +
                                "SET stock = stock - ? " +
                                "WHERE id = ?",
                        new Object[]{
                                item.qty,
                                item.medicineId
                        }
                );
            }

            // ---------------------------------
            // 5. COMMIT EVERYTHING
            // ---------------------------------
            db.setTransactionSuccessful();

        } finally {
            db.endTransaction();
        }
    }

    // =========================
    // SALES HISTORY
    // MainActivity uses:
    // 1=customer
    // 3=total
    // 4=date
    // =========================
    public Cursor bills() {

        SQLiteDatabase db = getReadableDatabase();

        return db.rawQuery(
                "SELECT id, customer, phone, total, date " +
                        "FROM bills " +
                        "ORDER BY id DESC",
                null
        );
    }

    // =========================
    // OPTIONAL: GET CURRENT STOCK
    // =========================
    public int getStock(long medicineId) {

        SQLiteDatabase db = getReadableDatabase();

        Cursor c = db.rawQuery(
                "SELECT stock FROM medicines WHERE id = ?",
                new String[]{
                        String.valueOf(medicineId)
                }
        );

        try {
            if (c.moveToFirst()) {
                return c.getInt(0);
            }
        } finally {
            c.close();
        }

        return 0;
    }

    // =========================
    // OPTIONAL: DELETE MEDICINE
    // =========================
    public boolean deleteMedicine(long medicineId) {

        SQLiteDatabase db = getWritableDatabase();

        int result = db.delete(
                "medicines",
                "id = ?",
                new String[]{
                        String.valueOf(medicineId)
                }
        );

        return result > 0;
    }

    // =========================
    // CLOSE DATABASE
    // =========================
    @Override
    public synchronized void close() {
        super.close();
    }
}

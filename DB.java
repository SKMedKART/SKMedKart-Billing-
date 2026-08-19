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

    private static final String DATABASE_NAME = "skmedkart.db";
    private static final int DB_VERSION = 3;

    public static class Medicine {
        public long id;
        public String name;
        public double price;
        public int stock;
        public String expiry;

        Medicine(long id, String name, double price, int stock, String expiry) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.stock = stock;
            this.expiry = expiry;
        }
    }

    public static class BillItem {
        public long medicineId;
        public String name;
        public double price;
        public int qty;

        public BillItem(long medicineId, String name, double price, int qty) {
            this.medicineId = medicineId;
            this.name = name;
            this.price = price;
            this.qty = qty;
        }
    }

    public DB(Context context) {
        super(context, DATABASE_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        db.execSQL(
                "CREATE TABLE customers (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name TEXT NOT NULL," +
                        "phone TEXT," +
                        "notes TEXT)"
        );

        db.execSQL(
                "CREATE TABLE medicines (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name TEXT NOT NULL," +
                        "price REAL NOT NULL," +
                        "stock INTEGER NOT NULL," +
                        "expiry TEXT)"
        );

        db.execSQL(
                "CREATE TABLE bills (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "customer TEXT," +
                        "phone TEXT," +
                        "total REAL NOT NULL," +
                        "created TEXT NOT NULL)"
        );

        db.execSQL(
                "CREATE TABLE bill_items (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "bill_id INTEGER NOT NULL," +
                        "medicine_id INTEGER NOT NULL," +
                        "medicine_name TEXT NOT NULL," +
                        "price REAL NOT NULL," +
                        "qty INTEGER NOT NULL," +
                        "amount REAL NOT NULL)"
        );
    }

    @Override
    public void onUpgrade(
            SQLiteDatabase db,
            int oldVersion,
            int newVersion) {

        if (oldVersion < 2) {
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS bill_items (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "bill_id INTEGER NOT NULL," +
                            "medicine_id INTEGER NOT NULL," +
                            "medicine_name TEXT NOT NULL," +
                            "price REAL NOT NULL," +
                            "qty INTEGER NOT NULL," +
                            "amount REAL NOT NULL)"
            );
        }

        if (oldVersion < 3) {
            // Version 3 is used for the corrected stock deduction logic.
            // No table changes are required.
        }
    }

    // ---------------------------------------------------------
    // CUSTOMER
    // ---------------------------------------------------------

    public long addCustomer(
            String name,
            String phone,
            String notes) {

        ContentValues values = new ContentValues();

        values.put("name", name);
        values.put("phone", phone);
        values.put("notes", notes);

        return getWritableDatabase().insert(
                "customers",
                null,
                values
        );
    }

    public Cursor customers() {

        return getReadableDatabase().rawQuery(
                "SELECT id,name,phone,notes " +
                        "FROM customers " +
                        "ORDER BY id DESC",
                null
        );
    }

    // ---------------------------------------------------------
    // MEDICINE
    // ---------------------------------------------------------

    public long addMedicine(
            String name,
            double price,
            int stock,
            String expiry) {

        ContentValues values = new ContentValues();

        values.put("name", name);
        values.put("price", price);
        values.put("stock", stock);
        values.put("expiry", expiry);

        return getWritableDatabase().insert(
                "medicines",
                null,
                values
        );
    }

    public ArrayList<Medicine> medicineList() {

        ArrayList<Medicine> list = new ArrayList<>();

        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id,name,price,stock,expiry " +
                        "FROM medicines " +
                        "ORDER BY name COLLATE NOCASE",
                null
        );

        try {
            while (cursor.moveToNext()) {

                list.add(
                        new Medicine(
                                cursor.getLong(0),
                                cursor.getString(1),
                                cursor.getDouble(2),
                                cursor.getInt(3),
                                cursor.getString(4)
                        )
                );
            }
        } finally {
            cursor.close();
        }

        return list;
    }

    public Cursor medicines() {

        return getReadableDatabase().rawQuery(
                "SELECT id,name,price,stock,expiry " +
                        "FROM medicines " +
                        "ORDER BY id DESC",
                null
        );
    }

    // ---------------------------------------------------------
    // BILL + STOCK DEDUCTION
    // ---------------------------------------------------------

    public long addBillWithItems(
            String customer,
            String phone,
            double total,
            String created,
            ArrayList<BillItem> items) {

        if (items == null || items.isEmpty()) {
            throw new IllegalStateException(
                    "Bill has no items"
            );
        }

        SQLiteDatabase db = getWritableDatabase();

        db.beginTransaction();

        try {

            /*
             * First calculate total quantity required
             * for each medicine.
             *
             * This prevents problems if the same medicine
             * is added more than once to the cart.
             */
            Map<Long, Integer> required =
                    new HashMap<>();

            Map<Long, BillItem> medicineInfo =
                    new HashMap<>();

            for (BillItem item : items) {

                if (item.medicineId <= 0) {
                    throw new IllegalStateException(
                            "Invalid medicine"
                    );
                }

                if (item.qty <= 0) {
                    throw new IllegalStateException(
                            "Invalid quantity for "
                                    + item.name
                    );
                }

                Integer oldQty =
                        required.get(item.medicineId);

                if (oldQty == null) {
                    oldQty = 0;
                }

                required.put(
                        item.medicineId,
                        oldQty + item.qty
                );

                medicineInfo.put(
                        item.medicineId,
                        item
                );
            }

            /*
             * CHECK STOCK BEFORE CREATING BILL
             */
            for (Map.Entry<Long, Integer> entry
                    : required.entrySet()) {

                long medicineId = entry.getKey();
                int requiredQty = entry.getValue();

                Cursor cursor = db.rawQuery(
                        "SELECT name,stock " +
                                "FROM medicines " +
                                "WHERE id=?",
                        new String[]{
                                String.valueOf(medicineId)
                        }
                );

                try {

                    if (!cursor.moveToFirst()) {

                        throw new IllegalStateException(
                                "Medicine not found"
                        );
                    }

                    String medicineName =
                            cursor.getString(0);

                    int currentStock =
                            cursor.getInt(1);

                    if (requiredQty > currentStock) {

                        throw new IllegalStateException(
                                "Insufficient stock: "
                                        + medicineName
                                        + " (available "
                                        + currentStock
                                        + ")"
                        );
                    }

                } finally {
                    cursor.close();
                }
            }

            /*
             * CREATE BILL
             */
            ContentValues billValues =
                    new ContentValues();

            billValues.put(
                    "customer",
                    customer
            );

            billValues.put(
                    "phone",
                    phone
            );

            billValues.put(
                    "total",
                    total
            );

            billValues.put(
                    "created",
                    created
            );

            long billId =
                    db.insertOrThrow(
                            "bills",
                            null,
                            billValues
                    );

            /*
             * SAVE BILL ITEMS
             *
             * Then deduct stock.
             */
            for (BillItem item : items) {

                double amount =
                        item.price * item.qty;

                ContentValues line =
                        new ContentValues();

                line.put(
                        "bill_id",
                        billId
                );

                line.put(
                        "medicine_id",
                        item.medicineId
                );

                line.put(
                        "medicine_name",
                        item.name
                );

                line.put(
                        "price",
                        item.price
                );

                line.put(
                        "qty",
                        item.qty
                );

                line.put(
                        "amount",
                        amount
                );

                db.insertOrThrow(
                        "bill_items",
                        null,
                        line
                );
            }

            /*
             * IMPORTANT:
             * DEDUCT STOCK HERE.
             *
             * Example:
             * Stock = 1500
             * Sold  = 1
             * New stock = 1499
             */
            for (Map.Entry<Long, Integer> entry
                    : required.entrySet()) {

                long medicineId =
                        entry.getKey();

                int soldQty =
                        entry.getValue();

                int affectedRows =
                        db.compileStatement(
                                "UPDATE medicines " +
                                        "SET stock = stock - ? " +
                                        "WHERE id = ? " +
                                        "AND stock >= ?"
                        ).executeUpdateDelete();

                /*
                 * The above statement is prepared with
                 * placeholders, so use direct SQL below
                 * for the actual values.
                 */
                String sql =
                        "UPDATE medicines " +
                                "SET stock = stock - "
                                + soldQty +
                                " WHERE id = "
                                + medicineId +
                                " AND stock >= "
                                + soldQty;

                affectedRows =
                        db.compileStatement(sql)
                                .executeUpdateDelete();

                if (affectedRows != 1) {

                    BillItem info =
                            medicineInfo.get(
                                    medicineId
                            );

                    String name =
                            info != null
                                    ? info.name
                                    : "Medicine";

                    throw new IllegalStateException(
                            "Stock update failed for "
                                    + name
                    );
                }
            }

            /*
             * EVERYTHING SUCCESSFUL
             */
            db.setTransactionSuccessful();

            return billId;

        } finally {

            db.endTransaction();
        }
    }

    // ---------------------------------------------------------
    // SALES HISTORY
    // ---------------------------------------------------------

    public Cursor bills() {

        return getReadableDatabase().rawQuery(
                "SELECT id,customer,phone,total,created " +
                        "FROM bills " +
                        "ORDER BY id DESC",
                null
        );
    }
}
